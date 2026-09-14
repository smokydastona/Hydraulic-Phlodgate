package org.geysermc.hydraulic.compat.corpus;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Automated Corpus Diff Re-indexer.
 * Detects structural and semantic modifications in curated Bedrock addon schemas and re-evaluates
 * capability rankings and admissibility without requiring a full manual cache wipe.
 */
public final class CorpusDiffReindexer {

    public enum DiffType {
        ADDED,
        MODIFIED,
        DELETED,
        UNCHANGED
    }

    public record EntryDiff(
        @NotNull String corpusId,
        @NotNull DiffType type,
        @Nullable String previousSha256,
        @Nullable String currentSha256,
        double previousScore,
        double updatedScore,
        boolean rankingChanged
    ) {
        public EntryDiff {
            Objects.requireNonNull(corpusId, "corpusId");
            Objects.requireNonNull(type, "type");
        }
    }

    public record ReindexReport(
        int totalScanned,
        int addedCount,
        int modifiedCount,
        int deletedCount,
        int unchangedCount,
        @NotNull List<EntryDiff> diffs,
        @NotNull AddonCorpusIndex updatedIndex
    ) {
        public ReindexReport {
            Objects.requireNonNull(diffs, "diffs");
            Objects.requireNonNull(updatedIndex, "updatedIndex");
        }
    }

    /**
     * Scans a target directory against the baseline index and computes a diff-driven updated AddonCorpusIndex.
     */
    @NotNull
    public static ReindexReport reindex(@NotNull Path corpusDirectory, @NotNull AddonCorpusIndex currentIndex) {
        Objects.requireNonNull(corpusDirectory, "corpusDirectory");
        Objects.requireNonNull(currentIndex, "currentIndex");

        Map<String, Path> onDiskFiles = new LinkedHashMap<>();
        if (Files.isDirectory(corpusDirectory)) {
            try (Stream<Path> stream = Files.walk(corpusDirectory)) {
                stream.filter(Files::isRegularFile)
                      .filter(p -> p.getFileName().toString().endsWith(".json"))
                      .sorted()
                      .forEach(p -> {
                          String filename = p.getFileName().toString();
                          String id = filename.substring(0, filename.length() - 5);
                          onDiskFiles.put(id, p);
                      });
            } catch (IOException ignored) {
            }
        }

        List<EntryDiff> diffs = new ArrayList<>();
        Map<String, AddonCorpusIndex.IndexedEntry> updatedEntries = new LinkedHashMap<>();

        int added = 0;
        int modified = 0;
        int deleted = 0;
        int unchanged = 0;

        // 1. Process files present on disk
        for (Map.Entry<String, Path> entry : onDiskFiles.entrySet()) {
            String corpusId = entry.getKey();
            Path path = entry.getValue();
            String sha256 = calculateSha256(path);
            long lastModified = 0;
            try {
                lastModified = Files.getLastModifiedTime(path).toMillis();
            } catch (IOException ignored) {
            }

            AddonCorpusIndex.IndexedEntry oldEntry = currentIndex.entries().get(corpusId);

            if (oldEntry == null) {
                // ADDED
                added++;
                double initialScore = evaluateScoreFromDisk(path);
                AddonCorpusIndex.IndexedEntry newIndexed = new AddonCorpusIndex.IndexedEntry(
                    corpusId,
                    "bedrock:" + corpusId,
                    corpusDirectory.getFileName().toString(),
                    true,
                    "Added in re-index",
                    initialScore,
                    lastModified
                );
                updatedEntries.put(corpusId, newIndexed);
                diffs.add(new EntryDiff(corpusId, DiffType.ADDED, null, sha256, 0.0, initialScore, true));
            } else {
                // Check if modified
                boolean changed = oldEntry.lastModifiedEpochMillis() != lastModified;

                if (changed) {
                    modified++;
                    double newScore = evaluateScoreFromDisk(path);
                    boolean scoreDiff = Math.abs(oldEntry.capabilityScore() - newScore) > 0.001;
                    AddonCorpusIndex.IndexedEntry updatedIndexed = new AddonCorpusIndex.IndexedEntry(
                        corpusId,
                        oldEntry.bedrockIdentifier(),
                        oldEntry.storageLocation(),
                        oldEntry.isAdmissible(),
                        oldEntry.admissibilityReason(),
                        newScore,
                        lastModified
                    );
                    updatedEntries.put(corpusId, updatedIndexed);
                    diffs.add(new EntryDiff(corpusId, DiffType.MODIFIED, null, sha256, oldEntry.capabilityScore(), newScore, scoreDiff));
                } else {
                    unchanged++;
                    updatedEntries.put(corpusId, oldEntry);
                    diffs.add(new EntryDiff(corpusId, DiffType.UNCHANGED, null, sha256, oldEntry.capabilityScore(), oldEntry.capabilityScore(), false));
                }
            }
        }

        // 2. Detect deleted entries
        for (Map.Entry<String, AddonCorpusIndex.IndexedEntry> currentEntry : currentIndex.entries().entrySet()) {
            String id = currentEntry.getKey();
            if (!onDiskFiles.containsKey(id)) {
                deleted++;
                diffs.add(new EntryDiff(id, DiffType.DELETED, null, null, currentEntry.getValue().capabilityScore(), 0.0, true));
            }
        }

        int admissible = (int) updatedEntries.values().stream().filter(AddonCorpusIndex.IndexedEntry::isAdmissible).count();
        int inadmissible = updatedEntries.size() - admissible;

        AddonCorpusIndex newIndex = new AddonCorpusIndex(
            currentIndex.corpusVersion(),
            currentIndex.algorithm(),
            updatedEntries,
            new AddonCorpusIndex.CorpusMetadata(
                updatedEntries.size(),
                admissible,
                inadmissible,
                System.currentTimeMillis()
            )
        );

        return new ReindexReport(
            onDiskFiles.size(),
            added,
            modified,
            deleted,
            unchanged,
            Collections.unmodifiableList(diffs),
            newIndex
        );
    }

    private static String calculateSha256(@NotNull Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception e) {
            return "";
        }
    }

    private static double evaluateScoreFromDisk(@NotNull Path path) {
        try {
            long size = Files.size(path);
            // Heuristic scoring based on schema density
            return Math.min(1.0, 0.5 + (size / 10000.0));
        } catch (Exception e) {
            return 0.5;
        }
    }
}
