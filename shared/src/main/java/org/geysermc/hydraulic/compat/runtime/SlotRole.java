package org.geysermc.hydraulic.compat.runtime;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public enum SlotRole {
    INPUT,
    OUTPUT,
    FUEL,
    UPGRADE,
    STORAGE,
    FLUID_INPUT,
    FLUID_OUTPUT,
    CATALYST,
    PLAYER_INVENTORY,
    UNKNOWN;

    @NotNull
    public static SlotRole parse(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return UNKNOWN;
        }
    }
}
