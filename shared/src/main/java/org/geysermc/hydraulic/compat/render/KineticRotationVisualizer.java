package org.geysermc.hydraulic.compat.render;

import org.cloudburstmc.math.vector.Vector3f;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compiles Create-style rotational kinetic network states into Bedrock entity animation transforms and bone rotations.
 * Maps rotational speed (RPM), stress capacity, axis of rotation, and gear ratios to Bedrock client bone keyframes.
 */
public final class KineticRotationVisualizer {

    public enum RotationAxis {
        X,
        Y,
        Z;

        @NotNull
        public Vector3f getUnitVector() {
            return switch (this) {
                case X -> Vector3f.from(1.0f, 0.0f, 0.0f);
                case Y -> Vector3f.from(0.0f, 1.0f, 0.0f);
                case Z -> Vector3f.from(0.0f, 0.0f, 1.0f);
            };
        }
    }

    public enum KineticNodeType {
        SHAFT,
        COGWHEEL,
        LARGE_COGWHEEL,
        GEARBOX,
        CRUSHING_WHEEL,
        WATER_WHEEL,
        WINDMILL_BEARING,
        MECHANICAL_PRESS,
        MECHANICAL_MIXER,
        FLYWHEEL,
        CUSTOM
    }

    public record KineticNetworkState(
        @NotNull String networkId,
        float speedRpm,
        float stressLoad,
        float stressCapacity,
        boolean isOverstressed
    ) {
        public boolean isSpinning() {
            return !isOverstressed && Math.abs(speedRpm) > 0.001f;
        }

        public float normalizedStressRatio() {
            if (stressCapacity <= 0.0f) {
                return 1.0f;
            }
            return Math.min(1.0f, Math.max(0.0f, stressLoad / stressCapacity));
        }
    }

    public record KineticNode(
        @NotNull String blockPosKey,
        @NotNull String modId,
        @NotNull KineticNodeType nodeType,
        @NotNull RotationAxis axis,
        float localGearRatio,
        boolean reversed,
        @NotNull String targetBoneName
    ) {
        public KineticNode {
            Objects.requireNonNull(blockPosKey, "blockPosKey");
            Objects.requireNonNull(modId, "modId");
            Objects.requireNonNull(nodeType, "nodeType");
            Objects.requireNonNull(axis, "axis");
            Objects.requireNonNull(targetBoneName, "targetBoneName");
        }

        public float computeEffectiveRpm(float networkRpm) {
            float direction = reversed ? -1.0f : 1.0f;
            return networkRpm * localGearRatio * direction;
        }
    }

    public record BedrockBoneTransform(
        @NotNull String boneName,
        @NotNull Vector3f rotationDegrees,
        @NotNull Vector3f translationOffset,
        @NotNull Vector3f scaleMultiplier,
        float angularVelocityDegPerSec,
        @NotNull String animationControllerState
    ) {
        public BedrockBoneTransform {
            Objects.requireNonNull(boneName, "boneName");
            Objects.requireNonNull(rotationDegrees, "rotationDegrees");
            Objects.requireNonNull(translationOffset, "translationOffset");
            Objects.requireNonNull(scaleMultiplier, "scaleMultiplier");
            Objects.requireNonNull(animationControllerState, "animationControllerState");
        }
    }

    private final Map<String, KineticNetworkState> networks = new ConcurrentHashMap<>();
    private final Map<String, KineticNode> nodes = new ConcurrentHashMap<>();
    private final Map<String, String> nodeToNetwork = new ConcurrentHashMap<>();

    public void registerNetwork(@NotNull KineticNetworkState state) {
        Objects.requireNonNull(state, "state");
        networks.put(state.networkId(), state);
    }

    public void removeNetwork(@NotNull String networkId) {
        networks.remove(networkId);
        nodeToNetwork.entrySet().removeIf(entry -> entry.getValue().equals(networkId));
    }

    public void registerNode(@NotNull String networkId, @NotNull KineticNode node) {
        Objects.requireNonNull(networkId, "networkId");
        Objects.requireNonNull(node, "node");
        nodes.put(node.blockPosKey(), node);
        nodeToNetwork.put(node.blockPosKey(), networkId);
    }

    public void removeNode(@NotNull String blockPosKey) {
        nodes.remove(blockPosKey);
        nodeToNetwork.remove(blockPosKey);
    }

    @Nullable
    public KineticNetworkState getNetwork(@NotNull String networkId) {
        return networks.get(networkId);
    }

    @Nullable
    public KineticNode getNode(@NotNull String blockPosKey) {
        return nodes.get(blockPosKey);
    }

    /**
     * Compiles the animated Bedrock bone transform for a specific kinetic node given the elapsed client tick.
     *
     * @param blockPosKey World block position identifier (e.g. "x,y,z")
     * @param worldTick Game or client tick for continuous keyframe interpolation
     * @return The calculated BedrockBoneTransform, or null if node/network is untracked
     */
    @Nullable
    public BedrockBoneTransform compileTransform(@NotNull String blockPosKey, long worldTick) {
        KineticNode node = nodes.get(blockPosKey);
        if (node == null) {
            return null;
        }
        String networkId = nodeToNetwork.get(blockPosKey);
        if (networkId == null) {
            return null;
        }
        KineticNetworkState network = networks.get(networkId);
        if (network == null) {
            return null;
        }

        if (!network.isSpinning()) {
            return new BedrockBoneTransform(
                node.targetBoneName(),
                Vector3f.ZERO,
                Vector3f.ZERO,
                Vector3f.ONE,
                0.0f,
                network.isOverstressed() ? "overstressed_halt" : "idle"
            );
        }

        float effectiveRpm = node.computeEffectiveRpm(network.speedRpm());
        // 1 RPM = 360 degrees per minute = 6 degrees per second.
        // 20 ticks per second -> 0.3 degrees per tick per RPM.
        float degPerSec = effectiveRpm * 6.0f;
        float currentAngleDeg = (worldTick * (effectiveRpm * 0.3f)) % 360.0f;

        Vector3f rotation = switch (node.axis()) {
            case X -> Vector3f.from(currentAngleDeg, 0.0f, 0.0f);
            case Y -> Vector3f.from(0.0f, currentAngleDeg, 0.0f);
            case Z -> Vector3f.from(0.0f, 0.0f, currentAngleDeg);
        };

        String controllerState = "running_rpm_" + Math.round(Math.abs(effectiveRpm));

        return new BedrockBoneTransform(
            node.targetBoneName(),
            rotation,
            Vector3f.ZERO,
            Vector3f.ONE,
            degPerSec,
            controllerState
        );
    }

    /**
     * Compiles all active kinetic nodes into a batch of Bedrock bone transforms for client synchronization.
     */
    @NotNull
    public Map<String, BedrockBoneTransform> compileAllActiveTransforms(long worldTick) {
        Map<String, BedrockBoneTransform> results = new LinkedHashMap<>();
        for (String posKey : nodes.keySet()) {
            BedrockBoneTransform transform = compileTransform(posKey, worldTick);
            if (transform != null) {
                results.put(posKey, transform);
            }
        }
        return Collections.unmodifiableMap(results);
    }
}
