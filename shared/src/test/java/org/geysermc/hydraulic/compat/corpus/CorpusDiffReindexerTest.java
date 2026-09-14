package org.geysermc.hydraulic.compat.corpus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class CorpusDiffReindexerTest {

    @Test
    public void testDiffReindexing(@TempDir Path tempDir) throws IOException {
        Path curatedDir = tempDir.resolve("curated");
        Files.createDirectories(curatedDir);

        Path entryA = curatedDir.resolve("create_addon.json");
        Files.writeString(entryA, "{\"schema_version\": 1, \"capabilities\": [\"item_transfer\", \"kinetic\"]}");

        Path entryB = curatedDir.resolve("mekanism_addon.json");
        Files.writeString(entryB, "{\"schema_version\": 1, \"capabilities\": [\"energy\", \"gas\"]}");

        // Initial baseline with entryA and an old deleted entryC
        AddonCorpusIndex baseline = new AddonCorpusIndex(
            "v2.0.0",
            "HYDRAULIC_CORPUS_INDEX_V2",
            Map.of(
                "create_addon", new AddonCorpusIndex.IndexedEntry(
                    "create_addon",
                    "bedrock:create_addon",
                    "curated",
                    true,
                    "Baseline entry",
                    0.75,
                    Files.getLastModifiedTime(entryA).toMillis()
                ),
                "deleted_addon", new AddonCorpusIndex.IndexedEntry(
                    "deleted_addon",
                    "bedrock:deleted_addon",
                    "curated",
                    true,
                    "To be deleted",
                    0.6,
                    1000L
                )
            ),
            new AddonCorpusIndex.CorpusMetadata(2, 2, 0, 1000L)
        );

        CorpusDiffReindexer.ReindexReport report = CorpusDiffReindexer.reindex(curatedDir, baseline);

        assertEquals(2, report.totalScanned());
        assertEquals(1, report.addedCount()); // mekanism_addon
        assertEquals(0, report.modifiedCount());
        assertEquals(1, report.deletedCount()); // deleted_addon
        assertEquals(1, report.unchangedCount()); // create_addon

        AddonCorpusIndex updated = report.updatedIndex();
        assertEquals(2, updated.entries().size());
        assertTrue(updated.entries().containsKey("create_addon"));
        assertTrue(updated.entries().containsKey("mekanism_addon"));
        assertFalse(updated.entries().containsKey("deleted_addon"));
    }
}
