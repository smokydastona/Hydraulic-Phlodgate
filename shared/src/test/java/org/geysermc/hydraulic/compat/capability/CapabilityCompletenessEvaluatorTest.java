package org.geysermc.hydraulic.compat.capability;

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

class CapabilityCompletenessEvaluatorTest {

    @Test
    @DisplayName("Evaluate complete processing machine against System 15 Matrix")
    void evaluateCompleteProcessingMachine() {
        CompiledCompatibilityPlan plan = new CompiledCompatibilityPlan(
            "test_mod",
            "block",
            "test_mod:crusher",
            "test_mod:crusher",
            SupportLevel.ADAPTED,
            CompatibilityStatus.COMPLETE,
            95,
            new Confidence(0.95, "test"),
            List.of(),
            List.of("item_transfer_bridge", "machine_behavior_bridge"),
            List.of(RuntimeBridgeKind.ITEM_TRANSFER, RuntimeBridgeKind.MACHINE_BEHAVIOR),
            Map.of("has_processing", "true", "can_insert", "true", "can_extract", "true"),
            true,
            null,
            true,
            null,
            true,
            true,
            false,
            false,
            false,
            null,
            null,
            List.of(),
            List.of(),
            List.of(),
            null,
            false,
            false,
            SupportLevel.ADAPTED,
            "machine",
            List.of()
        );

        CapabilityCompletenessEvaluator.CapabilityCompletenessReport report = CapabilityCompletenessEvaluator.evaluate(plan);
        assertNotNull(report);
        assertEquals("test_mod:crusher", report.objectId());
        assertEquals(CapabilityCompletenessEvaluator.OverallVerdict.FULL_SUPPORT, report.verdict());
        assertFalse(report.hasCriticalFailure());
        assertTrue(report.isUsableInGameplay());

        assertEquals(CapabilityCompletenessEvaluator.MatrixStatus.PASS, report.content().status());
        assertEquals(CapabilityCompletenessEvaluator.MatrixStatus.PASS, report.presentation().status());
        assertEquals(CapabilityCompletenessEvaluator.MatrixStatus.PASS, report.behavior().status());
        assertEquals(CapabilityCompletenessEvaluator.MatrixStatus.PASS, report.interaction().status());
    }

    @Test
    @DisplayName("Evaluate critical-failing machine correctly marks UNSUPPORTED/VISUAL_ONLY")
    void evaluateCriticalFailingMachine() {
        CompiledCompatibilityPlan plan = new CompiledCompatibilityPlan(
            "test_mod",
            "block",
            "test_mod:broken_machine",
            "test_mod:broken_machine",
            SupportLevel.VISUAL_ONLY,
            CompatibilityStatus.PARTIAL,
            0,
            new Confidence(0.95, "test"),
            List.of(),
            List.of(),
            List.of(),
            Map.of("critical_failure", "true", "can_insert", "true"),
            true,
            null,
            true,
            null,
            true,
            true,
            false,
            false,
            false,
            null,
            null,
            List.of(),
            List.of(),
            List.of(),
            null,
            false,
            false,
            SupportLevel.UNSUPPORTED,
            "broken",
            List.of()
        );

        CapabilityCompletenessEvaluator.CapabilityCompletenessReport report = CapabilityCompletenessEvaluator.evaluate(plan);
        assertNotNull(report);
        assertEquals(CapabilityCompletenessEvaluator.OverallVerdict.UNSUPPORTED, report.verdict());
        assertTrue(report.hasCriticalFailure());
        assertFalse(report.isUsableInGameplay());
        assertEquals(CapabilityCompletenessEvaluator.MatrixStatus.FAIL, report.behavior().status());
        assertFalse(report.criticalIssues().isEmpty());
    }
}
