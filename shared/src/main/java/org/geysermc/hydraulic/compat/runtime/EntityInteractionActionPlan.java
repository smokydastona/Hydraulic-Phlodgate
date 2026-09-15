package org.geysermc.hydraulic.compat.runtime;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

/** Validated, metadata-compiled entity action contract. */
public record EntityInteractionActionPlan(
    @NotNull Action action,
    @NotNull String hand,
    @Nullable String requiredItem
) {
    public enum Action {
        USE,
        ATTACK,
        MOUNT,
        DISMOUNT
    }

    public EntityInteractionActionPlan {
        hand = hand.toLowerCase(Locale.ROOT);
        if (!"main_hand".equals(hand) && !"off_hand".equals(hand)) {
            throw new IllegalArgumentException("Entity interaction hand must be main_hand or off_hand");
        }
        if (requiredItem != null && requiredItem.isBlank()) {
            requiredItem = null;
        }
    }

    @Nullable
    public static EntityInteractionActionPlan fromFacts(@NotNull Map<String, String> facts) {
        String rawAction = facts.get("interaction.entity.action");
        if (rawAction == null || rawAction.isBlank()) {
            return null;
        }

        Action action;
        try {
            action = Action.valueOf(rawAction.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }

        String hand = facts.getOrDefault("interaction.entity.hand", "main_hand");
        String item = facts.get("interaction.entity.item");
        try {
            return new EntityInteractionActionPlan(action, hand, item);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}