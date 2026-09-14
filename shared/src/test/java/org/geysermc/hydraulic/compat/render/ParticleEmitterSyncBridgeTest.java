package org.geysermc.hydraulic.compat.render;

import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.packet.SpawnParticleEffectPacket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ParticleEmitterSyncBridgeTest {

    private ParticleEmitterSyncBridge bridge;

    @BeforeEach
    public void setUp() {
        bridge = new ParticleEmitterSyncBridge();
    }

    @Test
    public void testParticleEmitterCompilationAndTickFrequency() {
        ParticleEmitterSyncBridge.ParticleEmitterDefinition emitter = new ParticleEmitterSyncBridge.ParticleEmitterDefinition(
            "crusher_1",
            "10,64,10",
            ParticleEmitterSyncBridge.ParticleEmitterPreset.CRUSHING_WHEEL_SPARKS,
            "",
            Vector3f.from(10.0f, 64.0f, 10.0f),
            Vector3f.from(0.5f, 0.5f, 0.5f),
            2, // every 2 ticks
            0,
            true // active
        );

        bridge.registerEmitter(emitter);
        assertEquals(1, bridge.getEmitterCount());

        // Tick 0 -> 0 % 2 == 0 -> should emit
        List<SpawnParticleEffectPacket> packetsTick0 = bridge.compileTickParticles(0);
        assertEquals(1, packetsTick0.size());
        SpawnParticleEffectPacket p0 = packetsTick0.getFirst();
        assertEquals("hydraulic:crushing_sparks", p0.getIdentifier());
        assertEquals(10.5f, p0.getPosition().getX(), 0.001f);
        assertEquals(64.5f, p0.getPosition().getY(), 0.001f);

        // Tick 1 -> 1 % 2 != 0 -> should not emit
        List<SpawnParticleEffectPacket> packetsTick1 = bridge.compileTickParticles(1);
        assertTrue(packetsTick1.isEmpty());

        // Deactivate emitter
        bridge.setEmitterActive("crusher_1", false);
        List<SpawnParticleEffectPacket> packetsTick2 = bridge.compileTickParticles(2);
        assertTrue(packetsTick2.isEmpty());
    }

    @Test
    public void testCustomParticlePreset() {
        ParticleEmitterSyncBridge.ParticleEmitterDefinition customEmitter = new ParticleEmitterSyncBridge.ParticleEmitterDefinition(
            "laser_special",
            "50,70,50",
            ParticleEmitterSyncBridge.ParticleEmitterPreset.CUSTOM,
            "custom_addon:mega_spark",
            Vector3f.from(50.0f, 70.0f, 50.0f),
            Vector3f.ZERO,
            1,
            1, // nether
            true
        );

        bridge.registerEmitter(customEmitter);

        List<SpawnParticleEffectPacket> packets = bridge.compileTickParticles(5);
        assertEquals(1, packets.size());
        assertEquals("custom_addon:mega_spark", packets.getFirst().getIdentifier());
        assertEquals(1, packets.getFirst().getDimensionId());
    }
}
