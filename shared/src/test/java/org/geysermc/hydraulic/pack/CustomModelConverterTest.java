package org.geysermc.hydraulic.pack;

import net.kyori.adventure.key.Key;
import org.geysermc.hydraulic.pack.converter.CustomModelConverter;
import org.geysermc.hydraulic.platform.mod.ModInfo;
import org.geysermc.pack.converter.pipeline.ExtractionContext;
import org.geysermc.pack.converter.util.LogListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import team.unnamed.creative.model.Model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomModelConverterTest {
    @TempDir
    Path tempDir;

    @Test
    void extractsIndexedModelsWithoutDependingOnParsedPackModels() throws IOException {
        this.writeModel("assets/examplemod/models/item/indexed.json", "minecraft:item/generated", "examplemod:item/indexed");

        ModResourceIndex resourceIndex = ModResourceIndex.create(mod(), LoggerFactory.getLogger("CustomModelConverterTest"));
        IndexedModelProvider provider = new IndexedModelProvider(
            LoggerFactory.getLogger("CustomModelConverterTest"),
            resourceIndex.modelPaths(),
            null,
            8
        );
        TextureDependencyGraph textureDependencies = new TextureDependencyGraph();
        CustomModelConverter converter = new CustomModelConverter(resourceIndex, provider, textureDependencies);

        team.unnamed.creative.ResourcePack parsedPack = team.unnamed.creative.ResourcePack.resourcePack();
        parsedPack.model(Model.model()
            .key(Key.key("examplemod", "item/parsed_only"))
            .parent(Key.key("minecraft", "item/generated"))
            .build());

        Collection<Model> extracted = converter.extract(parsedPack, new ExtractionContext(null, Optional.empty(), new NoOpLogListener()));

        assertEquals(1, extracted.size());
        assertTrue(extracted.stream().anyMatch(model -> Key.key("examplemod", "item/indexed").equals(model.key())));
        assertFalse(extracted.stream().anyMatch(model -> Key.key("examplemod", "item/parsed_only").equals(model.key())));
        assertEquals(1, textureDependencies.requiredTextures().size());
        assertTrue(textureDependencies.requiredTextures().contains(Key.key("examplemod", "item/indexed")));
    }

    private ModInfo mod() {
        return new ModInfo("examplemod", "examplemod", "Example Mod", "1.0.0", null, List.of(this.tempDir));
    }

    private Path writeModel(String relativePath, String parent, String layer0) throws IOException {
        Path path = this.tempDir.resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, """
            {
              "parent": "%s",
              "textures": {
                "layer0": "%s"
              }
            }
            """.formatted(parent, layer0));
        return path;
    }

    private static final class NoOpLogListener implements LogListener {
        @Override
        public void debugUnchecked(String message) {
        }

        @Override
        public void info(String message) {
        }

        @Override
        public void warn(String message) {
        }

        @Override
        public void error(String message) {
        }

        @Override
        public void error(String message, Throwable throwable) {
        }
    }
}