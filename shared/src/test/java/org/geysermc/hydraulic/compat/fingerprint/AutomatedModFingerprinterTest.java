package org.geysermc.hydraulic.compat.fingerprint;

import org.geysermc.hydraulic.compat.CompatibilityStatus;
import org.geysermc.hydraulic.compat.ir.CompiledCompatibilityPlan;
import org.geysermc.hydraulic.compat.model.Confidence;
import org.geysermc.hydraulic.compat.model.SupportLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AutomatedModFingerprinterTest {

    @Test
    @DisplayName("PlanOptimizer analyzes mod plans and classifies archetype")
    void planOptimizerClassifiesTechMod() {
        CompiledCompatibilityPlan plan1 = new CompiledCompatibilityPlan(
            "tech_mod",
            "block",
            "tech_mod:crusher",
            "tech_mod:crusher",
            SupportLevel.ADAPTED,
            CompatibilityStatus.COMPLETE,
            90,
            new Confidence(0.9, "test"),
            List.of(),
            List.of(),
            List.of(),
            Map.of("has_processing", "true", "can_receive_energy", "true"),
            true, null, true, null, true, true, false, false, false, null, null,
            List.of(), List.of(), List.of(), null, false, false,
            SupportLevel.ADAPTED, "tech", List.of()
        );

        CompiledCompatibilityPlan plan2 = new CompiledCompatibilityPlan(
            "tech_mod",
            "item",
            "tech_mod:battery",
            "tech_mod:battery",
            SupportLevel.AUTOMATIC,
            CompatibilityStatus.COMPLETE,
            100,
            new Confidence(1.0, "test"),
            List.of(),
            List.of(),
            List.of(),
            Map.of("can_receive_energy", "true"),
            true, null, true, null, false, false, false, false, false, null, null,
            List.of(), List.of(), List.of(), null, false, false,
            SupportLevel.AUTOMATIC, "tech", List.of()
        );

        AutomatedModFingerprinter.ModFingerprintReport report =
            AutomatedModFingerprinter.PlanOptimizer.analyzeMod("tech_mod", List.of(plan1, plan2));

        assertNotNull(report);
        assertEquals("tech_mod", report.modId());
        assertEquals(AutomatedModFingerprinter.ModArchetype.TECH_INDUSTRIAL, report.primaryArchetype());
        assertEquals(2, report.totalObjects());
        assertEquals(95, report.averageScore());
    }

    @Test
    @DisplayName("PlanOptimizer ranks adapter opportunities for visual-only objects")
    void planOptimizerRanksAdapterOpportunities() {
        CompiledCompatibilityPlan failingPlan = new CompiledCompatibilityPlan(
            "tech_mod",
            "block",
            "tech_mod:generator",
            "tech_mod:generator",
            SupportLevel.VISUAL_ONLY,
            CompatibilityStatus.PARTIAL,
            0,
            new Confidence(0.5, "test"),
            List.of(),
            List.of(),
            List.of(),
            Map.of("has_processing", "true"),
            true, null, true, null, true, true, false, false, false, null, null,
            List.of(), List.of(), List.of(), null, false, false,
            SupportLevel.UNSUPPORTED, "tech", List.of()
        );

        List<AutomatedModFingerprinter.RankedAdapterCandidate> candidates =
            AutomatedModFingerprinter.PlanOptimizer.rankAdapterOpportunities(List.of(failingPlan));

        assertEquals(1, candidates.size());
        assertEquals("tech_mod:generator", candidates.get(0).objectId());
        assertEquals("MachineProcessingBridge", candidates.get(0).suggestedBridge());
        assertEquals(85, candidates.get(0).estimatedImpactScore());
    }
}
