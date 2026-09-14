package org.geysermc.hydraulic.compat.discovery;

import net.minecraft.resources.Identifier;
import org.geysermc.hydraulic.compat.runtime.TransferBridgeFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * MachineProfile holding multi-resource slot roles, recipe contracts, transfer capabilities,
 * automation rules, state properties, and evidence trails.
 */
public record MachineProfile(
    @NotNull Identifier identifier,
    @NotNull Map<String, String> slotRoles,
    @NotNull List<RecipeContract> recipes,
    boolean supportsItemTransfer,
    boolean supportsFluidTransfer,
    boolean supportsEnergyTransfer,
    boolean supportsAutomation,
    @NotNull Map<String, String> stateProperties,
    @NotNull List<SemanticDiscoveryEngine.DiscoveryEvidence> evidence
) {
    public record RecipeContract(
        @NotNull List<TransferBridgeFactory.ItemStackView> itemInputs,
        @NotNull List<TransferBridgeFactory.FluidStackView> fluidInputs,
        int energyInput,
        @NotNull List<TransferBridgeFactory.ItemStackView> itemOutputs,
        @NotNull List<TransferBridgeFactory.FluidStackView> fluidOutputs,
        int energyOutput,
        int duration
    ) {}

    @NotNull
    public static MachineProfile fromFacts(
        @NotNull Identifier identifier,
        @NotNull Map<String, String> facts,
        @NotNull List<SemanticDiscoveryEngine.DiscoveryEvidence> evidence
    ) {
        Map<String, String> slots = Map.of(
            "input_slot", facts.getOrDefault("machine.input_slot", "0"),
            "output_slot", facts.getOrDefault("machine.output_slot", "1")
        );

        boolean item = Boolean.parseBoolean(facts.getOrDefault("can_insert", "false")) || Boolean.parseBoolean(facts.getOrDefault("can_extract", "false"));
        boolean fluid = Boolean.parseBoolean(facts.getOrDefault("can_insert_fluid", "false")) || Boolean.parseBoolean(facts.getOrDefault("can_extract_fluid", "false"));
        boolean energy = Boolean.parseBoolean(facts.getOrDefault("can_receive_energy", "false")) || Boolean.parseBoolean(facts.getOrDefault("can_provide_energy", "false"));
        boolean automation = Boolean.parseBoolean(facts.getOrDefault("sided_insert", "false")) || Boolean.parseBoolean(facts.getOrDefault("sided_extract", "false"));

        List<RecipeContract> recipeContracts = new java.util.ArrayList<>();
        for (int index = 0; index < 64; index++) {
            String prefix = "machine.processing.recipe." + index + ".";
            String input = nonBlank(facts.get(prefix + "input"));
            String output = nonBlank(facts.get(prefix + "output"));
            if (input == null || output == null) {
                continue;
            }

            int inputCount = positiveInt(facts.get(prefix + "input_count"), 1);
            int outputCount = positiveInt(facts.get(prefix + "output_count"), 1);
            int duration = positiveInt(facts.get(prefix + "duration"), 40);
            int energyInput = nonNegativeInt(facts.get(prefix + "energy_input"), 0);
            int energyOutput = nonNegativeInt(facts.get(prefix + "energy_output"), 0);

            List<TransferBridgeFactory.FluidStackView> fluidInputs = fluidFacts(facts, prefix + "fluid_input");
            List<TransferBridgeFactory.FluidStackView> fluidOutputs = fluidFacts(facts, prefix + "fluid_output");
            recipeContracts.add(new RecipeContract(
                List.of(new TransferBridgeFactory.ItemStackView(input, inputCount)),
                fluidInputs,
                energyInput,
                List.of(new TransferBridgeFactory.ItemStackView(output, outputCount)),
                fluidOutputs,
                energyOutput,
                duration
            ));
        }

        return new MachineProfile(
            identifier,
            slots,
            recipeContracts,
            item,
            fluid,
            energy,
            automation,
            Map.copyOf(facts),
            List.copyOf(evidence)
        );
    }

    private static List<TransferBridgeFactory.FluidStackView> fluidFacts(Map<String, String> facts, String prefix) {
        String fluid = nonBlank(facts.get(prefix));
        if (fluid == null) {
            return List.of();
        }
        int amount = positiveInt(facts.get(prefix + "_amount"), 1000);
        return List.of(new TransferBridgeFactory.FluidStackView(fluid, amount));
    }

    private static String nonBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static int positiveInt(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value == null ? "" : value);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int nonNegativeInt(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value == null ? "" : value);
            return parsed >= 0 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
