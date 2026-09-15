package org.geysermc.hydraulic.compat.runtime;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

/** Validated evidence for entity custom-network requirements. */
public record EntityNetworkContract(
    @NotNull Direction direction,
    @NotNull String channel,
    boolean executable
) {
    public enum Direction {
        CLIENTBOUND,
        SERVERBOUND,
        BIDIRECTIONAL
    }

    public EntityNetworkContract {
        if (channel.isBlank() || channel.length() > 128 || !channel.matches("[A-Za-z0-9_:.\\-/]+")) {
            throw new IllegalArgumentException("Entity network channel is invalid");
        }
    }

    @Nullable
    public static EntityNetworkContract fromFacts(@NotNull Map<String, String> facts) {
        if (!Boolean.parseBoolean(facts.getOrDefault("entity.network.required", "false"))) {
            return null;
        }
        String rawDirection = facts.getOrDefault("entity.network.direction", "bidirectional");
        String channel = facts.get("entity.network.channel");
        if (channel == null || channel.isBlank()) {
            return null;
        }
        try {
            return new EntityNetworkContract(Direction.valueOf(rawDirection.toUpperCase(Locale.ROOT)), channel, false);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}