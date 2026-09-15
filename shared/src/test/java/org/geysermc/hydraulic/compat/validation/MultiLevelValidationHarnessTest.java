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

    @Test
    @DisplayName("Tier-1 Mod Validation Suite evaluates Farmer's Delight, Create, Citadel, and Lootr")
    void tier1ModValidationSuitePasses() {
        CompiledCompatibilityPlan farmersDelightPlan = new CompiledCompatibilityPlan(
            "farmersdelight",
            "block",
            "farmersdelight:cooking_pot",
            "farmersdelight:cooking_pot",
            SupportLevel.ADAPTED,
            CompatibilityStatus.COMPLETE,
            95,
            new Confidence(0.95, "test"),
            List.of(),
            List.of("item_transfer_bridge", "menu_container_bridge"),
            List.of(RuntimeBridgeKind.ITEM_TRANSFER, RuntimeBridgeKind.MENU_CONTAINER),
            Map.of("has_inventory", "true", "can_insert", "true", "can_extract", "true"),
            true, null, true, null, true, true, false, false, false, null, null,
            List.of(), List.of(), List.of(), null, false, false,
            SupportLevel.ADAPTED, "machine", List.of()
        );

        CompiledCompatibilityPlan createPlan = new CompiledCompatibilityPlan(
            "create",
            "block",
            "create:mechanical_press",
            "create:mechanical_press",
            SupportLevel.ADAPTED,
            CompatibilityStatus.COMPLETE,
            90,
            new Confidence(0.9, "test"),
            List.of(),
            List.of("item_transfer_bridge", "block_placement_bridge"),
            List.of(RuntimeBridgeKind.ITEM_TRANSFER, RuntimeBridgeKind.BLOCK_PLACEMENT),
            Map.of("has_kinetics", "true", "interaction.block_use.action", "insert_held_item"),
            true, null, true, null, true, true, false, false, false, null, null,
            List.of(), List.of(), List.of(), null, false, false,
            SupportLevel.ADAPTED, "machine", List.of()
        );

        CompiledCompatibilityPlan citadelPlan = new CompiledCompatibilityPlan(
            "citadel",
            "item",
            "citadel:citadel_book",
            "citadel:citadel_book",
            SupportLevel.ADAPTED,
            CompatibilityStatus.COMPLETE,
            85,
            new Confidence(0.85, "test"),
            List.of(),
            List.of("custom_item_registration"),
            List.of(RuntimeBridgeKind.ITEM_BEHAVIOR),
            Map.of(),
            false, null, false, null, true, false, false, false, false, null, null,
            List.of(), List.of(), List.of(), null, false, false,
            null, null, List.of()
        );

        CompiledCompatibilityPlan lootrPlan = new CompiledCompatibilityPlan(
            "lootr",
            "block",
            "lootr:lootr_chest",
            "lootr:lootr_chest",
            SupportLevel.ADAPTED,
            CompatibilityStatus.COMPLETE,
            90,
            new Confidence(0.9, "test"),
            List.of(),
            List.of("menu_container_bridge"),
            List.of(RuntimeBridgeKind.MENU_CONTAINER),
            Map.of("has_inventory", "true"),
            true, null, true, null, true, true, false, false, false, null, null,
            List.of(), List.of(), List.of(), null, false, false,
            SupportLevel.ADAPTED, "container", List.of()
        );

        MultiLevelValidationHarness.ValidationRunner runner = new MultiLevelValidationHarness.ValidationRunner();
        MultiLevelValidationHarness.ModpackCorpusSummary summary = runner.evaluateModpackCorpus(
            "Tier-1 Mod Corpus",
            List.of(farmersDelightPlan, createPlan, citadelPlan, lootrPlan)
        );

        assertNotNull(summary);
        assertEquals(4, summary.totalMods());
        assertEquals(4, summary.totalObjects());
        assertEquals(4, summary.passingObjects());
        assertEquals(0, summary.failingObjects());
        assertEquals(100, summary.overallCompliancePercentage());
    }
}
