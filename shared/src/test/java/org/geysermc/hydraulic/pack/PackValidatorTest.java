package org.geysermc.hydraulic.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackValidatorTest {
    @TempDir
    Path tempDir;

    @Test
    void validatesPackAndReportsWarnings() throws IOException {
        Path pack = this.tempDir.resolve("examplemod.mcpack");
        writeZip(pack,
            entry("manifest.json", """
                {"header":{"name":"Example","uuid":"123e4567-e89b-12d3-a456-426614174000","version":[1,0,0]},"modules":[{"type":"resources","uuid":"123e4567-e89b-12d3-a456-426614174001","version":[1,0,0]}]}
                """),
            entry("blocks/example.json", "{}")
        );

        PackValidationReport.ModValidation validation = new PackValidator().validate(pack);

        assertTrue(validation.created());
        assertTrue(validation.valid());
        assertEquals(0, validation.errorCount());
        assertEquals(1, validation.warningCount());
        assertEquals("pack.icon.missing", validation.warnings().get(0).code());
    }

    @Test
    void reportsErrorsForMissingOutput() {
        PackValidationReport.ModValidation validation = new PackValidator().validate(this.tempDir.resolve("missing.mcpack"));

        assertFalse(validation.created());
        assertFalse(validation.valid());
        assertEquals(1, validation.errorCount());
        assertEquals("pack.output.missing", validation.errors().get(0).code());
        assertEquals(1, validation.manualActionCount());
    }

    @Test
    void reportsErrorsForInvalidGeneratedJson() throws IOException {
        Path pack = this.tempDir.resolve("broken.mcpack");
        writeZip(pack,
            entry("manifest.json", """
                {"header":{"name":"Example","uuid":"123e4567-e89b-12d3-a456-426614174000","version":[1,0,0]},"modules":[{"type":"resources","uuid":"123e4567-e89b-12d3-a456-426614174001","version":[1,0,0]}]}
                """),
            entry("items/example.json", "{broken")
        );

        PackValidationReport.ModValidation validation = new PackValidator().validate(pack);

        assertFalse(validation.valid());
        PackValidationReport.ValidationMessage message = validation.errors().stream()
            .filter(error -> error.code().equals("pack.json.invalid"))
            .findFirst()
            .orElseThrow();
        assertEquals(PackValidationReport.FailureClassification.GENERIC_GENERATOR_DEFECT, message.classification());
    }

    @Test
    void reportsErrorsForMissingRequiredSelectedTextures() throws IOException {
        Path pack = this.tempDir.resolve("missing-texture.mcpack");
        writeZip(pack,
            entry("manifest.json", """
                {"header":{"name":"Example","uuid":"123e4567-e89b-12d3-a456-426614174000","version":[1,0,0]},"modules":[{"type":"resources","uuid":"123e4567-e89b-12d3-a456-426614174001","version":[1,0,0]}]}
                """),
            entry("items/example.json", "{}")
        );

        PackValidationReport.ModValidation validation = new PackValidator().validate(
            pack,
            PackValidator.TextureExpectations.ofArchiveEntries(java.util.List.of("textures/items/examplemod/required.png"))
        );

        assertFalse(validation.valid());
        assertTrue(validation.errors().stream().anyMatch(message -> message.code().equals("pack.texture.required_missing")));
    }

    @Test
    void reportsWarningsForUnreferencedGeneratedTextures() throws IOException {
        Path pack = this.tempDir.resolve("extra-texture.mcpack");
        writeZip(pack,
            entry("manifest.json", """
                {"header":{"name":"Example","uuid":"123e4567-e89b-12d3-a456-426614174000","version":[1,0,0]},"modules":[{"type":"resources","uuid":"123e4567-e89b-12d3-a456-426614174001","version":[1,0,0]}]}
                """),
            entry("items/example.json", "{}"),
            entry("textures/items/examplemod/required.png", "png"),
            entry("textures/items/examplemod/extra.png", "png")
        );

        PackValidationReport.ModValidation validation = new PackValidator().validate(
            pack,
            PackValidator.TextureExpectations.ofArchiveEntries(java.util.List.of("textures/items/examplemod/required.png"))
        );

        assertTrue(validation.valid());
        assertTrue(validation.warnings().stream().anyMatch(message -> message.code().equals("pack.texture.unreferenced_output")));
    }

    @Test
    void acceptsConverterTransformedEquipmentTextureOutput() throws IOException {
        Path pack = this.tempDir.resolve("equipment-texture.mcpack");
        writeZip(pack,
            entry("manifest.json", """
                {"header":{"name":"Example","uuid":"123e4567-e89b-12d3-a456-426614174000","version":[1,0,0]},"modules":[{"type":"resources","uuid":"123e4567-e89b-12d3-a456-426614174001","version":[1,0,0]}]}
                """),
            entry("textures/models/create/armor/copper_1.png", "png")
        );

        PackValidationReport.ModValidation validation = new PackValidator().validate(
            pack,
            PackValidator.TextureExpectations.ofArchiveEntries(java.util.List.of("textures/entity/create/equipment/humanoid/copper.png"))
        );

        assertTrue(validation.valid());
        assertFalse(validation.errors().stream().anyMatch(message -> message.code().equals("pack.texture.required_missing")));
        assertFalse(validation.warnings().stream().anyMatch(message -> message.code().equals("pack.texture.unreferenced_output")));
    }

    @Test
    void acceptsConverterTransformedLeggingsTextureOutput() throws IOException {
        Path pack = this.tempDir.resolve("leggings-texture.mcpack");
        writeZip(pack,
            entry("manifest.json", """
                {"header":{"name":"Example","uuid":"123e4567-e89b-12d3-a456-426614174000","version":[1,0,0]},"modules":[{"type":"resources","uuid":"123e4567-e89b-12d3-a456-426614174001","version":[1,0,0]}]}
                """),
            entry("textures/models/create/armor/copper_2.png", "png")
        );

        PackValidationReport.ModValidation validation = new PackValidator().validate(
            pack,
            PackValidator.TextureExpectations.ofArchiveEntries(java.util.List.of("textures/entity/create/equipment/humanoid_leggings/copper.png"))
        );

        assertTrue(validation.valid());
    }

    @Test
    void reportsBedrockWarningsForLongArchivePaths() throws IOException {
        Path pack = this.tempDir.resolve("long-path.mcpack");
        String longPath = "textures/blocks/example/" + "nested/".repeat(9) + "stone.png";
        writeZip(pack,
            entry("manifest.json", """
                {"header":{"name":"Example","uuid":"123e4567-e89b-12d3-a456-426614174000","version":[1,0,0]},"modules":[{"type":"resources","uuid":"123e4567-e89b-12d3-a456-426614174001","version":[1,0,0]}]}
                """),
            entry(longPath, "png")
        );

        PackValidationReport.ModValidation validation = new PackValidator().validate(pack);

        assertTrue(validation.valid());
        assertTrue(validation.warnings().stream().anyMatch(message -> message.code().equals("pack.path.long")));
        assertTrue(validation.manualActions().stream().anyMatch(action -> action.contains("80 characters")));
    }

    private static void writeZip(Path output, ZipContent... contents) throws IOException {
        try (ZipOutputStream stream = new ZipOutputStream(Files.newOutputStream(output))) {
            for (ZipContent content : contents) {
                stream.putNextEntry(new ZipEntry(content.path()));
                stream.write(content.content().getBytes(StandardCharsets.UTF_8));
                stream.closeEntry();
            }
        }
    }

    private static ZipContent entry(String path, String content) {
        return new ZipContent(path, content);
    }

    private record ZipContent(String path, String content) {
    }
}