package org.geysermc.hydraulic.compat.capability;

import org.geysermc.hydraulic.compat.CompatibilityStatus;
import org.geysermc.hydraulic.compat.ir.CompiledCompatibilityPlan;
import org.geysermc.hydraulic.compat.model.Confidence;
import org.geysermc.hydraulic.compat.model.SupportLevel;
import org.geysermc.hydraulic.compat.runtime.RuntimeBridgeKind;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CapabilityIRTest {

    @Test
    @DisplayName("Verify Canonical Capabilities exist and have valid domains")
    void canonicalCapabilitiesHaveValidDomains() {
        assertNotNull(CapabilityIR.Canonical.BLOCK_ENTITY_DATA);
        assertEquals(CapabilityDomain.STATE_DATA, CapabilityIR.Canonical.BLOCK_ENTITY_DATA.domain());
        assertEquals("block_entity_data", CapabilityIR.Canonical.BLOCK_ENTITY_DATA.name());

        assertNotNull(CapabilityIR.Canonical.ITEM_TRANSFER);
        assertEquals(CapabilityDomain.BEHAVIOR, CapabilityIR.Canonical.ITEM_TRANSFER.domain());

        assertNotNull(CapabilityIR.Canonical.MENU_CONTAINER);
        assertEquals(CapabilityDomain.INTERACTION, CapabilityIR.Canonical.MENU_CONTAINER.domain());

        assertNotNull(CapabilityIR.Canonical.PRESENTATION);
        assertEquals(CapabilityDomain.PRESENTATION, CapabilityIR.Canonical.PRESENTATION.domain());
    }

    @Test
    @DisplayName("Verify CapabilityIR compilation from CompiledCompatibilityPlan")
    void capabilityIRCompilationFromPlan() {
        CompiledCompatibilityPlan plan = new CompiledCompatibilityPlan(
            "test_mod",
            "block",
            "test_mod:crusher",
            "test_mod:crusher",
            SupportLevel.ADAPTED,
            CompatibilityStatus.COMPLETE,
            95,
            new Confidence(0.95, "test"),
            List.of(),
            List.of("item_transfer_bridge", "machine_behavior_bridge"),
            List.of(RuntimeBridgeKind.ITEM_TRANSFER, RuntimeBridgeKind.MACHINE_BEHAVIOR),
            Map.of("has_processing", "true", "can_insert", "true", "can_extract", "true"),
            true,
            null,
            true,
            null,
            true,
            true,
            false,
            false,
            true,
            ContainerType.GENERIC_9X3,
            null,
            List.of(),
            List.of(),
            List.of(),
            null,
            false,
            false,
            SupportLevel.ADAPTED,
            "machine",
            List.of()
        );

        CapabilityIR ir = CapabilityIR.from(plan);
        assertEquals("test_mod:crusher", ir.objectId());
        assertEquals(SupportLevel.ADAPTED, ir.overallLevel());
        assertEquals(95, ir.overallScore());
        assertTrue(ir.hasCapability("item_transfer"));
        assertTrue(ir.hasCapability("machine_processing"));
        assertTrue(ir.hasCapability("menu_container"));
        assertTrue(ir.hasCapability("presentation"));

        CapabilityIR.CapabilityDeclaration itemDecl = ir.getDeclaration("item_transfer");
        assertNotNull(itemDecl);
        assertTrue(itemDecl.isMandatory());
        assertTrue(itemDecl.isSupported());
        assertEquals(CapabilityIR.ComplianceStatus.PASS, itemDecl.complianceStatus());

        assertTrue(ir.isFullyCompliant());
    }
}
