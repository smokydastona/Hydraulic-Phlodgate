package org.geysermc.hydraulic.compat.discovery;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * MenuProfile modeling container slots, slot roles (input/output/fuel/upgrade/fluid),
 * progress properties, buttons, and server authority.
 */
public record MenuProfile(
    @NotNull Identifier identifier,
    int containerSize,
    @NotNull Map<Integer, SlotRole> slotRoles,
    @NotNull Map<String, Integer> progressProperties,
    boolean serverAuthoritative
) {
    public enum SlotRole {
        INPUT,
        OUTPUT,
        FUEL,
        UPGRADE,
        FLUID_INPUT,
        FLUID_OUTPUT,
        GENERIC
    }

    @NotNull
    public static MenuProfile createDefault(@NotNull Identifier identifier, int containerSize) {
        Map<Integer, SlotRole> roles = Map.of(
            0, SlotRole.INPUT,
            1, SlotRole.OUTPUT
        );
        return new MenuProfile(identifier, containerSize, roles, Map.of("progress", 0), true);
    }
}
