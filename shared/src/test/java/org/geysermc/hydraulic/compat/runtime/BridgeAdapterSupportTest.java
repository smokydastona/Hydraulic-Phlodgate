package org.geysermc.hydraulic.compat.runtime;

import net.minecraft.resources.Identifier;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.geysermc.hydraulic.compat.CompatibilityStatus;
import org.geysermc.hydraulic.compat.MappingOwnership;
import org.geysermc.hydraulic.compat.adapter.AdapterBinding;
import org.geysermc.hydraulic.compat.adapter.AdapterFeature;
import org.geysermc.hydraulic.compat.ir.CompiledCompatibilityPlan;
import org.geysermc.hydraulic.compat.mapping.ContentPatch;
import org.geysermc.hydraulic.compat.model.Confidence;
import org.geysermc.hydraulic.compat.model.SupportLevel;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BridgeAdapterSupportTest {
    @Test
    void menuFallbackRequiresExplicitAdapterBinding() {
        assertTrue(BridgeAdapterSupport.supportsMenuFallback(menuPlan(true, RuntimeBridgeKind.MENU_CONTAINER)));
        assertFalse(BridgeAdapterSupport.supportsMenuFallback(menuPlan(false, RuntimeBridgeKind.MENU_CONTAINER)));
    }

    @Test
    void menuFallbackRequiresContainerBridgeKind() {
        assertFalse(BridgeAdapterSupport.supportsMenuFallback(menuPlan(true, RuntimeBridgeKind.MENU_BEHAVIOR)));
    }

    @Test
    void blockEntityPatchRequiresExplicitAdapterBinding() {
        assertTrue(BridgeAdapterSupport.supportsBlockEntityPatch(blockEntityPlan(true, RuntimeBridgeKind.BLOCK_ENTITY_DATA)));
        assertFalse(BridgeAdapterSupport.supportsBlockEntityPatch(blockEntityPlan(false, RuntimeBridgeKind.BLOCK_ENTITY_DATA)));
    }

    @Test
    void blockEntityPatchRequiresDataBridgeKind() {
        assertFalse(BridgeAdapterSupport.supportsBlockEntityPatch(blockEntityPlan(true, RuntimeBridgeKind.BLOCK_ENTITY_BEHAVIOR)));
    }

    @Test
    void entityInteractionPromptRequiresExplicitAdapterBinding() {
        assertTrue(BridgeAdapterSupport.supportsEntityInteractionPrompt(entityPlan(true, "Open Barrel Cube")));
        assertFalse(BridgeAdapterSupport.supportsEntityInteractionPrompt(entityPlan(false, "Open Barrel Cube")));
        assertFalse(BridgeAdapterSupport.supportsEntityInteractionPrompt(entityPlan(true, null)));
    }

    @Test
    void machineBehaviorRequiresMachineBridgeKind() {
        assertTrue(BridgeAdapterSupport.supportsMachineBehavior(machinePlan(RuntimeBridgeKind.MACHINE_BEHAVIOR)));
        assertFalse(BridgeAdapterSupport.supportsMachineBehavior(machinePlan(RuntimeBridgeKind.MACHINE_INVENTORY)));
        assertFalse(BridgeAdapterSupport.supportsMachineBehavior(machinePlanWithoutFacts(RuntimeBridgeKind.MACHINE_BEHAVIOR)));
    }

    @Test
    void transferBridgesRequireTypedRuntimeKinds() {
        assertTrue(BridgeAdapterSupport.supportsItemTransfer(transferPlan(RuntimeBridgeKind.ITEM_TRANSFER)));
        assertTrue(BridgeAdapterSupport.supportsFluidTransfer(transferPlan(RuntimeBridgeKind.FLUID_TRANSFER)));
        assertTrue(BridgeAdapterSupport.supportsEnergyTransfer(transferPlan(RuntimeBridgeKind.ENERGY_TRANSFER)));
        assertFalse(BridgeAdapterSupport.supportsItemTransfer(transferPlan(RuntimeBridgeKind.FLUID_TRANSFER)));
        assertFalse(BridgeAdapterSupport.supportsItemTransfer(transferPlanWithoutFacts(RuntimeBridgeKind.ITEM_TRANSFER)));
    }

    @Test
    void fluidTranslatorCanUseFactsButRuntimeRequiresConcreteOperations() {
        assertTrue(BridgeAdapterSupport.supportsFluidTranslator(fluidPlan(true, false)));
        assertFalse(BridgeAdapterSupport.supportsFluidRuntime(fluidPlan(false, true)));
        assertFalse(BridgeAdapterSupport.supportsFluidRuntime(fluidPlan(false, false)));
    }

    private static CompiledCompatibilityPlan menuPlan(boolean includeAdapter, RuntimeBridgeKind runtimeBridgeKind) {
        return new CompiledCompatibilityPlan(
            "testmod",
            "menu",
            Identifier.fromNamespaceAndPath("example", "test_menu").toString(),
            Identifier.fromNamespaceAndPath("example", "bedrock_menu").toString(),
            SupportLevel.ADAPTED,
            CompatibilityStatus.PARTIAL,
            75,
            new Confidence(0.8D, "test"),
            includeAdapter ? List.of(new AdapterBinding("menu.fallback_translator", AdapterFeature.MENU_FALLBACK_TRANSLATION, "test")) : List.of(),
            List.of(runtimeBridgeKind.requirementId()),
            List.of(runtimeBridgeKind),
            Map.of(),
            false,
            null,
            false,
            null,
            false,
            false,
            false,
            false,
            true,
            ContainerType.GENERIC_9X3,
            null,
            List.of(runtimeBridgeKind.requirementId()),
            List.of(),
            List.of(),
            null,
            false,
            false,
            SupportLevel.UNSUPPORTED,
            null,
            List.of()
        );
    }

    private static CompiledCompatibilityPlan blockEntityPlan(boolean includeAdapter, RuntimeBridgeKind runtimeBridgeKind) {
        return new CompiledCompatibilityPlan(
            "testmod",
            "block_entity",
            Identifier.fromNamespaceAndPath("example", "test_block_entity").toString(),
            Identifier.fromNamespaceAndPath("example", "test_block_entity").toString(),
            SupportLevel.ADAPTED,
            CompatibilityStatus.PARTIAL,
            75,
            new Confidence(0.8D, "test"),
            includeAdapter ? List.of(new AdapterBinding("block_entity.patch_translator", AdapterFeature.BLOCK_ENTITY_PATCH_TRANSLATION, "test")) : List.of(),
            List.of(runtimeBridgeKind.requirementId()),
            List.of(runtimeBridgeKind),
            Map.of(),
            false,
            null,
            false,
            null,
            false,
            false,
            false,
            false,
            false,
            null,
            null,
            List.of(),
            List.of(runtimeBridgeKind.requirementId()),
            List.of(),
            BlockEntityPatchTemplate.resolve(List.of(new ContentPatch(
                Identifier.fromNamespaceAndPath("example", "test_block_entity"),
                "block_entity",
                Map.of("bedrock.block_entity.id", "BedrockChest"),
                MappingOwnership.USER,
                "test.json",
                1,
                0
            ))),
            true,
            false,
            SupportLevel.UNSUPPORTED,
            null,
            List.of()
        );
    }

    private static CompiledCompatibilityPlan machinePlan(RuntimeBridgeKind runtimeBridgeKind) {
        return new CompiledCompatibilityPlan(
            "testmod",
            "block",
            Identifier.fromNamespaceAndPath("example", "machine").toString(),
            Identifier.fromNamespaceAndPath("example", "machine").toString(),
            SupportLevel.ADAPTED,
            CompatibilityStatus.PARTIAL,
            70,
            new Confidence(0.7D, "test"),
            List.of(),
            List.of(runtimeBridgeKind.requirementId()),
            List.of(runtimeBridgeKind),
            Map.of("has_processing", "true"),
            false,
            null,
            false,
            null,
            false,
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
            null,
            List.of()
        );
    }

    private static CompiledCompatibilityPlan machinePlanWithoutFacts(RuntimeBridgeKind runtimeBridgeKind) {
        return machinePlanWithFacts(runtimeBridgeKind, Map.of());
    }

    private static CompiledCompatibilityPlan transferPlan(RuntimeBridgeKind runtimeBridgeKind) {
        Map<String, String> facts = switch (runtimeBridgeKind) {
            case ITEM_TRANSFER -> Map.of("can_insert", "true");
            case FLUID_TRANSFER -> Map.of("can_insert_fluid", "true");
            case ENERGY_TRANSFER -> Map.of("can_receive_energy", "true");
            default -> Map.of();
        };
        return machinePlanWithFacts(runtimeBridgeKind, facts);
    }

    private static CompiledCompatibilityPlan transferPlanWithoutFacts(RuntimeBridgeKind runtimeBridgeKind) {
        return machinePlanWithFacts(runtimeBridgeKind, Map.of());
    }

    private static CompiledCompatibilityPlan machinePlanWithFacts(RuntimeBridgeKind runtimeBridgeKind, Map<String, String> facts) {
        return new CompiledCompatibilityPlan(
            "testmod",
            "block",
            Identifier.fromNamespaceAndPath("example", "machine").toString(),
            Identifier.fromNamespaceAndPath("example", "machine").toString(),
            SupportLevel.ADAPTED,
            CompatibilityStatus.PARTIAL,
            70,
            new Confidence(0.7D, "test"),
            List.of(),
            List.of(runtimeBridgeKind.requirementId()),
            List.of(runtimeBridgeKind),
            facts,
            false,
            null,
            false,
            null,
            false,
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
            null,
            List.of()
        );
    }

    private static CompiledCompatibilityPlan fluidPlan(boolean includeBucketTexture, boolean requiresRuntime) {
        Map<String, String> facts = new LinkedHashMap<>();
        if (includeBucketTexture) {
            facts.put("bucket_texture", "example:bucket");
        }
        return new CompiledCompatibilityPlan(
            "testmod",
            "fluid",
            Identifier.fromNamespaceAndPath("example", "fluid").toString(),
            Identifier.fromNamespaceAndPath("example", "fluid").toString(),
            SupportLevel.ADAPTED,
            CompatibilityStatus.PARTIAL,
            70,
            new Confidence(0.7D, "test"),
            includeBucketTexture ? List.of(new AdapterBinding("fluid.bucket_texture_fallback", AdapterFeature.FLUID_BUCKET_TEXTURE_FALLBACK, "test")) : List.of(),
            List.of(),
            List.of(),
            facts,
            false,
            null,
            false,
            null,
            false,
            false,
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
            requiresRuntime,
            SupportLevel.UNSUPPORTED,
            requiresRuntime ? "fluid_runtime" : null,
            List.of()
        );
    }

    private static CompiledCompatibilityPlan entityPlan(boolean includeAdapter, String interactionPrompt) {
        return new CompiledCompatibilityPlan(
            "testmod",
            "entity",
            Identifier.fromNamespaceAndPath("example", "test_entity").toString(),
            Identifier.fromNamespaceAndPath("example", "bedrock_entity").toString(),
            SupportLevel.ADAPTED,
            CompatibilityStatus.PARTIAL,
            75,
            new Confidence(0.8D, "test"),
            includeAdapter ? List.of(new AdapterBinding("entity.interaction_prompt", AdapterFeature.ENTITY_INTERACTION_PROMPT, "test")) : List.of(),
            List.of(),
            List.of(),
            interactionPrompt == null ? Map.of() : Map.of("interaction_prompt", interactionPrompt),
            false,
            null,
            false,
            null,
            false,
            false,
            false,
            false,
            false,
            null,
            interactionPrompt,
            List.of(),
            List.of(),
            List.of(),
            null,
            false,
            false,
            SupportLevel.UNSUPPORTED,
            null,
            List.of()
        );
    }
}