package org.geysermc.hydraulic.compat.runtime;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

/** Compiled bounded energy transfer declared for a server-authoritative block use. */
public record EnergyBlockUseActionPlan(
    @NotNull Action action,
    int amount,
    @Nullable String side,
    @Nullable Integer propertyId
) {
    private static final int MAX_AMOUNT = 10_000_000;

    public EnergyBlockUseActionPlan {
        if (amount <= 0 || amount > MAX_AMOUNT) {
            throw new IllegalArgumentException("Energy action amount must be between 1 and " + MAX_AMOUNT);
        }
        if (side != null && side.isBlank()) {
            side = null;
        }
        if (propertyId != null && propertyId < 0) {
            throw new IllegalArgumentException("Energy action property id must not be negative");
        }
    }

    @Nullable
    public static EnergyBlockUseActionPlan from(@NotNull Map<String, String> facts) {
        Action action = Action.parse(facts.get("interaction.energy.action"));
        Integer amount = positiveInteger(facts.get("interaction.energy.amount"));
        Integer propertyId = optionalNonNegativeInteger(facts.get("interaction.energy.property"));
        if (action == null || amount == null || (facts.containsKey("interaction.energy.property") && propertyId == null)) {
            return null;
        }
        try {
            return new EnergyBlockUseActionPlan(action, amount, facts.get("interaction.energy.side"), propertyId);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Nullable
    private static Integer positiveInteger(@Nullable String value) {
        try {
            int parsed = value == null || value.isBlank() ? -1 : Integer.parseInt(value);
            return parsed > 0 && parsed <= MAX_AMOUNT ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Nullable
    private static Integer optionalNonNegativeInteger(@Nullable String value) {
        if (value == null) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed >= 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public enum Action {
        RECEIVE,
        EXTRACT;

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