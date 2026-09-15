package org.geysermc.hydraulic.entity;

import org.geysermc.hydraulic.pack.ModResourceIndex;
import org.geysermc.hydraulic.platform.mod.ModInfo;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityPresentationProfileScannerTest {
    @Test
    void validatesIndexedEntityRenderProfileAndReferencedAssets() throws Exception {
        Path root = Files.createTempDirectory("hydraulic-eme");
        Files.createDirectories(root.resolve("data/example/easy_model_entities/profiles/entity"));
        Files.createDirectories(root.resolve("assets/example/easy_model_entities/render_profiles/entity"));
        Files.createDirectories(root.resolve("assets/example/easy_model_entities/models"));
        Files.createDirectories(root.resolve("assets/example/textures/entity"));
        Files.writeString(root.resolve("data/example/easy_model_entities/profiles/entity/test.json"), "{\"schema_version\":\"0.2.0\",\"model_type\":\"entity\",\"version\":\"v1\"}");
        Files.writeString(root.resolve("assets/example/easy_model_entities/render_profiles/entity/test.json"), "{\"schema_version\":\"0.2.0\",\"version\":\"v1\",\"model\":\"example:easy_model_entities/models/test\",\"texture\":\"example:textures/entity/test\",\"animation\":{\"walk_speed_multiplier\":0.85}}");
        Files.writeString(root.resolve("assets/example/easy_model_entities/models/test.bbmodel"), "{}");
        Files.writeString(root.resolve("assets/example/textures/entity/test.png"), "texture");

        ModInfo mod = new ModInfo("example", "example", "Example", "1.0", null, List.of(root));
        EntityPresentationProfileScanner.Report report = EntityPresentationProfileScanner.scan(mod, ModResourceIndex.create(mod, LoggerFactory.getLogger("test")), LoggerFactory.getLogger("test"));

        assertEquals(2, report.profiles().size());
        assertEquals(2, report.validCount());
        assertTrue(report.profiles().stream().allMatch(EntityPresentationProfileScanner.Profile::valid));
    }

    @Test
    void rejectsMissingRenderAssetsWithoutAbortingScan() throws Exception {
        Path root = Files.createTempDirectory("hydraulic-eme-invalid");
        Path profiles = root.resolve("assets/example/easy_model_entities/render_profiles/entity");
        Files.createDirectories(profiles);
        Files.writeString(profiles.resolve("broken.json"), "{\"schema_version\":\"0.2.0\",\"version\":\"v1\",\"model\":\"example:missing\",\"texture\":\"example:missing\"}");

        ModInfo mod = new ModInfo("example", "example", "Example", "1.0", null, List.of(root));
        EntityPresentationProfileScanner.Report report = EntityPresentationProfileScanner.scan(mod, ModResourceIndex.create(mod, LoggerFactory.getLogger("test")), LoggerFactory.getLogger("test"));

        assertEquals(1, report.profiles().size());
        assertFalse(report.profiles().get(0).valid());
        assertEquals(2, report.profiles().get(0).issues().size());
    }
}