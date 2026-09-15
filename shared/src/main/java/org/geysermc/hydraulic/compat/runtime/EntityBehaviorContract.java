package org.geysermc.hydraulic.compat.runtime;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

/**
 * Validates the small, portable entity behavior vocabulary that can be reported across loaders.
 * This is evidence and planning data; it is not a claim that arbitrary Java AI bytecode is
 * executable on Bedrock.
 */
public record EntityBehaviorContract(
    @NotNull Behavior behavior,
    @Nullable String target,
    int range,
    boolean executable
) {
    public enum Behavior {
        WANDER,
        FOLLOW,
        ATTACK,
        FLEE,
        GUARD,
        LOOK_AT,
        PICKUP,
        WORK,
        BREED
    }

    public EntityBehaviorContract {
        if (range < 0 || range > 128) {
            throw new IllegalArgumentException("Entity behavior range must be between 0 and 128");
        }
    }

    @Nullable
    public static EntityBehaviorContract fromFacts(@NotNull Map<String, String> facts) {
        String raw = facts.get("entity.ai.behavior");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Behavior behavior;
        try {
            behavior = Behavior.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
        int range = 0;
        String rawRange = facts.get("entity.ai.range");
        if (rawRange != null && !rawRange.isBlank()) {
            try {
                range = Integer.parseInt(rawRange);
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        try {
            return new EntityBehaviorContract(behavior, facts.get("entity.ai.target"), range, false);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}