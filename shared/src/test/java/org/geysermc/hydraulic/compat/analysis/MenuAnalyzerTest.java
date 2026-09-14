package org.geysermc.hydraulic.compat.analysis;

import net.minecraft.resources.Identifier;
import org.geysermc.hydraulic.compat.ContentInventory;
import org.geysermc.hydraulic.compat.MappingOwnership;
import org.geysermc.hydraulic.compat.mapping.ContentPatch;
import org.geysermc.hydraulic.compat.model.CompatibilityObject;
import org.geysermc.hydraulic.compat.model.ModFingerprint;
import org.geysermc.hydraulic.compat.model.SupportLevel;
import org.geysermc.hydraulic.metadata.MetadataIndex;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuAnalyzerTest {
    @Test
    void exposesMetadataBackedContainerBridge() {
        ContentPatch patch = new ContentPatch(
            Identifier.fromNamespaceAndPath("test", "barrel_menu"),
            "menu",
            Map.of("bedrock.menu.container_type", "generic_9x3"),
            MappingOwnership.USER,
            "user/menus.json",
            MappingOwnership.USER.priority(),
            0
        );
        MetadataIndex metadataIndex = new MetadataIndex(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(patch.target(), List.of(patch)), List.of(), MetadataIndex.Summary.empty());
        ContentInventory.ContentDescriptor descriptor = new ContentInventory.ContentDescriptor("menu", "test", "test:barrel_menu", true, false, List.of());

        CompatibilityObject object = new MenuAnalyzer().analyze(descriptor, emptyInventory(), metadataIndex);

        assertEquals(SupportLevel.ADAPTED, object.supportResults().get("interaction").level());
        assertFalse(object.runtimeRequirements().contains("container_bridge"));
        assertTrue(object.runtimeRequirements().contains("menu_behavior_bridge"));
    }

    @Test
    void keepsContainerBridgeRequirementWhenNoFallbackTemplateExists() {
        ContentInventory.ContentDescriptor descriptor = new ContentInventory.ContentDescriptor("menu", "test", "test:barrel_menu", true, false, List.of());

        CompatibilityObject object = new MenuAnalyzer().analyze(descriptor, emptyInventory(), MetadataIndex.empty());

        assertEquals(SupportLevel.UNSUPPORTED, object.supportResults().get("interaction").level());
        assertTrue(object.runtimeRequirements().contains("container_bridge"));
    }

    @Test
    void compilesMachineArchetypeAndSlotRoles() {
        Identifier target = Identifier.fromNamespaceAndPath("test", "machine_menu");
        ContentPatch patch = new ContentPatch(
            target,
            "menu",
            Map.of(
                "bedrock.menu.container_type", "generic_9x3",
                "container.archetype", "machine",
                "menu.button.0", "toggle",
                "container.slot.input", "2",
                "container.slot.output", "0",
                "container.slot.player_inventory", "3,4,5"
            ),
            MappingOwnership.USER,
            "user/menus.json",
            MappingOwnership.USER.priority(),
            0
        );
        MetadataIndex metadataIndex = new MetadataIndex(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(target, List.of(patch)), List.of(), MetadataIndex.Summary.empty());
        CompatibilityObject object = new MenuAnalyzer().analyze(
            new ContentInventory.ContentDescriptor("menu", "test", target.toString(), true, false, List.of()),
            emptyInventory(),
            metadataIndex
        );

        assertEquals("machine", object.inventoryFacts().get("container.archetype"));
        assertEquals("toggle", object.inventoryFacts().get("menu.button.0"));
        assertEquals("2", object.inventoryFacts().get("container.slot.input"));
        assertEquals("0", object.inventoryFacts().get("container.slot.output"));
        assertEquals("3,4,5", object.inventoryFacts().get("container.slot.player_inventory"));
        assertEquals(SupportLevel.ADAPTED, object.supportResults().get("behavior").level());
        assertFalse(object.runtimeRequirements().contains("menu_behavior_bridge"));
    }

    private static ContentInventory.ModContentInventory emptyInventory() {
        return new ContentInventory.ModContentInventory(
            "test",
            "test",
            "Test Mod",
            "1.0.0",
            List.of(),
            new ModFingerprint("test", "test", "1.0.0", "test", "1.21.0", 0, 0, 0, 0, 0, 1, 0, false, false, false, false, false, false, false, false),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of()
        );
    }
}