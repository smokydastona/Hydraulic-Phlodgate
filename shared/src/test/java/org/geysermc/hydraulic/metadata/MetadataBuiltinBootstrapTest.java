package org.geysermc.hydraulic.metadata;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetadataBuiltinBootstrapTest {
    @TempDir
    Path tempDir;

    @Test
    void installsCuratedBuiltinMetadataFiles() {
        MetadataBuiltinBootstrap.installBuiltinMetadata(LoggerFactory.getLogger("MetadataBuiltinBootstrapTest"), this.tempDir);

        Path builtinDir = this.tempDir.resolve("builtin");
        assertTrue(Files.isDirectory(builtinDir));
        for (String fileName : List.of(
            "immersiveengineering.crusher.json",
            "immersiveengineering.diesel_generator.json",
            "immersiveengineering.arc_furnace.json",
            "create.mechanical_mixer.json",
            "mekanism.digital_miner.json",
            "mekanism.thermoelectric_generator.json",
            "mekanism.chemical_crystallizer.json",
            "botania.mana_pool.json",
            "ae2.inscriber.json",
            "thermal.machine_pulverizer.json"
        )) {
            assertTrue(Files.isRegularFile(builtinDir.resolve(fileName)), "missing " + fileName);
        }
    }

    @Test
    void loadsInstalledBuiltinMetadataIntoIndex() {
        MetadataBuiltinBootstrap.installBuiltinMetadata(LoggerFactory.getLogger("MetadataBuiltinBootstrapTest"), this.tempDir);
        MetadataIndex index = new MetadataLoader(LoggerFactory.getLogger("MetadataBuiltinBootstrapTest")).load(this.tempDir);

        assertNotNull(index.blockMapping(Identifier.fromNamespaceAndPath("immersiveengineering", "crusher")));
        assertNotNull(index.blockMapping(Identifier.fromNamespaceAndPath("immersiveengineering", "diesel_generator")));
        assertNotNull(index.blockMapping(Identifier.fromNamespaceAndPath("immersiveengineering", "arc_furnace")));
        assertNotNull(index.blockMapping(Identifier.fromNamespaceAndPath("create", "mechanical_mixer")));
        assertNotNull(index.blockMapping(Identifier.fromNamespaceAndPath("mekanism", "digital_miner")));
        assertNotNull(index.blockMapping(Identifier.fromNamespaceAndPath("mekanismgenerators", "thermoelectric_generator")));
        assertNotNull(index.blockMapping(Identifier.fromNamespaceAndPath("mekanism", "chemical_crystallizer")));
        assertNotNull(index.blockMapping(Identifier.fromNamespaceAndPath("botania", "mana_pool")));
        assertNotNull(index.blockMapping(Identifier.fromNamespaceAndPath("ae2", "inscriber")));
        assertNotNull(index.blockMapping(Identifier.fromNamespaceAndPath("thermal", "machine_pulverizer")));

        assertTrue(index.summary().patchCount() >= 10);
        assertTrue(index.summary().blockMappingCount() >= 10);
    }
}
