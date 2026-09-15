package org.geysermc.hydraulic.compat.runtime;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

/** Compiled exact-volume fluid exchange declared for a server-authoritative block use. */
public record FluidBlockUseActionPlan(
    @NotNull Action action,
    @NotNull String inputItemId,
    @NotNull String outputItemId,
    @NotNull String fluidId,
    int tank,
    int amount,
    @Nullable String side,
    @Nullable Integer propertyId
) {
    private static final int MAX_AMOUNT = 64_000;

    public FluidBlockUseActionPlan {
        inputItemId = identifier(inputItemId, "Fluid action input item");
        outputItemId = identifier(outputItemId, "Fluid action output item");
        fluidId = identifier(fluidId, "Fluid action fluid");
        if (inputItemId.equals(outputItemId)) {
            throw new IllegalArgumentException("Fluid action input and output items must differ");
        }
        if (tank < 0) {
            throw new IllegalArgumentException("Fluid action tank must not be negative");
        }
        if (amount <= 0 || amount > MAX_AMOUNT) {
            throw new IllegalArgumentException("Fluid action amount must be between 1 and " + MAX_AMOUNT);
        }
        if (side != null && side.isBlank()) {
            side = null;
        }
        if (propertyId != null && propertyId < 0) {
            throw new IllegalArgumentException("Fluid action property id must not be negative");
        }
    }

    @Nullable
    public static FluidBlockUseActionPlan from(@NotNull Map<String, String> facts) {
        Action action = Action.parse(facts.get("interaction.fluid.action"));
        Integer tank = nonNegativeInteger(facts.get("interaction.fluid.tank"));
        Integer amount = positiveInteger(facts.get("interaction.fluid.amount"));
        Integer propertyId = optionalNonNegativeInteger(facts.get("interaction.fluid.property"));
        if (action == null || tank == null || amount == null || (facts.containsKey("interaction.fluid.property") && propertyId == null)) {
            return null;
        }
        try {
            return new FluidBlockUseActionPlan(
                action,
                facts.get("interaction.fluid.input_item"),
                facts.get("interaction.fluid.output_item"),
                facts.get("interaction.fluid.id"),
                tank,
                amount,
                facts.get("interaction.fluid.side"),
                propertyId
            );
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @NotNull
    private static String identifier(@Nullable String value, @NotNull String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        try {
            return Identifier.parse(value.trim()).toString();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(label + " must be a valid identifier", exception);
        }
    }

    @Nullable
    private static Integer nonNegativeInteger(@Nullable String value) {
        Integer parsed = integer(value);
        return parsed == null || parsed < 0 ? null : parsed;
    }

    @Nullable
    private static Integer positiveInteger(@Nullable String value) {
        Integer parsed = integer(value);
        return parsed == null || parsed <= 0 || parsed > MAX_AMOUNT ? null : parsed;
    }

    @Nullable
    private static Integer optionalNonNegativeInteger(@Nullable String value) {
        return value == null ? null : nonNegativeInteger(value);
    }

    @Nullable
    private static Integer integer(@Nullable String value) {
        try {
            return value == null || value.isBlank() ? null : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public enum Action {
        DRAIN_HELD_CONTAINER,
        FILL_HELD_CONTAINER;

        @Nullable
        private static Action parse(@Nullable String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }
}