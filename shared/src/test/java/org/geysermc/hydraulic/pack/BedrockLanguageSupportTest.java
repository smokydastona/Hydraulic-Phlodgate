package org.geysermc.hydraulic.pack;

import net.minecraft.resources.Identifier;
import org.geysermc.hydraulic.platform.mod.ModInfo;
import org.geysermc.pack.bedrock.resource.BedrockResourcePack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BedrockLanguageSupportTest {
    @TempDir
    Path tempDir;

    @Test
    void copiesIndexedJavaLanguageJsonIntoBedrockLanguageMap() throws Exception {
        Path root = this.tempDir.resolve("root");
        Path language = root.resolve("assets/examplemod/lang/en_us.json");
        Files.createDirectories(language.getParent());
        Files.writeString(language, """
            {
              "block.examplemod.crusher": "Crusher",
              "item.examplemod.wrench": "Wrench",
              "ignored.number": 4
            }
            """);

        ModInfo mod = new ModInfo("examplemod", "examplemod", "Example Mod", "1.0.0", null, List.of(root));
        ModResourceIndex index = ModResourceIndex.create(mod, LoggerFactory.getLogger("BedrockLanguageSupportTest"));
        BedrockResourcePack pack = new BedrockResourcePack(this.tempDir.resolve("bedrock-pack"));

        BedrockLanguageSupport.includeIndexedLanguages(pack, index, LoggerFactory.getLogger("BedrockLanguageSupportTest"));

        assertEquals("Crusher", pack.languages().translation("en_US", "block.examplemod.crusher"));
        assertEquals("Wrench", pack.languages().translation("en_US", "item.examplemod.wrench"));
        assertEquals(false, pack.languages().language("en_US").containsKey("ignored.number"));
        String exportedLanguage = new String(pack.extraFiles().get("texts/en_US.lang"), StandardCharsets.UTF_8);
        assertEquals(true, exportedLanguage.contains("block.examplemod.crusher=Crusher\n"));
        assertEquals(true, exportedLanguage.contains("item.examplemod.wrench=Wrench\n"));
    }

    @Test
    void defaultTranslationsDoNotOverwriteIndexedTranslations() throws Exception {
        BedrockResourcePack pack = new BedrockResourcePack(this.tempDir.resolve("bedrock-pack"));
        pack.addLanguage("en_US", java.util.Map.of("item.examplemod.wrench", "Indexed Wrench"));

        BedrockLanguageSupport.includeDefaultTranslation(pack, "item.examplemod.wrench", "Fallback Wrench");
        BedrockLanguageSupport.includeDefaultTranslation(pack, "item.examplemod.copper_wrench", BedrockLanguageSupport.fallbackName(Identifier.fromNamespaceAndPath("examplemod", "tools/copper_wrench")));

        assertEquals("Indexed Wrench", pack.languages().translation("en_US", "item.examplemod.wrench"));
        assertEquals("Copper Wrench", pack.languages().translation("en_US", "item.examplemod.copper_wrench"));
    }
}