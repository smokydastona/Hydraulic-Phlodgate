package org.geysermc.hydraulic.compat.render;

import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.ParticleType;
import org.cloudburstmc.protocol.bedrock.packet.SpawnParticleEffectPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Synchronizes custom Bedrock particle emitter effects with active Java machine processing ticks.
 * E.g., emitting sparks, smoke, laser beams, or crushing debris during active pulverizing/milling operations.
 */
public final class ParticleEmitterSyncBridge {

    public enum ParticleEmitterPreset {
        CRUSHING_WHEEL_SPARKS("hydraulic:crushing_sparks", 1, 0.05f),
        THERMAL_PULVERIZER_SMOKE("hydraulic:pulverizer_smoke", 2, 0.1f),
        MEKANISM_LASER_BEAM("hydraulic:laser_beam_focus", 1, 0.0f),
        BLAST_FURNACE_FLAME("hydraulic:furnace_flames", 2, 0.2f),
        FLUID_SPLASH("hydraulic:fluid_splash", 3, 0.15f),
        CUSTOM("", 1, 0.0f);

        private final String defaultEffectIdentifier;
        private final int defaultFrequencyTicks;
        private final float defaultPositionJitter;

        ParticleEmitterPreset(String defaultEffectIdentifier, int defaultFrequencyTicks, float defaultPositionJitter) {
            this.defaultEffectIdentifier = defaultEffectIdentifier;
            this.defaultFrequencyTicks = defaultFrequencyTicks;
            this.defaultPositionJitter = defaultPositionJitter;
        }

        public String getDefaultEffectIdentifier() {
            return defaultEffectIdentifier;
        }

        public int getDefaultFrequencyTicks() {
            return defaultFrequencyTicks;
        }

        public float getDefaultPositionJitter() {
            return defaultPositionJitter;
        }
    }

    public record ParticleEmitterDefinition(
        @NotNull String emitterId,
        @NotNull String blockPosKey,
        @NotNull ParticleEmitterPreset preset,
        @NotNull String customEffectIdentifier,
        @NotNull Vector3f basePosition,
        @NotNull Vector3f offset,
        int frequencyTicks,
        int dimensionId,
        boolean active
    ) {
        public ParticleEmitterDefinition {
            Objects.requireNonNull(emitterId, "emitterId");
            Objects.requireNonNull(blockPosKey, "blockPosKey");
            Objects.requireNonNull(preset, "preset");
            Objects.requireNonNull(customEffectIdentifier, "customEffectIdentifier");
            Objects.requireNonNull(basePosition, "basePosition");
            Objects.requireNonNull(offset, "offset");
        }

        public String getEffectiveIdentifier() {
            return preset == ParticleEmitterPreset.CUSTOM ? customEffectIdentifier : preset.getDefaultEffectIdentifier();
        }

        public Vector3f getEmitPosition() {
            return basePosition.add(offset);
        }

        public boolean shouldEmit(long worldTick) {
            if (!active) {
                return false;
            }
            int freq = frequencyTicks > 0 ? frequencyTicks : Math.max(1, preset.getDefaultFrequencyTicks());
            return worldTick % freq == 0;
        }
    }

    private final Map<String, ParticleEmitterDefinition> emitters = new ConcurrentHashMap<>();

    public void registerEmitter(@NotNull ParticleEmitterDefinition emitter) {
        Objects.requireNonNull(emitter, "emitter");
        emitters.put(emitter.emitterId(), emitter);
    }

    public void unregisterEmitter(@NotNull String emitterId) {
        emitters.remove(emitterId);
    }

    public void setEmitterActive(@NotNull String emitterId, boolean active) {
        ParticleEmitterDefinition current = emitters.get(emitterId);
        if (current != null) {
            emitters.put(emitterId, new ParticleEmitterDefinition(
                current.emitterId(),
                current.blockPosKey(),
                current.preset(),
                current.customEffectIdentifier(),
                current.basePosition(),
                current.offset(),
                current.frequencyTicks(),
                current.dimensionId(),
                active
            ));
        }
    }

    @Nullable
    public ParticleEmitterDefinition getEmitter(@NotNull String emitterId) {
        return emitters.get(emitterId);
    }

    public int getEmitterCount() {
        return emitters.size();
    }

    /**
     * Evaluates all registered particle emitters for the given world tick and compiles SpawnParticleEffectPacket list.
     *
     * @param worldTick The current game/machine processing tick
     * @return List of generated Bedrock particle packets ready to be sent to clients
     */
    @NotNull
    public List<SpawnParticleEffectPacket> compileTickParticles(long worldTick) {
        List<SpawnParticleEffectPacket> packets = new ArrayList<>();

        for (ParticleEmitterDefinition emitter : emitters.values()) {
            if (!emitter.shouldEmit(worldTick)) {
                continue;
            }

            SpawnParticleEffectPacket packet = new SpawnParticleEffectPacket();
            packet.setIdentifier(emitter.getEffectiveIdentifier());
            packet.setPosition(emitter.getEmitPosition());
            packet.setDimensionId(emitter.dimensionId());
            packets.add(packet);
        }

        return Collections.unmodifiableList(packets);
    }
}
