package org.geysermc.hydraulic.cache;

import org.geysermc.hydraulic.Constants;
import org.geysermc.hydraulic.compat.CompatibilityProfile;
import org.geysermc.hydraulic.compat.CompatibilityReport;
import org.geysermc.hydraulic.compat.ContentInventory;
import org.geysermc.hydraulic.compat.CompatibilityStatus;
import org.geysermc.hydraulic.compat.model.ModFingerprint;
import org.geysermc.hydraulic.compat.model.SupportResult;
import org.geysermc.hydraulic.compat.model.SupportLevel;
import org.geysermc.hydraulic.metadata.MetadataIndex;
import org.geysermc.hydraulic.pack.ModResourceIndex;
import org.geysermc.hydraulic.pack.PackValidationReport;
import org.geysermc.hydraulic.platform.mod.ModInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ArtifactCacheTest {
    @TempDir
    Path tempDir;

    @Test
    void storesAndLoadsCompatibilitySnapshotByCacheKey() {
        ArtifactCache cache = new ArtifactCache(LoggerFactory.getLogger("ArtifactCacheTest"), this.tempDir.resolve("cache"));
        cache.ensureLayout();

        ArtifactCache.StartupCompatibilityKey key = new ArtifactCache.StartupCompatibilityKey("startup-key-1");
        ContentInventory inventory = sampleInventory();
        CompatibilityReport report = sampleReport();
        cache.storeCompatibilitySnapshot(new ArtifactCache.CompatibilitySnapshot(
            new ArtifactCache.CompatibilityManifest(key, "metadata-1", "engine-1", "adapter-catalog-1", 1, Map.of("testmod", "fingerprint-1")),
            inventory,
            report
        ));

        ArtifactCache.CompatibilitySnapshot loaded = cache.loadCompatibilitySnapshot(key);
        assertNotNull(loaded);
        assertEquals("startup-key-1", loaded.manifest().startupKey().value());
        assertEquals("engine-1", loaded.manifest().engineFingerprint());
        assertEquals("adapter-catalog-1", loaded.manifest().adapterCatalogFingerprint());
        assertEquals(1, loaded.inventory().mods().size());
        assertEquals("testmod", loaded.report().profile("testmod").modId());
        assertNull(cache.loadCompatibilitySnapshot(new ArtifactCache.StartupCompatibilityKey("startup-key-2")));
    }

    @Test
    void ignoresLegacyCompatibilityManifestWithoutStartupKey() throws IOException {
        ArtifactCache cache = new ArtifactCache(LoggerFactory.getLogger("ArtifactCacheTest"), this.tempDir.resolve("cache"));
        cache.ensureLayout();

        Path compatibilityPath = this.tempDir.resolve("cache/compatibility");
        Files.createDirectories(compatibilityPath);
        Files.writeString(compatibilityPath.resolve("compatibility-manifest.json"), """
            {
              "cacheKey": "legacy-key",
              "metadataFingerprint": "metadata-1",
              "engineFingerprint": "engine-1",
              "modCount": 1,
              "modFingerprints": {
                "testmod": "fingerprint-1"
              }
            }
            """);
        Files.writeString(compatibilityPath.resolve("content-inventory.json"), Constants.GSON.toJson(sampleInventory()));
        Files.writeString(compatibilityPath.resolve("compatibility-report.json"), Constants.GSON.toJson(sampleReport()));

        assertNull(cache.loadCompatibilitySnapshot(new ArtifactCache.StartupCompatibilityKey("legacy-key")));
    }

    @Test
    void storesIndexConversionAndValidationArtifacts() throws IOException {
        ArtifactCache cache = new ArtifactCache(LoggerFactory.getLogger("ArtifactCacheTest"), this.tempDir.resolve("cache"));
        cache.ensureLayout();

        Path modRoot = this.tempDir.resolve("mod-root");
        Files.createDirectories(modRoot.resolve("assets/testmod/models/item"));
        Files.createDirectories(modRoot.resolve("assets/testmod/textures/item"));
        Files.createDirectories(modRoot.resolve("data/testmod/recipes"));
        Files.writeString(modRoot.resolve("assets/testmod/models/item/test_item.json"), "{\"parent\":\"minecraft:item/generated\"}");
        Files.writeString(modRoot.resolve("assets/testmod/textures/item/test_item.png"), "png");
        Files.writeString(modRoot.resolve("data/testmod/recipes/test_recipe.json"), "{}");
        ModInfo mod = new ModInfo("testmod", "testmod", "Test Mod", "1.0.0", null, List.of(modRoot));
        ModResourceIndex index = ModResourceIndex.create(mod, LoggerFactory.getLogger("ArtifactCacheTest"));

        ArtifactCache.IndexSnapshot snapshot = ArtifactCache.IndexSnapshot.from(List.of(mod), Map.of(mod.id(), index));
        cache.storeIndexSnapshot(snapshot);
        cache.storeConversionArtifact("testmod", new ArtifactCache.ConversionArtifact(
            "testmod",
            new ConversionKey("alg", "testmod", "1.0.0", "hydraulic", "26.2", "rfp", 2, 42, "metadata", "deps", 1),
            "packs/testmod.mcpack",
            "uuid-1"
        ));
        PackValidationReport report = new PackValidationReport(Map.of(
            "testmod",
            new PackValidationReport.ModValidation("packs/testmod.mcpack", true, true, 12, List.of(), List.of(), List.of("manual"))
        ));
        cache.storeValidationArtifact(report, "compat-key-1");

        assertNotNull(cache.loadIndexSnapshot());
        Map<String, ModResourceIndex> rehydrated = cache.loadReusableIndexes(List.of(mod));
        assertEquals(index.fingerprint().stableValue(), rehydrated.get("testmod").fingerprint().stableValue());
        assertEquals(index.modelCount(), rehydrated.get("testmod").modelCount());
        assertEquals(index.textureCount(), rehydrated.get("testmod").textureCount());
        assertEquals(
            index.resolveTexturePath(net.kyori.adventure.key.Key.key("testmod", "item/test_item")),
            rehydrated.get("testmod").resolveTexturePath(net.kyori.adventure.key.Key.key("testmod", "item/test_item"))
        );

        try (Reader reader = Files.newBufferedReader(this.tempDir.resolve("cache/conversions/testmod/conversion-manifest.json"))) {
            ArtifactCache.ConversionArtifact conversion = Constants.GSON.fromJson(reader, ArtifactCache.ConversionArtifact.class);
            assertEquals("uuid-1", conversion.packUuid());
        }
        try (Reader reader = Files.newBufferedReader(this.tempDir.resolve("cache/validation/pack-validation-report.json"))) {
            PackValidationReport written = Constants.GSON.fromJson(reader, PackValidationReport.class);
            assertEquals(1, written.perMod().size());
            assertEquals(1, written.perMod().get("testmod").manualActionCount());
        }

        Files.writeString(modRoot.resolve("assets/testmod/models/item/test_item.json"), "{\"parent\":\"minecraft:item/handheld\"}");
        assertEquals(0, cache.loadReusableIndexes(List.of(mod)).size());
    }

        @Test
        void ignoresLegacyIndexSnapshotsThatDoNotContainRehydrationData() throws IOException {
                ArtifactCache cache = new ArtifactCache(LoggerFactory.getLogger("ArtifactCacheTest"), this.tempDir.resolve("cache"));
                cache.ensureLayout();

                Path indexManifest = this.tempDir.resolve("cache/index/index-manifest.json");
                Files.createDirectories(indexManifest.getParent());
                Files.writeString(indexManifest, """
                        {
                            "algorithm": "HYDRAULIC_INDEX_SNAPSHOT_V1",
                            "modCount": 1,
                            "mods": {
                                "testmod": {
                                    "fingerprint": {
                                        "algorithm": "HYDRAULIC_INDEX_V1",
                                        "fileCount": 1,
                                        "totalSizeBytes": 1,
                                        "latestModifiedEpochMillis": 1,
                                        "digest": "abc"
                                    },
                                    "namespaceCount": 1,
                                    "blockStateCount": 0,
                                    "itemAssetCount": 0,
                                    "hasAssetFiles": true,
                                    "assetCounts": {
                                        "models": 1
                                    }
                                }
                            }
                        }
                        """);

                Path modRoot = this.tempDir.resolve("mod-root");
                Files.createDirectories(modRoot);
                ModInfo mod = new ModInfo("testmod", "testmod", "Test Mod", "1.0.0", null, List.of(modRoot));

                assertNotNull(cache.loadIndexSnapshot());
                assertEquals(0, cache.loadReusableIndexes(List.of(mod)).size());
        }

    private static ContentInventory sampleInventory() {
        return new ContentInventory(Map.of(
            "testmod",
            new ContentInventory.ModContentInventory(
                "testmod",
                "testmod",
                "Test Mod",
                "1.0.0",
                List.of("/tmp/testmod"),
                new ModFingerprint("testmod", "testmod", "1.0.0", "fabric", "26.2", 1, 1, 0, 0, 0, 0, 0, false, false, false, false, false, true, false, false),
                Map.of("blocks", 1),
                Map.of("blocks", List.of("testmod:block")),
                Map.of("block_assets", 1),
                Map.of("block_assets", List.of("testmod:block")),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of()
            )
        ));
    }

    private static CompatibilityReport sampleReport() {
        return new CompatibilityReport(
            "2026-09-08T00:00:00Z",
            MetadataIndex.Summary.empty(),
            List.of(),
            Map.of(
                "testmod",
                new CompatibilityProfile(
                    "testmod",
                    new ModFingerprint("testmod", "testmod", "1.0.0", "fabric", "26.2", 1, 1, 0, 0, 0, 0, 0, false, false, false, false, false, true, false, false),
                    SupportLevel.AUTOMATIC,
                    CompatibilityStatus.COMPLETE,
                    100,
                    Map.of("content", new SupportResult("content", SupportLevel.AUTOMATIC, CompatibilityStatus.COMPLETE, 100, List.of("present"), List.of(), List.of())),
                    Map.of("AUTOMATIC", 1),
                    List.of(),
                    List.of(),
                    List.of("note")
                )
            )
        );
    }
}