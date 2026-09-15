package org.geysermc.hydraulic.compat.adapter;

import org.geysermc.hydraulic.compat.model.CompatibilityObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * Capability adapter for Create mod kinetics (speed, stress, kinetic state translation).
 * Teaches the generic Hydraulic engine how Create exposes rotational kinetic capabilities,
 * RPM, and stress capacities without hardcoding individual machine blocks.
 */
public final class CreateKineticAdapter implements CapabilityAdapter {

    public record KineticNetworkMetrics(
        float speedRpm,
        float stressCapacity,
        float stressApplied,
        boolean overstressed
    ) {
        public boolean isOperating() {
            return Math.abs(speedRpm) > 0.001f && !overstressed;
        }
    }

    @Override
    public @NotNull String id() {
        return "adapter.create.kinetic";
    }

    @Override
    public @NotNull Set<AdapterFeature> features() {
        return Set.of(
            AdapterFeature.BLOCK_PLACEMENT,
            AdapterFeature.BLOCK_ITEM_TEXTURE_FALLBACK,
            AdapterFeature.BLOCK_CREATIVE_EXPOSURE
        );
    }

    @Override
    public boolean supports(@NotNull CompatibilityObject compatibilityObject, @Nullable Object runtimeObject, @NotNull AdapterFeature feature) {
        return compatibilityObject.javaIdentifier().startsWith("create:");
    }

    @Override
    public @NotNull String reason(@NotNull CompatibilityObject compatibilityObject, @Nullable Object runtimeObject, @NotNull AdapterFeature feature) {
        return "Create kinetic block state translation and rotational speed/stress bridging.";
    }

    @Override
    public int priority() {
        return 100;
    }

    @NotNull
    public static Map<String, String> translateKineticProperties(@NotNull KineticNetworkMetrics metrics) {
        return Map.of(
            "create:speed", String.valueOf((int) metrics.speedRpm()),
            "create:stress_capacity", String.valueOf((int) metrics.stressCapacity()),
            "create:stress_applied", String.valueOf((int) metrics.stressApplied()),
            "create:overstressed", String.valueOf(metrics.overstressed())
        );
    }
}
