package org.geysermc.hydraulic.compat.analysis;

import net.minecraft.resources.Identifier;
import org.geysermc.hydraulic.compat.ContentInventory;
import org.geysermc.hydraulic.compat.MappingOwnership;
import org.geysermc.hydraulic.compat.mapping.ContentPatch;
import org.geysermc.hydraulic.compat.model.CompatibilityObject;
import org.geysermc.hydraulic.compat.runtime.RuntimeBridgeKind;
import org.geysermc.hydraulic.metadata.IdentifierMapping;
import org.geysermc.hydraulic.metadata.MetadataIndex;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityAnalyzerAdvancedFactsTest {
    @Test
    void classifiesAiAndCustomNetworkEvidenceFailClosed() {
        Identifier identifier = Identifier.fromNamespaceAndPath("example", "networked_guard");
        ContentPatch patch = new ContentPatch(identifier, "entity", Map.of(
            "entity.ai.behavior", "guard",
            "entity.ai.target", "player",
            "entity.ai.range", "24",
            "entity.network.required", "true",
            "entity.network.direction", "serverbound",
            "entity.network.channel", "example:guard_action"
        ), MappingOwnership.USER, "user/entities.json", MappingOwnership.USER.priority(), 0);
        MetadataIndex metadata = new MetadataIndex(
            Map.of(), Map.of(), Map.of(),
            Map.of(identifier, new IdentifierMapping(identifier, identifier, MappingOwnership.USER, "user/entities.json", 1000, 0)),
            Map.of(), Map.of(identifier, List.of(patch)), List.of(), MetadataIndex.Summary.empty()
        );

        CompatibilityObject object = new EntityAnalyzer().analyze(
            new ContentInventory.ContentDescriptor("entity", "example", identifier.toString(), true, false, List.of()),
            new EntityAnalyzerTestSupport().inventory(),
            metadata
        );

        assertEquals("guard", object.inventoryFacts().get("entity.ai.classified"));
        assertEquals("false", object.inventoryFacts().get("entity.ai.executable"));
        assertTrue(object.runtimeRequirements().contains(RuntimeBridgeKind.NETWORK_PROTOCOL.requirementId()));
        assertTrue(object.findings().stream().anyMatch(finding -> finding.code().equals("entity.custom_networking.unsupported")));
    }

    private static final class EntityAnalyzerTestSupport {
        ContentInventory.ModContentInventory inventory() {
            return new ContentInventory.ModContentInventory("example", "example", "Example", "1.0.0", List.of(),
                new org.geysermc.hydraulic.compat.model.ModFingerprint("example", "example", "1.0.0", "test", "26.2", 0, 0, 0, 0, 0, 0, 0, false, false, false, false, false, false, false, false),
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
    }
}