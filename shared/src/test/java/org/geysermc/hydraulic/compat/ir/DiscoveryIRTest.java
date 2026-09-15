package org.geysermc.hydraulic.compat.ir;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscoveryIRTest {

    @Test
    @DisplayName("DiscoveryIR correctly models facts and immutable properties")
    void testDiscoveryIRFactModel() {
        DiscoveryIR ir = new DiscoveryIR(
            Identifier.fromNamespaceAndPath("create", "mechanical_press"),
            DiscoveryIR.ResourceKind.BLOCK_ENTITY,
            "create",
            "create-1.20.1.jar",
            "assets/create/models/block/mechanical_press.json",
            1024L,
            1700000000L,
            "abc123sha",
            Set.of("minecraft", "forge"),
            Map.of("has_kinetics", "true", "stress_impact", "8")
        );

        assertEquals("create:mechanical_press", ir.identifier().toString());
        assertEquals(DiscoveryIR.ResourceKind.BLOCK_ENTITY, ir.kind());
        assertTrue(ir.hasFact("has_kinetics"));
        assertTrue(ir.isFactTrue("has_kinetics"));
        assertEquals("8", ir.getFact("stress_impact"));
        assertFalse(ir.hasFact("missing_fact"));

        DiscoveryIR updated = ir.withFact("rpm_max", "256");
        assertTrue(updated.hasFact("rpm_max"));
        assertEquals("256", updated.getFact("rpm_max"));
    }
}
