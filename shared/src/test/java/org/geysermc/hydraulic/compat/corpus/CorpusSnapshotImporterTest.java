package org.geysermc.hydraulic.compat.corpus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorpusSnapshotImporterTest {
    @TempDir
    Path tempDir;

    @Test
    void extractsPackStructureAndScriptCapabilitiesIntoGeneratedCorpus() throws IOException {
        Path snapshot = this.tempDir.resolve("addon");
        Files.createDirectories(snapshot.resolve("behavior_packs/demo"));
        Files.createDirectories(snapshot.resolve("resource_packs/demo/textures/blocks"));
        Files.createDirectories(snapshot.resolve("resource_packs/demo/models"));
        Files.createDirectories(snapshot.resolve("scripts"));
        Files.createDirectories(snapshot.resolve("behavior_packs/demo/recipes"));
        Files.writeString(snapshot.resolve("behavior_packs/demo/manifest.json"), """
            {
              "format_version": 2,
              "header": {"name": "Demo"},
              "modules": [{"type": "data", "version": [1, 0, 0]}],
              "dependencies": [{"uuid": "demo-core"}]
            }
            """);
        Files.writeString(snapshot.resolve("resource_packs/demo/manifest.json"), """
            {
              "format_version": 2,
              "header": {"name": "Demo Resources"},
              "modules": [{"type": "resources", "version": [1, 0, 0]}]
            }
            """);
        Files.writeString(snapshot.resolve("behavior_packs/demo/recipes/processor.json"), "{}");
        Files.writeString(snapshot.resolve("scripts/processor.js"), """
            import { world, system } from '@minecraft/server';
            system.runInterval(() => { /* machine inventory fluid energy pipe */ });
            """);
        Files.writeString(snapshot.resolve("resource_packs/demo/models/processor.json"), "{}");
        Files.writeString(snapshot.resolve("resource_packs/demo/textures/blocks/processor.png"), "texture");

        AddonCorpusLoader loader = new AddonCorpusLoader(LoggerFactory.getLogger("CorpusImporterTest"), this.tempDir);
        loader.ensureLayout();
        CorpusSnapshotImporter importer = new CorpusSnapshotImporter(LoggerFactory.getLogger("CorpusImporterTest"), loader);
        CorpusSnapshotImporter.ImportResult result = importer.importSnapshot(snapshot, request("demo-addon"));

        assertEquals("demo-addon", result.entry().identity().corpusId());
        assertTrue(result.entry().behaviorPack().hasBehaviorPack());
        assertTrue(result.entry().resourcePack().hasResourcePack());
        assertTrue(result.entry().capabilities().machineTypes().contains("machine"));
        assertTrue(result.entry().capabilities().transferTypes().contains("item_transfer"));
        assertTrue(result.entry().capabilities().fluidTypes().contains("fluid"));
        assertTrue(result.entry().evidence().scriptEvidence().contains("scripts/processor.js"));
        assertTrue(loader.index().entries().containsKey("demo-addon"));
        assertEquals(1, loader.loadAdmissibleEntries().size());
    }

    @Test
    void rejectsUnsafeArchiveEntriesWithoutWritingOutsideCorpus() throws IOException {
        Path archive = this.tempDir.resolve("unsafe.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("../escaped.json"));
            output.write("{}".getBytes());
            output.closeEntry();
            output.putNextEntry(new ZipEntry("behavior_packs/demo/manifest.json"));
            output.write("{\"format_version\":2,\"modules\":[{\"type\":\"data\"}]}".getBytes());
            output.closeEntry();
        }

        AddonCorpusLoader loader = new AddonCorpusLoader(LoggerFactory.getLogger("CorpusImporterTest"), this.tempDir);
        loader.ensureLayout();
        CorpusSnapshotImporter importer = new CorpusSnapshotImporter(LoggerFactory.getLogger("CorpusImporterTest"), loader);
        CorpusSnapshotImporter.ImportResult result = importer.importSnapshot(archive, request("safe-addon"));

        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("unsafe archive path")));
        assertTrue(!Files.exists(this.tempDir.resolve("escaped.json")));
        assertEquals("safe-addon", loader.index().entries().keySet().iterator().next());
    }

    private static CorpusSnapshotImporter.ImportRequest request(String corpusId) {
        return new CorpusSnapshotImporter.ImportRequest(
            corpusId,
            "demo:processor",
            AddonCorpusEntry.SourceType.GITHUB,
            "https://github.com/demo/" + corpusId,
            "https://github.com/demo/" + corpusId,
            null,
            null,
            "MIT",
            null,
            null,
            true,
            true,
            true,
            false,
            "test-author",
            "Demo Addon",
            "Test addon",
            "1.0.0",
            Set.of("1.21.0"),
            "1.21.0",
            "1.21.0",
            List.of("1.0.0"),
            "test",
            null,
            null
        );
    }
}
