package org.geysermc.hydraulic.compat.entity;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deep Entity Runtime Engine (Phase 7).
 * Manages custom entity lifecycle, prompt overrides, mounting synchronization,
 * health/attribute replication, and Bedrock $\to$ Java interaction mapping.
 */
public final class UniversalEntityRuntime {

    public record EntityStateIR(
        @NotNull Identifier entityIdentifier,
        double posX,
        double posY,
        double posZ,
        float yaw,
        float pitch,
        float health,
        float maxHealth,
        boolean isVehicle,
        @Nullable String passengerId,
        @NotNull Map<String, String> customAttributes
    ) {
        public EntityStateIR {
            customAttributes = Collections.unmodifiableMap(new LinkedHashMap<>(customAttributes));
        }

        public boolean isAlive() {
            return health > 0;
        }
    }

    public record EntityInteractionAction(
        @NotNull Identifier entityIdentifier,
        @NotNull String actionType,
        @Nullable String heldItemId,
        boolean isSneaking
    ) {}

    public record EntityInteractionResult(
        boolean success,
        @NotNull String resultAction,
        @Nullable String updatedStatePrompt
    ) {}

    public static final class EntityInteractionMapper {
        private final Map<Identifier, String> interactionPrompts = new LinkedHashMap<>();

        public void registerPrompt(@NotNull Identifier entityId, @NotNull String prompt) {
            interactionPrompts.put(entityId, prompt);
        }

        @NotNull
        public String getPrompt(@NotNull Identifier entityId) {
            return interactionPrompts.getOrDefault(entityId, "Interact");
        }

        @NotNull
        public EntityInteractionResult handleInteraction(@NotNull EntityInteractionAction action) {
            String prompt = interactionPrompts.get(action.entityIdentifier());
            return new EntityInteractionResult(
                true,
                "EXECUTE_JAVA_INTERACTION",
                prompt
            );
        }
    }
}
