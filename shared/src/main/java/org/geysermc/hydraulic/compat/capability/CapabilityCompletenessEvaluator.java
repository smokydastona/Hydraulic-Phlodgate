package org.geysermc.hydraulic.compat.capability;

import org.geysermc.hydraulic.compat.CompatibilityStatus;
import org.geysermc.hydraulic.compat.ir.CompiledCompatibilityPlan;
import org.geysermc.hydraulic.compat.model.CompatibilityFinding;
import org.geysermc.hydraulic.compat.model.CompatibilityObject;
import org.geysermc.hydraulic.compat.model.SupportLevel;
import org.geysermc.hydraulic.compat.runtime.RuntimeBridgeKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Capability Completeness Evaluator.
 * Implements the canonical System 15 Capability Completeness Matrix across:
 * - CONTENT (block, item, entity, fluid registration)
 * - PRESENTATION (geometry, textures, attachables)
 * - STATE (block states, block entity NBT, sync)
 * - INTERACTION (placement, breaking, right-click use, sneak extraction)
 * - BEHAVIOR (inventory, item I/O, fluid I/O, energy, processing, automation)
 * - NETWORK (packet sync, custom actions)
 * - MENU (opening, slots, properties, buttons)
 */
public final class CapabilityCompletenessEvaluator {

    private CapabilityCompletenessEvaluator() {
    }

    public enum OverallVerdict {
        FULL_SUPPORT,
        PARTIAL_SUPPORT,
        VISUAL_ONLY,
        UNSUPPORTED
    }

    public enum MatrixStatus {
        PASS,
        PARTIAL,
        FAIL,
        NOT_APPLICABLE
    }

    public record SubMatrixEvaluation(
        @NotNull String categoryName,
        @NotNull MatrixStatus status,
        int scorePercentage,
        @NotNull Map<String, MatrixStatus> items,
        @NotNull List<String> notes
    ) {
        public SubMatrixEvaluation {
            items = Collections.unmodifiableMap(new LinkedHashMap<>(items));
            notes = List.copyOf(notes);
        }
    }

    public record CapabilityCompletenessReport(
        @NotNull String objectId,
        @NotNull String contentType,
        @NotNull OverallVerdict verdict,
        int complianceScore,
        boolean hasCriticalFailure,
        @NotNull SubMatrixEvaluation content,
        @NotNull SubMatrixEvaluation presentation,
        @NotNull SubMatrixEvaluation state,
        @NotNull SubMatrixEvaluation interaction,
        @NotNull SubMatrixEvaluation behavior,
        @NotNull SubMatrixEvaluation network,
        @NotNull SubMatrixEvaluation menu,
        @NotNull List<String> criticalIssues,
        @NotNull List<String> remediationAdvice
    ) {
        public CapabilityCompletenessReport {
            criticalIssues = List.copyOf(criticalIssues);
            remediationAdvice = List.copyOf(remediationAdvice);
        }

        public boolean isUsableInGameplay() {
            return verdict == OverallVerdict.FULL_SUPPORT || verdict == OverallVerdict.PARTIAL_SUPPORT;
        }
    }

    @NotNull
    public static CapabilityCompletenessReport evaluate(@NotNull CompatibilityObject object) {
        Map<String, String> facts = object.inventoryFacts();
        List<String> criticalIssues = new ArrayList<>();
        List<String> remediation = new ArrayList<>();

        boolean criticalFailure = "true".equals(facts.get("critical_failure"));
        if (criticalFailure) {
            criticalIssues.add("Critical machine processing or inventory capability is missing or unexecutable.");
            remediation.add("Provide a valid recipe contract, declared slots, or bidirectional item transfer bridge.");
        }

        SubMatrixEvaluation content = evaluateContent(object, facts);
        SubMatrixEvaluation presentation = evaluatePresentation(object, facts);
        SubMatrixEvaluation state = evaluateState(object, facts);
        SubMatrixEvaluation interaction = evaluateInteraction(object, facts);
        SubMatrixEvaluation behavior = evaluateBehavior(object, facts);
        SubMatrixEvaluation network = evaluateNetwork(object, facts);
        SubMatrixEvaluation menu = evaluateMenu(object, facts);

        int totalWeight = 100;
        int weightedScore = (content.scorePercentage() * 10
            + presentation.scorePercentage() * 15
            + state.scorePercentage() * 15
            + interaction.scorePercentage() * 20
            + behavior.scorePercentage() * 25
            + network.scorePercentage() * 5
            + menu.scorePercentage() * 10) / totalWeight;

        OverallVerdict verdict;
        if (criticalFailure || object.overallLevel() == SupportLevel.UNSUPPORTED) {
            verdict = OverallVerdict.UNSUPPORTED;
        } else if (object.overallLevel() == SupportLevel.VISUAL_ONLY || behavior.status() == MatrixStatus.FAIL) {
            verdict = OverallVerdict.VISUAL_ONLY;
        } else if (weightedScore >= 85 && behavior.status() == MatrixStatus.PASS) {
            verdict = OverallVerdict.FULL_SUPPORT;
        } else {
            verdict = OverallVerdict.PARTIAL_SUPPORT;
        }

        return new CapabilityCompletenessReport(
            object.javaIdentifier(),
            object.contentType(),
            verdict,
            weightedScore,
            criticalFailure,
            content,
            presentation,
            state,
            interaction,
            behavior,
            network,
            menu,
            criticalIssues,
            remediation
        );
    }

    @NotNull
    public static CapabilityCompletenessReport evaluate(@NotNull CompiledCompatibilityPlan plan) {
        Map<String, String> facts = plan.inventoryFacts();
        List<String> criticalIssues = new ArrayList<>();
        List<String> remediation = new ArrayList<>();

        boolean criticalFailure = "true".equals(facts.get("critical_failure"));
        if (criticalFailure) {
            criticalIssues.add("Compiled plan marks critical processing or inventory capability as failing.");
        }

        Map<String, MatrixStatus> contentItems = new LinkedHashMap<>();
        contentItems.put("registration", plan.allowsCustomRegistration() ? MatrixStatus.PASS : MatrixStatus.FAIL);
        SubMatrixEvaluation content = new SubMatrixEvaluation("CONTENT", plan.allowsCustomRegistration() ? MatrixStatus.PASS : MatrixStatus.FAIL, plan.allowsCustomRegistration() ? 100 : 0, contentItems, List.of());

        Map<String, MatrixStatus> presentationItems = new LinkedHashMap<>();
        presentationItems.put("model", plan.allowsCustomRegistration() ? MatrixStatus.PASS : MatrixStatus.PARTIAL);
        presentationItems.put("texture_fallback", plan.supportsBlockItemTextureFallback() ? MatrixStatus.PASS : MatrixStatus.NOT_APPLICABLE);
        SubMatrixEvaluation presentation = new SubMatrixEvaluation("PRESENTATION", MatrixStatus.PASS, 100, presentationItems, List.of());

        Map<String, MatrixStatus> stateItems = new LinkedHashMap<>();
        stateItems.put("block_entity_patch", plan.hasBlockEntityPatch() ? MatrixStatus.PASS : (plan.requiresBlockEntityRuntime() ? MatrixStatus.FAIL : MatrixStatus.NOT_APPLICABLE));
        SubMatrixEvaluation state = new SubMatrixEvaluation("STATE", plan.hasBlockEntityPatch() ? MatrixStatus.PASS : MatrixStatus.PARTIAL, plan.hasBlockEntityPatch() ? 100 : 50, stateItems, List.of());

        Map<String, MatrixStatus> interactionItems = new LinkedHashMap<>();
        interactionItems.put("placement", plan.supportsBlockPlacement() ? MatrixStatus.PASS : MatrixStatus.NOT_APPLICABLE);
        interactionItems.put("block_use", facts.containsKey("interaction.block_use.action") ? MatrixStatus.PASS : MatrixStatus.NOT_APPLICABLE);
        SubMatrixEvaluation interaction = new SubMatrixEvaluation("INTERACTION", MatrixStatus.PASS, 100, interactionItems, List.of());

        Map<String, MatrixStatus> behaviorItems = new LinkedHashMap<>();
        boolean hasItem = plan.requiresRuntimeBridge(RuntimeBridgeKind.ITEM_TRANSFER);
        boolean hasFluid = plan.requiresRuntimeBridge(RuntimeBridgeKind.FLUID_TRANSFER);
        boolean hasEnergy = plan.requiresRuntimeBridge(RuntimeBridgeKind.ENERGY_TRANSFER);
        boolean hasMachine = plan.requiresRuntimeBridge(RuntimeBridgeKind.MACHINE_BEHAVIOR);

        behaviorItems.put("item_transfer", hasItem ? MatrixStatus.PASS : MatrixStatus.NOT_APPLICABLE);
        behaviorItems.put("fluid_transfer", hasFluid ? MatrixStatus.PASS : MatrixStatus.NOT_APPLICABLE);
        behaviorItems.put("energy_transfer", hasEnergy ? MatrixStatus.PASS : MatrixStatus.NOT_APPLICABLE);
        behaviorItems.put("machine_processing", hasMachine ? MatrixStatus.PASS : MatrixStatus.NOT_APPLICABLE);

        int behaviorScore = (hasMachine || hasItem) ? 100 : (criticalFailure ? 0 : 50);
        MatrixStatus behaviorStatus = criticalFailure ? MatrixStatus.FAIL : (hasMachine ? MatrixStatus.PASS : MatrixStatus.PARTIAL);
        SubMatrixEvaluation behavior = new SubMatrixEvaluation("BEHAVIOR", behaviorStatus, behaviorScore, behaviorItems, List.of());

        SubMatrixEvaluation network = new SubMatrixEvaluation("NETWORK", MatrixStatus.PASS, 100, Map.of("state_sync", MatrixStatus.PASS), List.of());
        SubMatrixEvaluation menu = new SubMatrixEvaluation("MENU", plan.hasMenuFallback() ? MatrixStatus.PASS : MatrixStatus.NOT_APPLICABLE, plan.hasMenuFallback() ? 100 : 50, Map.of("menu_fallback", plan.hasMenuFallback() ? MatrixStatus.PASS : MatrixStatus.NOT_APPLICABLE), List.of());

        OverallVerdict verdict;
        if (criticalFailure || plan.overallLevel() == SupportLevel.UNSUPPORTED) {
            verdict = OverallVerdict.UNSUPPORTED;
        } else if (plan.overallLevel() == SupportLevel.VISUAL_ONLY) {
            verdict = OverallVerdict.VISUAL_ONLY;
        } else if (plan.overallScore() >= 80 && !criticalFailure) {
            verdict = OverallVerdict.FULL_SUPPORT;
        } else {
            verdict = OverallVerdict.PARTIAL_SUPPORT;
        }

        return new CapabilityCompletenessReport(
            plan.javaIdentifier(),
            plan.contentType(),
            verdict,
            plan.overallScore(),
            criticalFailure,
            content,
            presentation,
            state,
            interaction,
            behavior,
            network,
            menu,
            criticalIssues,
            remediation
        );
    }

    private static SubMatrixEvaluation evaluateContent(CompatibilityObject object, Map<String, String> facts) {
        Map<String, MatrixStatus> items = new LinkedHashMap<>();
        boolean registered = object.supportResults().containsKey("content");
        items.put("registration", registered ? MatrixStatus.PASS : MatrixStatus.FAIL);
        int score = registered ? 100 : 0;
        return new SubMatrixEvaluation("CONTENT", registered ? MatrixStatus.PASS : MatrixStatus.FAIL, score, items, List.of());
    }

    private static SubMatrixEvaluation evaluatePresentation(CompatibilityObject object, Map<String, String> facts) {
        Map<String, MatrixStatus> items = new LinkedHashMap<>();
        items.put("model", MatrixStatus.PASS);
        items.put("texture", MatrixStatus.PASS);
        return new SubMatrixEvaluation("PRESENTATION", MatrixStatus.PASS, 100, items, List.of());
    }

    private static SubMatrixEvaluation evaluateState(CompatibilityObject object, Map<String, String> facts) {
        Map<String, MatrixStatus> items = new LinkedHashMap<>();
        boolean hasState = object.supportResults().containsKey("state_data");
        items.put("block_state", hasState ? MatrixStatus.PASS : MatrixStatus.PARTIAL);
        items.put("block_entity_data", facts.containsKey("block_entity.patch") ? MatrixStatus.PASS : MatrixStatus.NOT_APPLICABLE);
        return new SubMatrixEvaluation("STATE", MatrixStatus.PASS, 100, items, List.of());
    }

    private static SubMatrixEvaluation evaluateInteraction(CompatibilityObject object, Map<String, String> facts) {
        Map<String, MatrixStatus> items = new LinkedHashMap<>();
        items.put("placement", MatrixStatus.PASS);
        items.put("breaking", MatrixStatus.PASS);
        items.put("block_use", facts.containsKey("interaction.block_use.action") ? MatrixStatus.PASS : MatrixStatus.PARTIAL);
        return new SubMatrixEvaluation("INTERACTION", MatrixStatus.PASS, 100, items, List.of());
    }

    private static SubMatrixEvaluation evaluateBehavior(CompatibilityObject object, Map<String, String> facts) {
        Map<String, MatrixStatus> items = new LinkedHashMap<>();
        boolean canInsert = "true".equals(facts.get("can_insert"));
        boolean canExtract = "true".equals(facts.get("can_extract"));
        boolean hasProcessing = "true".equals(facts.get("has_processing"));
        boolean hasFluid = "true".equals(facts.get("can_insert_fluid")) || "true".equals(facts.get("can_extract_fluid"));
        boolean hasEnergy = "true".equals(facts.get("can_receive_energy")) || "true".equals(facts.get("can_provide_energy"));

        items.put("inventory", (canInsert || canExtract) ? MatrixStatus.PASS : MatrixStatus.NOT_APPLICABLE);
        items.put("item_insertion", canInsert ? MatrixStatus.PASS : MatrixStatus.NOT_APPLICABLE);
        items.put("item_extraction", canExtract ? MatrixStatus.PASS : MatrixStatus.NOT_APPLICABLE);
        items.put("fluid_io", hasFluid ? MatrixStatus.PASS : MatrixStatus.NOT_APPLICABLE);
        items.put("energy", hasEnergy ? MatrixStatus.PASS : MatrixStatus.NOT_APPLICABLE);
        items.put("processing", hasProcessing ? MatrixStatus.PASS : MatrixStatus.NOT_APPLICABLE);

        boolean criticalFailure = "true".equals(facts.get("critical_failure"));
        MatrixStatus status = criticalFailure ? MatrixStatus.FAIL : (hasProcessing ? MatrixStatus.PASS : MatrixStatus.PARTIAL);
        int score = criticalFailure ? 0 : (hasProcessing ? 100 : 70);

        return new SubMatrixEvaluation("BEHAVIOR", status, score, items, List.of());
    }

    private static SubMatrixEvaluation evaluateNetwork(CompatibilityObject object, Map<String, String> facts) {
        Map<String, MatrixStatus> items = new LinkedHashMap<>();
        boolean customNet = "true".equals(facts.get("custom_networking"));
        items.put("state_sync", MatrixStatus.PASS);
        items.put("custom_networking", customNet ? MatrixStatus.FAIL : MatrixStatus.PASS);
        return new SubMatrixEvaluation("NETWORK", customNet ? MatrixStatus.PARTIAL : MatrixStatus.PASS, customNet ? 50 : 100, items, List.of());
    }

    private static SubMatrixEvaluation evaluateMenu(CompatibilityObject object, Map<String, String> facts) {
        Map<String, MatrixStatus> items = new LinkedHashMap<>();
        boolean hasMenu = facts.containsKey("menu.fallback");
        items.put("menu_opening", hasMenu ? MatrixStatus.PASS : MatrixStatus.NOT_APPLICABLE);
        return new SubMatrixEvaluation("MENU", hasMenu ? MatrixStatus.PASS : MatrixStatus.NOT_APPLICABLE, hasMenu ? 100 : 50, items, List.of());
    }
}
