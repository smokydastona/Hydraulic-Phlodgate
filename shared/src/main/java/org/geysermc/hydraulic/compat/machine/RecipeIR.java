package org.geysermc.hydraulic.compat.machine;

import org.geysermc.hydraulic.compat.model.Confidence;
import org.geysermc.hydraulic.compat.runtime.TransferBridgeFactory;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Portable recipe contract consumed by generic machine runtimes. It contains only
 * semantics Hydraulic can execute without consulting the original mod recipe object.
 */
public record RecipeIR(
    @NotNull String recipeId,
    @NotNull String recipeType,
    @NotNull List<TransferBridgeFactory.ItemStackView> itemInputs,
    @NotNull List<String> tagInputs,
    @NotNull List<TransferBridgeFactory.FluidStackView> fluidInputs,
    int energyInputPerTick,
    @NotNull List<TransferBridgeFactory.ItemStackView> itemOutputs,
    @NotNull List<TransferBridgeFactory.FluidStackView> fluidOutputs,
    @NotNull List<ChanceOutput> chanceOutputs,
    int duration,
    @NotNull List<String> conditions,
    @NotNull List<TransferBridgeFactory.ItemStackView> catalysts,
    int energyGenerated,
    @NotNull String serializer,
    @NotNull Source source,
    @NotNull Confidence confidence
) {
    public RecipeIR {
        if (recipeId.isBlank() || recipeType.isBlank() || serializer.isBlank()) {
            throw new IllegalArgumentException("Recipe identity, type, and serializer must not be blank");
        }
        if (duration <= 0 || energyInputPerTick < 0 || energyGenerated < 0) {
            throw new IllegalArgumentException("Recipe duration and energy values are invalid");
        }
        itemInputs = List.copyOf(itemInputs);
        tagInputs = List.copyOf(tagInputs);
        fluidInputs = List.copyOf(fluidInputs);
        itemOutputs = List.copyOf(itemOutputs);
        fluidOutputs = List.copyOf(fluidOutputs);
        chanceOutputs = List.copyOf(chanceOutputs);
        conditions = List.copyOf(conditions);
        catalysts = List.copyOf(catalysts);
    }

    @NotNull
    public UniversalMachineRuntime.UniversalRecipe executableRecipe() {
        if (!tagInputs.isEmpty() || !chanceOutputs.isEmpty() || !conditions.isEmpty()) {
            throw new IllegalStateException("RecipeIR contains semantics unsupported by the generic machine executor");
        }
        return new UniversalMachineRuntime.UniversalRecipe(
            recipeId, itemInputs, fluidInputs, energyInputPerTick, duration,
            itemOutputs, fluidOutputs, energyGenerated, catalysts, conditions
        );
    }

    public enum Source {
        RESOURCE_JSON,
        RUNTIME_CODEC
    }

    public record ChanceOutput(@NotNull TransferBridgeFactory.ItemStackView stack, double chance) {
        public ChanceOutput {
            if (!Double.isFinite(chance) || chance <= 0.0D || chance > 1.0D) {
                throw new IllegalArgumentException("Recipe output chance must be in (0, 1]");
            }
        }
    }
}
