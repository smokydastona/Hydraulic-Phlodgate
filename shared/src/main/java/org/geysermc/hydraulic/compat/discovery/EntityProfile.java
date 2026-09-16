package org.geysermc.hydraulic.compat.discovery;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * EntityProfile modeling movement, interaction, inventory, equipment, health,
 * AI category, attack behavior, mountability, and synchronization.
 */
public record EntityProfile(
    @NotNull Identifier identifier,
    @NotNull AICategory aiCategory,
    boolean hasInventory,
    boolean mountable,
    @Nullable String interactionPrompt,
    float health
) {
    public enum AICategory {
        PASSIVE,
        HOSTILE,
        NEUTRAL,
        INTERACTIVE_NPC,
        BOSS,
        DECORATIVE
    }

    @NotNull
    public static EntityProfile createDefault(@NotNull Identifier identifier, @Nullable String prompt) {
        return new EntityProfile(
            identifier,
            prompt != null ? AICategory.INTERACTIVE_NPC : AICategory.PASSIVE,
            false,
            false,
            prompt,
            20.0f
        );
    }
}
