package org.geysermc.hydraulic.pack;

import org.geysermc.hydraulic.Constants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackValidationTrackerTest {
    @TempDir
    Path tempDir;

    @Test
    void recordsAndWritesValidationArtifacts() throws IOException {
        Path reportPath = this.tempDir.resolve("reports/pack-validation-report.json");
        PackValidationTracker tracker = new PackValidationTracker(LoggerFactory.getLogger("PackValidationTrackerTest"), reportPath);

        tracker.record("examplemod", new PackValidationReport.ModValidation(
            "packs/examplemod.zip",
            true,
            false,
            12,
            List.of(new PackValidationReport.ValidationMessage("pack.json.invalid", "Broken generated JSON", "blocks.json")),
            List.of(new PackValidationReport.ValidationMessage("pack.icon.missing", "Icon missing", "pack_icon.png")),
            List.of("Inspect generated JSON", "Add pack icon")
        ));

        PackValidationReport snapshot = tracker.snapshot();
        assertNotNull(snapshot.perMod().get("examplemod"));
        assertEquals(1, snapshot.perMod().get("examplemod").errorCount());
        assertEquals(2, snapshot.perMod().get("examplemod").manualActionCount());
        assertEquals(PackValidationReport.FailureClassification.GENERIC_GENERATOR_DEFECT,
            snapshot.perMod().get("examplemod").errors().getFirst().classification());

        PackValidationReport written;
        try (Reader reader = Files.newBufferedReader(reportPath)) {
            written = Constants.GSON.fromJson(reader, PackValidationReport.class);
        }

        assertNotNull(written);
        assertEquals(false, written.perMod().get("examplemod").valid());
        assertEquals("pack.json.invalid", written.perMod().get("examplemod").errors().get(0).code());
        assertEquals(PackValidationReport.FailureClassification.GENERIC_GENERATOR_DEFECT,
            written.perMod().get("examplemod").errors().get(0).classification());
    }
}