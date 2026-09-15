package org.geysermc.hydraulic.compat.analysis;

import org.geysermc.hydraulic.compat.mapping.ContentPatch;
import org.geysermc.hydraulic.compat.runtime.RuntimeBridgeKind;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts machine, container, and transfer behavior facts from metadata patches.
 * Facts compile into inventory facts and typed runtime bridge requirements.
 */
public final class BehaviorFactExtractor {
    private BehaviorFactExtractor() {
    }

    @NotNull
    public static Map<String, String> extractFacts(@NotNull List<ContentPatch> patches) {
        Map<String, String> facts = new LinkedHashMap<>();
        for (ContentPatch patch : patches) {
            mergeFacts(facts, patch);
        }
        return Map.copyOf(facts);
    }

    @NotNull
    public static List<String> runtimeRequirements(@NotNull Map<String, String> facts) {
        List<String> requirements = new ArrayList<>();
        if (booleanFact(facts, "has_processing")) {
            requirements.add(RuntimeBridgeKind.MACHINE_BEHAVIOR.requirementId());
        }
        if (booleanFact(facts, "has_inventory")) {
            requirements.add(RuntimeBridgeKind.MACHINE_INVENTORY.requirementId());
        }
        if (booleanFact(facts, "can_insert") || booleanFact(facts, "can_extract")) {
            requirements.add(RuntimeBridgeKind.ITEM_TRANSFER.requirementId());
        }
        if (booleanFact(facts, "can_insert_fluid") || booleanFact(facts, "can_extract_fluid")) {
            requirements.add(RuntimeBridgeKind.FLUID_TRANSFER.requirementId());
        }
        if (booleanFact(facts, "can_receive_energy") || booleanFact(facts, "can_provide_energy")) {
            requirements.add(RuntimeBridgeKind.ENERGY_TRANSFER.requirementId());
        }
        if (booleanFact(facts, "sided_insert")
            || booleanFact(facts, "sided_extract")
            || !facts.getOrDefault("filtering", "").isBlank()) {
            requirements.add(RuntimeBridgeKind.AUTOMATION_ACCESS.requirementId());
        }
        return List.copyOf(requirements);
    }

    private static void mergeFacts(@NotNull Map<String, String> facts, @NotNull ContentPatch patch) {
        putIfPresent(facts, "has_processing", patch.operation("machine.processing.enabled"));
        putIfPresent(facts, "processing_type", patch.operation("machine.processing.type"));
        putIfPresent(facts, "redstone_control", patch.operation("machine.processing.redstone_control"));

        putIfPresent(facts, "has_inventory", patch.operation("machine.inventory.enabled"));
        putIfPresent(facts, "inventory_layout", patch.operation("machine.inventory.layout"));
        putIfPresent(facts, "slot_semantics", patch.operation("machine.inventory.slot_semantics"));
        putIfPresent(facts, "inventory_type", patch.operation("machine.inventory.type"));

        putIfPresent(facts, "can_insert", patch.operation("transfer.item.can_insert"));
        putIfPresent(facts, "can_extract", patch.operation("transfer.item.can_extract"));
        putIfPresent(facts, "can_insert_fluid", patch.operation("transfer.fluid.can_insert"));
        putIfPresent(facts, "can_extract_fluid", patch.operation("transfer.fluid.can_extract"));
        putIfPresent(facts, "tank_type", patch.operation("transfer.fluid.tank_type"));
        putIfPresent(facts, "can_receive_energy", patch.operation("transfer.energy.can_receive"));
        putIfPresent(facts, "can_provide_energy", patch.operation("transfer.energy.can_provide"));
        putIfPresent(facts, "energy_type", patch.operation("transfer.energy.type"));
        putIfPresent(facts, "sided_insert", patch.operation("machine.automation.sided_insert"));
        putIfPresent(facts, "sided_extract", patch.operation("machine.automation.sided_extract"));
        putIfPresent(facts, "filtering", patch.operation("machine.automation.filtering"));

        putIfPresent(facts, "machine.input_slot", patch.operation("machine.inventory.input_slot"));
        putIfPresent(facts, "machine.output_slot", patch.operation("machine.inventory.output_slot"));
        putIfPresent(facts, "interaction.block_use.action", patch.operation("interaction.block_use.action"));
        putIfPresent(facts, "interaction.block_use.slot", patch.operation("interaction.block_use.slot"));
        putIfPresent(facts, "interaction.block_use.count", patch.operation("interaction.block_use.count"));
        putIfPresent(facts, "interaction.block_use.side", patch.operation("interaction.block_use.side"));
        putIfPresent(facts, "interaction.block_use.extract_slot", patch.operation("interaction.block_use.extract_slot"));
        putIfPresent(facts, "interaction.block_use.extract_item", patch.operation("interaction.block_use.extract_item"));
        putIfPresent(facts, "interaction.block_use.extract_count", patch.operation("interaction.block_use.extract_count"));
        putIfPresent(facts, "interaction.block_use.extract_side", patch.operation("interaction.block_use.extract_side"));
        putIfPresent(facts, "interaction.fluid.action", patch.operation("interaction.fluid.action"));
        putIfPresent(facts, "interaction.fluid.input_item", patch.operation("interaction.fluid.input_item"));
        putIfPresent(facts, "interaction.fluid.output_item", patch.operation("interaction.fluid.output_item"));
        putIfPresent(facts, "interaction.fluid.id", patch.operation("interaction.fluid.id"));
        putIfPresent(facts, "interaction.fluid.tank", patch.operation("interaction.fluid.tank"));
        putIfPresent(facts, "interaction.fluid.amount", patch.operation("interaction.fluid.amount"));
        putIfPresent(facts, "interaction.fluid.side", patch.operation("interaction.fluid.side"));
        putIfPresent(facts, "interaction.fluid.property", patch.operation("interaction.fluid.property"));
        for (Map.Entry<String, String> operation : patch.operations().entrySet()) {
            if (operation.getKey().startsWith("machine.processing.recipe.")) {
                putIfPresent(facts, operation.getKey(), operation.getValue());
            }
        }
    }

    private static void putIfPresent(@NotNull Map<String, String> facts, @NotNull String key, @NotNull String value) {
        if (value != null && !value.isBlank()) {
            facts.put(key, value);
        }
    }

    private static boolean booleanFact(@NotNull Map<String, String> facts, @NotNull String key) {
        return Boolean.parseBoolean(facts.getOrDefault(key, "false"));
    }
}
