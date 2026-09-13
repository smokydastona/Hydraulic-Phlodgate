package org.geysermc.hydraulic.compat.validation;

import org.geysermc.hydraulic.compat.CompatibilityStatus;
import org.geysermc.hydraulic.compat.ir.CompiledCompatibilityPlan;
import org.geysermc.hydraulic.compat.model.Confidence;
import org.geysermc.hydraulic.compat.model.SupportLevel;
import org.geysermc.hydraulic.compat.runtime.RuntimeBridgeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MultiLevelValidationHarnessTest {

    @Test
    @DisplayName("ValidationRunner executes multi-level validation on a modpack corpus")
    void validationRunnerEvaluatesModpackCorpus() {
        CompiledCompatibilityPlan validPlan = new CompiledCompatibilityPlan(
            "mod_a",
            "block",
            "mod_a:crusher",
            "mod_a:crusher",
            SupportLevel.ADAPTED,
            CompatibilityStatus.COMPLETE,
            90,
            new Confidence(0.9, "test"),
            List.of(),
            List.of("item_transfer_bridge", "machine_behavior_bridge"),
            List.of(RuntimeBridgeKind.ITEM_TRANSFER, RuntimeBridgeKind.MACHINE_BEHAVIOR),
            Map.of("has_processing", "true", "can_insert", "true", "can_extract", "true"),
            true, null, true, null, true, true, false, false, false, null, null,
            List.of(), List.of(), List.of(), null, false, false,
            SupportLevel.ADAPTED, "machine", List.of()
        );

        CompiledCompatibilityPlan visualPlan = new CompiledCompatibilityPlan(
            "mod_b",
            "block",
            "mod_b:decorative_pillar",
            "mod_b:decorative_pillar",
            SupportLevel.VISUAL_ONLY,
            CompatibilityStatus.COMPLETE,
            60,
            new Confidence(0.8, "test"),
            List.of(),
            List.of(),
            List.of(),
            Map.of(),
            true, null, true, null, true, true, false, false, false, null, null,
            List.of(), List.of(), List.of(), null, false, false,
            null, null, List.of()
        );

        MultiLevelValidationHarness.ValidationRunner runner = new MultiLevelValidationHarness.ValidationRunner();
        MultiLevelValidationHarness.ModpackCorpusSummary summary =
            runner.evaluateModpackCorpus("Test Modpack", List.of(validPlan, visualPlan));

        assertNotNull(summary);
        assertEquals("Test Modpack", summary.modpackName());
        assertEquals(2, summary.totalMods());
        assertEquals(2, summary.totalObjects());
        assertEquals(2, summary.passingObjects());
        assertEquals(0, summary.failingObjects());
        assertEquals(100, summary.overallCompliancePercentage());
    }
}
