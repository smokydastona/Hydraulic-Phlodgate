package org.geysermc.hydraulic.pack.index;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import net.minecraft.resources.Identifier;
import org.geysermc.hydraulic.pack.ModResourceIndex;
import org.geysermc.hydraulic.platform.mod.ModInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UniversalResourceIndexTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesOwningModAndIndexedPaths() throws Exception {
        Path modRoot = this.tempDir.resolve("examplemod");
        Path assets = modRoot.resolve("assets/examplemod/blockstates/test_block.json");
        Path language = modRoot.resolve("assets/examplemod/lang/en_us.json");
        Files.createDirectories(assets.getParent());
        Files.createDirectories(language.getParent());
        Files.writeString(assets, "{}");
        Files.writeString(language, "{}");

        ModInfo mod = new ModInfo("examplemod", "examplemod", "Example Mod", "1.0.0", null, java.util.List.of(modRoot));
        ModResourceIndex index = ModResourceIndex.create(mod, LoggerFactory.getLogger("UniversalResourceIndexTest"));

        ListMultimap<String, ModInfo> namespacesToMods = ArrayListMultimap.create();
        namespacesToMods.put("examplemod", mod);

        ListMultimap<String, Identifier> modsToBlocks = ArrayListMultimap.create();
        Identifier block = Identifier.fromNamespaceAndPath("examplemod", "test_block");
        modsToBlocks.put("examplemod", block);

        UniversalResourceIndex universal = UniversalResourceIndex.fromLookups(
            Map.of("examplemod", index),
            namespacesToMods,
            modsToBlocks,
            ArrayListMultimap.create()
        );

        assertEquals("examplemod", universal.resolveModForBlock(block));
        assertNotNull(universal.resolveModForNamespace("examplemod"));
        assertNotNull(index.resolveBlockStatePath(block));
        Identifier languageKey = Identifier.fromNamespaceAndPath("examplemod", "en_us");
        assertEquals(languageKey, universal.resolveLanguageKey(languageKey));
        assertEquals(language, universal.allLanguages().get(languageKey));
    }
}
