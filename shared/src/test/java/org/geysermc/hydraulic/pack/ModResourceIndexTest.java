package org.geysermc.hydraulic.pack;

import net.minecraft.resources.Identifier;
import net.kyori.adventure.key.Key;
import org.geysermc.hydraulic.platform.mod.ModInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModResourceIndexTest {
    @TempDir
    Path tempDir;

    @Test
    void indexesNamespacesBlockstatesAndItemAssetsAcrossRoots() throws IOException {
        Path firstRoot = this.tempDir.resolve("rootA");
        Path secondRoot = this.tempDir.resolve("rootB");

        Path blockstate = firstRoot.resolve("assets/examplemod/blockstates/machines/crusher.json");
        Path modernItem = secondRoot.resolve("assets/examplemod/items/tools/wrench.json");
        Path legacyItem = firstRoot.resolve("assets/examplemod/models/item/tools/hammer.json");
        Path blockModel = firstRoot.resolve("assets/examplemod/models/block/machines/crusher.json");
        Path texture = firstRoot.resolve("assets/examplemod/textures/block/crusher.png");
        Path sound = secondRoot.resolve("assets/examplemod/sounds/machines/crusher.ogg");
        Path language = firstRoot.resolve("assets/examplemod/lang/en_us.json");
        Path recipe = secondRoot.resolve("data/examplemod/recipes/machines/crusher.json");
        Path tag = firstRoot.resolve("data/examplemod/tags/blocks/machines.json");
        Path lootTable = secondRoot.resolve("data/examplemod/loot_tables/blocks/crusher.json");
        Files.createDirectories(blockstate.getParent());
        Files.createDirectories(modernItem.getParent());
        Files.createDirectories(legacyItem.getParent());
        Files.createDirectories(blockModel.getParent());
        Files.createDirectories(texture.getParent());
        Files.createDirectories(sound.getParent());
        Files.createDirectories(language.getParent());
        Files.createDirectories(recipe.getParent());
        Files.createDirectories(tag.getParent());
        Files.createDirectories(lootTable.getParent());
        Files.writeString(blockstate, "{}");
        Files.writeString(modernItem, "{}");
        Files.writeString(legacyItem, "{}");
        Files.writeString(blockModel, "{}");
        Files.writeString(texture, "png");
        Files.writeString(sound, "ogg");
        Files.writeString(language, "{}");
        Files.writeString(recipe, "{}");
        Files.writeString(tag, "{}");
        Files.writeString(lootTable, "{}");

        ModInfo mod = new ModInfo("examplemod", "examplemod", "Example Mod", "1.0.0", null, List.of(firstRoot, secondRoot));
        ModResourceIndex index = ModResourceIndex.create(mod, LoggerFactory.getLogger("ModResourceIndexTest"));

        assertTrue(index.hasAssetFiles());
        assertTrue(index.namespaces().contains("examplemod"));
        assertTrue(index.hasBlockState(Identifier.fromNamespaceAndPath("examplemod", "machines/crusher")));
        assertEquals(1, index.blockStateCount());
        assertEquals(blockstate, index.resolveBlockStatePath(Identifier.fromNamespaceAndPath("examplemod", "machines/crusher")));
        assertTrue(index.hasItemAsset(Identifier.fromNamespaceAndPath("examplemod", "tools/wrench")));
        assertTrue(index.hasItemAsset(Identifier.fromNamespaceAndPath("examplemod", "tools/hammer")));
        assertEquals(2, index.itemAssetCount());
        assertEquals(2, index.modelCount());
        assertEquals(1, index.textureCount());
        assertEquals(1, index.languageCount());
        assertTrue(!index.hasItemAsset(Identifier.fromNamespaceAndPath("examplemod", "tools/missing")));
        assertEquals(modernItem, index.resolveItemAssetPath(Identifier.fromNamespaceAndPath("examplemod", "tools/wrench")));
        assertEquals(legacyItem, index.resolveItemAssetPath(Identifier.fromNamespaceAndPath("examplemod", "tools/hammer")));
        assertEquals(blockModel, index.resolveModelPath(Key.key("examplemod", "block/machines/crusher")));
        assertEquals(texture, index.resolveTexturePath(Key.key("examplemod", "block/crusher")));
        assertEquals(language, index.resolveLanguagePath(Identifier.fromNamespaceAndPath("examplemod", "en_us")));
        assertNull(index.resolveItemAssetPath(Identifier.fromNamespaceAndPath("examplemod", "tools/missing")));
        assertEquals(Set.of("machines/crusher.json"), index.assetEntries("blockstates"));
        assertEquals(Set.of("tools/wrench.json"), index.assetEntries("item_models"));
        assertEquals(Set.of("item/tools/hammer.json", "block/machines/crusher.json"), index.assetEntries("models"));
        assertEquals(Set.of("block/crusher.png"), index.assetEntries("textures"));
        assertEquals(Set.of("machines/crusher.ogg"), index.assetEntries("sounds"));
        assertEquals(Set.of("en_us.json"), index.assetEntries("lang"));
        assertEquals(Set.of("examplemod:machines/crusher"), index.assetEntries("recipes"));
        assertEquals(recipe, index.resolveRecipePath(Identifier.fromNamespaceAndPath("examplemod", "machines/crusher")));
        assertEquals(Set.of("blocks/machines.json"), index.assetEntries("tags"));
        assertEquals(Set.of("blocks/crusher.json"), index.assetEntries("loot_tables"));
                assertEquals(Set.of(), index.dependencyNamespaces());
        assertEquals(10, index.fingerprint().fileCount());
        assertTrue(index.fingerprint().totalSizeBytes() > 0);
        assertTrue(!index.fingerprint().digest().isEmpty());
        assertEquals(1, index.fingerprint("blockstates").fileCount());
        assertEquals(2, index.fingerprint("models").fileCount());
        assertEquals(1, index.fingerprint("textures").fileCount());
        assertEquals(1, index.fingerprint("recipes").fileCount());
        assertEquals(1, index.fingerprint("tags").fileCount());
        assertEquals(1, index.fingerprint("loot_tables").fileCount());
        assertEquals(1, index.fingerprint("lang").fileCount());

        ModResourceIndex rehydrated = ModResourceIndex.rehydrate(index.snapshot());
        assertEquals(language, rehydrated.resolveLanguagePath(Identifier.fromNamespaceAndPath("examplemod", "en_us")));
    }

        @Test
        void recordsExternalDependencyNamespacesFromModelsAndEquipment() throws IOException {
                Path root = this.tempDir.resolve("root");
                Path model = root.resolve("assets/examplemod/models/item/test_item.json");
                Path equipment = root.resolve("assets/examplemod/equipment/test_asset.json");
                Files.createDirectories(model.getParent());
                Files.createDirectories(equipment.getParent());
                Files.writeString(model, """
                        {
                            "parent": "othermod:item/base",
                            "textures": {
                                "layer0": "thirdmod:item/layer"
                            },
                            "overrides": [
                                {
                                    "model": "fourthmod:item/override"
                                }
                            ]
                        }
                        """);
                Files.writeString(equipment, """
                        {
                            "layers": {
                                "humanoid": [
                                    {
                                        "texture": "fifthmod:entity/test"
                                    }
                                ]
                            }
                        }
                        """);

                ModInfo mod = new ModInfo("examplemod", "examplemod", "Example Mod", "1.0.0", null, List.of(root));
                ModResourceIndex index = ModResourceIndex.create(mod, LoggerFactory.getLogger("ModResourceIndexTest"));

                assertEquals(Set.of("othermod", "thirdmod", "fourthmod", "fifthmod"), index.dependencyNamespaces());
                assertEquals(
                    Set.of(Key.key("othermod:item/base"), Key.key("thirdmod:item/layer"), Key.key("fourthmod:item/override")),
                    index.modelDependencies().get(Identifier.fromNamespaceAndPath("examplemod", "item/test_item"))
                );
                assertEquals(
                    Set.of(Key.key("fifthmod:entity/test")),
                    index.equipmentDependencies().get(Identifier.fromNamespaceAndPath("examplemod", "test_asset"))
                );
        }

    @Test
    void prefersEarlierRootsWhenDuplicateAssetsExist() throws IOException {
        Path firstRoot = this.tempDir.resolve("rootA");
        Path secondRoot = this.tempDir.resolve("rootB");
        Path firstItem = firstRoot.resolve("assets/examplemod/items/test_item.json");
        Path secondItem = secondRoot.resolve("assets/examplemod/items/test_item.json");
        Files.createDirectories(firstItem.getParent());
        Files.createDirectories(secondItem.getParent());
        Files.writeString(firstItem, "{\"from\":\"first\"}");
        Files.writeString(secondItem, "{\"from\":\"second\"}");

        ModInfo mod = new ModInfo("examplemod", "examplemod", "Example Mod", "1.0.0", null, List.of(firstRoot, secondRoot));
        ModResourceIndex index = ModResourceIndex.create(mod, LoggerFactory.getLogger("ModResourceIndexTest"));

        Path resolved = index.resolveItemAssetPath(Identifier.fromNamespaceAndPath("examplemod", "test_item"));
        assertNotNull(resolved);
        assertEquals(firstItem, resolved);
    }

    @Test
    void reportsWhenModHasNoAssetFiles() {
        ModInfo mod = new ModInfo("examplemod", "examplemod", "Example Mod", "1.0.0", null, List.of(this.tempDir));

        ModResourceIndex index = ModResourceIndex.create(mod, LoggerFactory.getLogger("ModResourceIndexTest"));

        assertTrue(index.namespaces().isEmpty());
        assertEquals(0, index.blockStateCount());
        assertEquals(0, index.itemAssetCount());
        assertEquals(0, index.modelCount());
        assertEquals(0, index.textureCount());
        assertEquals(0, index.languageCount());
        assertEquals(Set.of(), index.dependencyNamespaces());
        assertNull(index.resolveBlockStatePath(Identifier.fromNamespaceAndPath("examplemod", "test_block")));
        assertTrue(!index.hasItemAsset(Identifier.fromNamespaceAndPath("examplemod", "test_item")));
        assertNull(index.resolveItemAssetPath(Identifier.fromNamespaceAndPath("examplemod", "test_item")));
        assertNull(index.resolveModelPath(Key.key("examplemod", "missing")));
        assertNull(index.resolveTexturePath(Key.key("examplemod", "missing")));
        assertNull(index.resolveLanguagePath(Identifier.fromNamespaceAndPath("examplemod", "missing")));
        assertEquals(false, index.hasAssetFiles());
    }
}
