package org.geysermc.hydraulic.compat;

import org.geysermc.hydraulic.compat.model.ModFingerprint;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ContentInventoryTest {
    @Test
    void normalizesLegacyNullCollectionsFromPersistedRecords() {
        ContentInventory.ModContentInventory inventory = new ContentInventory.ModContentInventory(
            "examplemod",
            "examplemod",
            "Example Mod",
            "1.0.0",
            null,
            new ModFingerprint("examplemod", "examplemod", "1.0.0", "unknown", "test", 0, 0, 0, 0, 0, 0, 0, false, false, false, false, false, false, false, false),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        assertEquals(List.of(), inventory.roots());
        assertEquals(Map.of(), inventory.registryCounts());
        assertEquals(Map.of(), inventory.registryEntries());
        assertEquals(Map.of(), inventory.assetCounts());
        assertEquals(Map.of(), inventory.assetEntries());
        assertEquals(Map.of(), inventory.metadataCounts());
        assertEquals(Map.of(), inventory.metadataEntries());
        assertEquals(Map.of(), inventory.patchCounts());
        assertEquals(Map.of(), inventory.patchEntries());
        assertEquals(Map.of(), inventory.recipePaths());
        assertNull(inventory.recipePath("examplemod:missing"));
    }
}
