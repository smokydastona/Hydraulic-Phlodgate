package org.geysermc.hydraulic.compat.render;

import org.cloudburstmc.math.vector.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class KineticRotationVisualizerTest {

    private KineticRotationVisualizer visualizer;

    @BeforeEach
    public void setUp() {
        visualizer = new KineticRotationVisualizer();
    }

    @Test
    public void testKineticTransformCompilation() {
        visualizer.registerNetwork(new KineticRotationVisualizer.KineticNetworkState(
            "create:net_1",
            128.0f,
            2048.0f,
            4096.0f,
            false
        ));

        KineticRotationVisualizer.KineticNode shaftNode = new KineticRotationVisualizer.KineticNode(
            "100,64,200",
            "create",
            KineticRotationVisualizer.KineticNodeType.SHAFT,
            KineticRotationVisualizer.RotationAxis.Y,
            1.0f,
            false,
            "bone_shaft"
        );

        visualizer.registerNode("create:net_1", shaftNode);

        KineticRotationVisualizer.BedrockBoneTransform transformTick0 = visualizer.compileTransform("100,64,200", 0);
        assertNotNull(transformTick0);
        assertEquals("bone_shaft", transformTick0.boneName());
        assertEquals(0.0f, transformTick0.rotationDegrees().getY(), 0.001f);
        assertEquals(128.0f * 6.0f, transformTick0.angularVelocityDegPerSec(), 0.001f);

        KineticRotationVisualizer.BedrockBoneTransform transformTick10 = visualizer.compileTransform("100,64,200", 10);
        assertNotNull(transformTick10);
        // 10 ticks * 128 RPM * 0.3 deg/tick = 384 degrees -> 24 degrees mod 360
        assertEquals(24.0f, transformTick10.rotationDegrees().getY(), 0.001f);
    }

    @Test
    public void testOverstressedAndHaltState() {
        visualizer.registerNetwork(new KineticRotationVisualizer.KineticNetworkState(
            "create:net_overloaded",
            64.0f,
            8192.0f,
            4096.0f,
            true // Overstressed
        ));

        KineticRotationVisualizer.KineticNode cogNode = new KineticRotationVisualizer.KineticNode(
            "10,64,10",
            "create",
            KineticRotationVisualizer.KineticNodeType.COGWHEEL,
            KineticRotationVisualizer.RotationAxis.X,
            2.0f,
            true, // reversed
            "bone_cog"
        );

        visualizer.registerNode("create:net_overloaded", cogNode);

        KineticRotationVisualizer.BedrockBoneTransform transform = visualizer.compileTransform("10,64,10", 50);
        assertNotNull(transform);
        assertEquals("overstressed_halt", transform.animationControllerState());
        assertEquals(0.0f, transform.angularVelocityDegPerSec(), 0.001f);
        assertEquals(Vector3f.ZERO, transform.rotationDegrees());
    }

    @Test
    public void testBatchActiveTransforms() {
        visualizer.registerNetwork(new KineticRotationVisualizer.KineticNetworkState(
            "create:net_crusher",
            32.0f,
            500.0f,
            1000.0f,
            false
        ));

        visualizer.registerNode("create:net_crusher", new KineticRotationVisualizer.KineticNode(
            "1,1,1",
            "create",
            KineticRotationVisualizer.KineticNodeType.CRUSHING_WHEEL,
            KineticRotationVisualizer.RotationAxis.Z,
            1.0f,
            false,
            "wheel_left"
        ));

        visualizer.registerNode("create:net_crusher", new KineticRotationVisualizer.KineticNode(
            "1,1,2",
            "create",
            KineticRotationVisualizer.KineticNodeType.CRUSHING_WHEEL,
            KineticRotationVisualizer.RotationAxis.Z,
            1.0f,
            true,
            "wheel_right"
        ));

        Map<String, KineticRotationVisualizer.BedrockBoneTransform> batch = visualizer.compileAllActiveTransforms(100);
        assertEquals(2, batch.size());
        assertTrue(batch.containsKey("1,1,1"));
        assertTrue(batch.containsKey("1,1,2"));
    }
}
