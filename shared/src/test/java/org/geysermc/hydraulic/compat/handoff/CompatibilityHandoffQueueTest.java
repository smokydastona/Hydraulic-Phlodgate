package org.geysermc.hydraulic.compat.handoff;

import org.geysermc.hydraulic.cache.ArtifactCache;
import org.geysermc.hydraulic.compat.CompatibilityReport;
import org.geysermc.hydraulic.compat.ContentInventory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompatibilityHandoffQueueTest {
    @TempDir
    Path tempDir;

    @Test
    void deduplicatesPendingEntriesByFingerprint() {
        CompatibilityHandoffQueue queue = new CompatibilityHandoffQueue(LoggerFactory.getLogger("HandoffQueueTest"), this.tempDir);
        queue.ensureLayout();

        HandoffEnvelope envelope = envelope("fingerprint-1");
        CompatibilityReport report = CompatibilityReport.empty();

        assertTrue(queue.enqueue(report, envelope));
        assertFalse(queue.enqueue(report, envelope));
        assertEquals(1, queue.pendingEntries().size());
    }

    @Test
    void persistsAndReloadsQueueState() {
        CompatibilityHandoffQueue queue = new CompatibilityHandoffQueue(LoggerFactory.getLogger("HandoffQueueTest"), this.tempDir);
        queue.ensureLayout();
        queue.enqueue(CompatibilityReport.empty(), envelope("fingerprint-2"));
        queue.markCompleted("fingerprint-2", "exported");

        CompatibilityHandoffQueue reloaded = new CompatibilityHandoffQueue(LoggerFactory.getLogger("HandoffQueueTest"), this.tempDir);
        reloaded.loadQueueState();

        assertEquals(1, reloaded.completedEntries().size());
        assertEquals(0, reloaded.pendingEntries().size());
    }

    @Test
    void skipsMalformedPersistedEntriesWithoutAbortingStartup() throws Exception {
        CompatibilityHandoffQueue queue = new CompatibilityHandoffQueue(LoggerFactory.getLogger("HandoffQueueTest"), this.tempDir);
        queue.ensureLayout();
        Files.writeString(
            this.tempDir.resolve("handoff-queue/completed/malformed.json"),
            "{not-json"
        );

        queue.loadQueueState();

        assertEquals(0, queue.completedEntries().size());
    }

    private static HandoffEnvelope envelope(String fingerprint) {
        ArtifactCache.StartupCompatibilityKey key = new ArtifactCache.StartupCompatibilityKey(fingerprint);
        ArtifactCache.CompatibilityManifest manifest = new ArtifactCache.CompatibilityManifest(
            key,
            "metadata",
            "engine",
            "adapter-catalog",
            0,
            Map.of()
        );
        return HandoffEnvelope.create(
            manifest,
            ContentInventory.empty(),
            CompatibilityReport.empty(),
            "test",
            "26.2",
            "geyser-test",
            "geyser-test"
        );
    }
}
