package org.geysermc.hydraulic.fabric.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HydraulicTestMetadataBootstrapTest {
    @Test
    void installsBundledMetadataIntoHydraulicConfigDirectory(@TempDir Path tempDir) throws IOException {
        HydraulicTestMetadataBootstrap.installBundledMetadata(tempDir);

        Path installed = tempDir.resolve("hydraulic").resolve("metadata").resolve("hydraulic_test_mod.golden_barrel.json");
        assertTrue(Files.exists(installed));
        String content = Files.readString(installed);
        assertTrue(content.contains("\"hydraulic_test_mod:barrel_cube\""));
        assertTrue(content.contains("\"visual_only_runtime\""));
        String resourceContent = Files.readString(Path.of("src/main/resources/hydraulic/metadata/hydraulic_test_mod.golden_barrel.json"));
        assertEquals(resourceContent, content);
    }

    @Test
    void installsItemTransferMachineMetadataIntoHydraulicConfigDirectory(@TempDir Path tempDir) throws IOException {
        HydraulicTestMetadataBootstrap.installBundledMetadata(tempDir);

        Path installed = tempDir.resolve("hydraulic").resolve("metadata").resolve("hydraulic_test_mod.item_transfer_machine.json");
        assertTrue(Files.exists(installed));
        String content = Files.readString(installed);
        assertTrue(content.contains("\"hydraulic_test_mod:item_transfer_machine\""));
        assertTrue(content.contains("\"can_insert\""));
        String resourceContent = Files.readString(Path.of("src/main/resources/hydraulic/metadata/hydraulic_test_mod.item_transfer_machine.json"));
        assertEquals(resourceContent, content);
    }

    @Test
    void installsProcessingMachineMetadataIntoHydraulicConfigDirectory(@TempDir Path tempDir) throws IOException {
        HydraulicTestMetadataBootstrap.installBundledMetadata(tempDir);

        Path installed = tempDir.resolve("hydraulic").resolve("metadata").resolve("hydraulic_test_mod.processing_machine.json");
        assertTrue(Files.exists(installed));
        String content = Files.readString(installed);
        assertTrue(content.contains("\"hydraulic_test_mod:processing_machine\""));
        assertTrue(content.contains("\"generic_smelting\""));
        String resourceContent = Files.readString(Path.of("src/main/resources/hydraulic/metadata/hydraulic_test_mod.processing_machine.json"));
        assertEquals(resourceContent, content);
    }

    @Test
    void installsResourceMachineMetadataIntoHydraulicConfigDirectory(@TempDir Path tempDir) throws IOException {
        HydraulicTestMetadataBootstrap.installBundledMetadata(tempDir);

        for (String fixture : new String[] {"fluid_machine", "energy_machine", "mixed_resource_machine", "menu_machine"}) {
            String fileName = "hydraulic_test_mod." + fixture + ".json";
            Path installed = tempDir.resolve("hydraulic").resolve("metadata").resolve(fileName);
            assertTrue(Files.exists(installed));
            assertEquals(
                Files.readString(Path.of("src/main/resources/hydraulic/metadata").resolve(fileName)),
                Files.readString(installed)
            );
            if ("menu_machine".equals(fixture)) {
                assertTrue(Files.readString(installed).contains("\"toggle\""));
            }
        }
    }
}