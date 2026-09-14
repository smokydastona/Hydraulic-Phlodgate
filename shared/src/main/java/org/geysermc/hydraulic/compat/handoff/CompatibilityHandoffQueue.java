package org.geysermc.hydraulic.compat.handoff;

import org.geysermc.hydraulic.compat.CompatibilityReport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Durable, non-blocking queue for compatibility report handoff operations.
 *
 * This system ensures that:
 * - Startup never blocks on handoff success
 * - Failed exports persist as retryable local queue entries
 * - Queue items deduplicate by compatibility fingerprint
 * - The handoff contract remains provider-neutral
 * - Operator-visible artifacts show handoff status and retry information
 */
public final class CompatibilityHandoffQueue {
    private static final String QUEUE_MANIFEST = "handoff-queue-manifest.json";
    private static final String PENDING_DIR = "pending";
    private static final String COMPLETED_DIR = "completed";
    private static final String FAILED_DIR = "failed";

    private final Logger logger;
    private final Path queueRoot;
    private final Map<String, QueueEntry> pendingEntries;
    private final Map<String, QueueEntry> completedEntries;
    private final Map<String, QueueEntry> failedEntries;

    public CompatibilityHandoffQueue(@NotNull Logger logger, @NotNull Path cacheRoot) {
        this.logger = logger;
        this.queueRoot = cacheRoot.resolve("handoff-queue");
        this.pendingEntries = new ConcurrentHashMap<>();
        this.completedEntries = new ConcurrentHashMap<>();
        this.failedEntries = new ConcurrentHashMap<>();
    }

    public void ensureLayout() {
        try {
            Files.createDirectories(this.queueRoot);
            Files.createDirectories(this.queueRoot.resolve(PENDING_DIR));
            Files.createDirectories(this.queueRoot.resolve(COMPLETED_DIR));
            Files.createDirectories(this.queueRoot.resolve(FAILED_DIR));
        } catch (Exception e) {
            this.logger.error("Failed to initialize handoff queue layout at {}", this.queueRoot, e);
        }
    }

    /**
     * Enqueues a compatibility report for handoff.
     * Returns true if the report was enqueued (or already pending), false if deduplication prevented enqueue.
     */
    public boolean enqueue(@NotNull CompatibilityReport report, @NotNull HandoffEnvelope envelope) {
        String fingerprint = envelope.compatibilityFingerprint();

        // Check for existing pending entry with same fingerprint
        if (this.pendingEntries.containsKey(fingerprint)) {
            this.logger.debug("Handoff entry already pending for fingerprint {}, skipping duplicate", fingerprint);
            return false;
        }

        // Check for recently completed entry with same fingerprint
        QueueEntry existingCompleted = this.completedEntries.get(fingerprint);
        if (existingCompleted != null && existingCompleted.completedAt() != null) {
            // Skip if completed within the last hour to avoid duplicate handoffs
            if (Instant.now().toEpochMilli() - existingCompleted.completedAt() < 3600000) {
                this.logger.debug("Handoff entry recently completed for fingerprint {}, skipping duplicate", fingerprint);
                return false;
            }
        }

        QueueEntry entry = new QueueEntry(
            UUID.randomUUID().toString(),
            fingerprint,
            envelope,
            Instant.now().toEpochMilli(),
            null,
            null,
            0,
            "pending"
        );

        this.pendingEntries.put(fingerprint, entry);
        this.persistQueueEntry(entry, PENDING_DIR);
        this.logger.info("Enqueued compatibility report for handoff (fingerprint={}, entryId={})", fingerprint, entry.entryId());
        return true;
    }

    /**
     * Marks a queue entry as completed.
     */
    public void markCompleted(@NotNull String fingerprint, @NotNull String transportResult) {
        QueueEntry entry = this.pendingEntries.remove(fingerprint);
        if (entry == null) {
            this.logger.warn("No pending entry found for fingerprint {}, cannot mark completed", fingerprint);
            return;
        }

        QueueEntry completedEntry = new QueueEntry(
            entry.entryId(),
            entry.fingerprint(),
            entry.envelope(),
            entry.enqueuedAt(),
            Instant.now().toEpochMilli(),
            transportResult,
            entry.retryCount(),
            "completed"
        );

        this.completedEntries.put(fingerprint, completedEntry);
        this.persistQueueEntry(completedEntry, COMPLETED_DIR);
        this.deleteQueueEntry(entry.entryId(), PENDING_DIR);
        this.logger.info("Marked handoff entry as completed (fingerprint={}, entryId={}, result={})", fingerprint, entry.entryId(), transportResult);
    }

    /**
     * Marks a queue entry as failed and increments retry count.
     */
    public void markFailed(@NotNull String fingerprint, @NotNull String failureReason) {
        QueueEntry entry = this.pendingEntries.remove(fingerprint);
        if (entry == null) {
            this.logger.warn("No pending entry found for fingerprint {}, cannot mark failed", fingerprint);
            return;
        }

        QueueEntry failedEntry = new QueueEntry(
            entry.entryId(),
            entry.fingerprint(),
            entry.envelope(),
            entry.enqueuedAt(),
            null,
            failureReason,
            entry.retryCount() + 1,
            "failed"
        );

        this.failedEntries.put(fingerprint, failedEntry);
        this.persistQueueEntry(failedEntry, FAILED_DIR);
        this.deleteQueueEntry(entry.entryId(), PENDING_DIR);
        this.logger.warn("Marked handoff entry as failed (fingerprint={}, entryId={}, reason={}, retryCount={})",
            fingerprint, entry.entryId(), failureReason, failedEntry.retryCount());
    }

    /**
     * Returns all pending entries for retry processing.
     */
    @NotNull
    public List<QueueEntry> pendingEntries() {
        return new ArrayList<>(this.pendingEntries.values());
    }

    /**
     * Returns completed entries for status reporting.
     */
    @NotNull
    public List<QueueEntry> completedEntries() {
        return new ArrayList<>(this.completedEntries.values());
    }

    /**
     * Returns failed entries for status reporting.
     */
    @NotNull
    public List<QueueEntry> failedEntries() {
        return new ArrayList<>(this.failedEntries.values());
    }

    /**
     * Loads the queue state from disk on startup.
     */
    public void loadQueueState() {
        this.ensureLayout();

        // Load pending entries
        this.loadEntriesFromDir(PENDING_DIR, this.pendingEntries);
        // Load completed entries
        this.loadEntriesFromDir(COMPLETED_DIR, this.completedEntries);
        // Load failed entries
        this.loadEntriesFromDir(FAILED_DIR, this.failedEntries);

        this.logger.info("Loaded handoff queue state (pending={}, completed={}, failed={})",
            this.pendingEntries.size(), this.completedEntries.size(), this.failedEntries.size());
    }

    /**
     * Persists the queue manifest.
     */
    public void persistManifest() {
        QueueManifest manifest = new QueueManifest(
            this.pendingEntries.size(),
            this.completedEntries.size(),
            this.failedEntries.size(),
            Instant.now().toEpochMilli()
        );
        this.writeJson(this.queueRoot.resolve(QUEUE_MANIFEST), manifest);
    }

    private void loadEntriesFromDir(@NotNull String dirName, @NotNull Map<String, QueueEntry> targetMap) {
        Path dir = this.queueRoot.resolve(dirName);
        if (!Files.isDirectory(dir)) {
            return;
        }

        try (var stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".json"))
                .forEach(path -> {
                    QueueEntry entry = this.readJson(path, QueueEntry.class);
                    if (entry != null) {
                        targetMap.put(entry.fingerprint(), entry);
                    }
                });
        } catch (Exception e) {
            this.logger.error("Failed to load queue entries from directory {}", dirName, e);
        }
    }

    private void persistQueueEntry(@NotNull QueueEntry entry, @NotNull String dirName) {
        Path entryPath = this.queueRoot.resolve(dirName).resolve(entry.entryId() + ".json");
        this.writeJson(entryPath, entry);
        this.persistManifest();
    }

    private void deleteQueueEntry(@NotNull String entryId, @NotNull String dirName) {
        try {
            Files.deleteIfExists(this.queueRoot.resolve(dirName).resolve(entryId + ".json"));
        } catch (Exception e) {
            this.logger.error("Failed to delete queue entry {} from directory {}", entryId, dirName, e);
        }
    }

    private void writeJson(@NotNull Path path, @NotNull Object value) {
        try {
            Files.createDirectories(path.getParent());
            try (var writer = Files.newBufferedWriter(path)) {
                org.geysermc.hydraulic.Constants.GSON.toJson(value, writer);
            }
        } catch (Exception e) {
            this.logger.error("Failed to write handoff queue entry {}", path, e);
        }
    }

    @Nullable
    private <T> T readJson(@NotNull Path path, @NotNull Class<T> type) {
        if (!Files.isRegularFile(path)) {
            return null;
        }

        try (var reader = Files.newBufferedReader(path)) {
            return org.geysermc.hydraulic.Constants.GSON.fromJson(reader, type);
        } catch (Exception e) {
            this.logger.warn("Skipping malformed handoff queue entry {}", path, e);
            return null;
        }
    }

    /**
     * Represents a single entry in the handoff queue.
     */
    public record QueueEntry(
        @NotNull String entryId,
        @NotNull String fingerprint,
        @NotNull HandoffEnvelope envelope,
        long enqueuedAt,
        @Nullable Long completedAt,
        @Nullable String failureReason,
        int retryCount,
        @NotNull String status
    ) {
    }

    /**
     * Represents the manifest of the queue state.
     */
    private record QueueManifest(
        int pendingCount,
        int completedCount,
        int failedCount,
        long lastUpdated
    ) {
    }
}