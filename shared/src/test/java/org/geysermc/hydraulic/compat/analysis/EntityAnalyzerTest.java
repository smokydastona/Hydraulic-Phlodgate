package org.geysermc.hydraulic.compat.analysis;

import net.minecraft.resources.Identifier;
import org.geysermc.hydraulic.compat.ContentInventory;
import org.geysermc.hydraulic.compat.MappingOwnership;
import org.geysermc.hydraulic.compat.mapping.ContentPatch;
import org.geysermc.hydraulic.compat.model.CompatibilityObject;
import org.geysermc.hydraulic.compat.model.SupportLevel;
import org.geysermc.hydraulic.metadata.IdentifierMapping;
import org.geysermc.hydraulic.metadata.MetadataIndex;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityAnalyzerTest {
    @Test
    void exposesVisualOnlyRuntimeTagForMetadataBackedEntity() {
        Identifier identifier = Identifier.fromNamespaceAndPath("example", "test_entity");
        ContentPatch patch = new ContentPatch(
            identifier,
            "entity",
            Map.of(
                "behavior.required", "true",
                "behavior.tag", "visual_only_runtime"
            ),
            MappingOwnership.USER,
            "user/entities.json",
            MappingOwnership.USER.priority(),
            0
        );
        MetadataIndex metadataIndex = new MetadataIndex(
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(identifier, new IdentifierMapping(identifier, Identifier.fromNamespaceAndPath("example", "bedrock_entity"), MappingOwnership.USER, "user/entities.json", 1000, 0)),
            Map.of(),
            Map.of(identifier, List.of(patch)),
            List.of(),
            MetadataIndex.Summary.empty()
        );
        ContentInventory.ContentDescriptor descriptor = new ContentInventory.ContentDescriptor("entity", "example", "example:test_entity", true, false, List.of());

        CompatibilityObject object = new EntityAnalyzer().analyze(descriptor, emptyInventory(), metadataIndex);

        assertEquals("true", object.inventoryFacts().get("behavior_required"));
        assertEquals("visual_only_runtime", object.inventoryFacts().get("behavior_tag"));
        assertTrue(object.runtimeRequirements().contains("entity_interaction_bridge"));
        assertTrue(object.runtimeRequirements().contains("entity_behavior_bridge"));
        assertEquals(SupportLevel.ADAPTED, object.supportResults().get("presentation").level());
    }

    @Test
    void exposesMetadataBackedInteractionPromptForEntity() {
        Identifier identifier = Identifier.fromNamespaceAndPath("example", "prompt_entity");
        ContentPatch patch = new ContentPatch(
            identifier,
            "entity",
            Map.of("interaction.prompt", "Open Prompt Entity"),
            MappingOwnership.USER,
            "user/entities.json",
            MappingOwnership.USER.priority(),
            0
        );
        MetadataIndex metadataIndex = new MetadataIndex(
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(identifier, new IdentifierMapping(identifier, Identifier.fromNamespaceAndPath("example", "bedrock_entity"), MappingOwnership.USER, "user/entities.json", 1000, 0)),
            Map.of(),
            Map.of(identifier, List.of(patch)),
            List.of(),
            MetadataIndex.Summary.empty()
        );
        ContentInventory.ContentDescriptor descriptor = new ContentInventory.ContentDescriptor("entity", "example", "example:prompt_entity", true, false, List.of());

        CompatibilityObject object = new EntityAnalyzer().analyze(descriptor, emptyInventory(), metadataIndex);

        assertEquals("Open Prompt Entity", object.inventoryFacts().get("interaction_prompt"));
        assertEquals(SupportLevel.ADAPTED, object.supportResults().get("interaction").level());
        assertFalse(object.runtimeRequirements().contains("entity_interaction_bridge"));
        assertTrue(object.adapterBindings().stream().anyMatch(binding -> binding.feature() == org.geysermc.hydraulic.compat.adapter.AdapterFeature.ENTITY_INTERACTION_PROMPT));
    }

    @Test
    void compilesMetadataBackedEntityActionFacts() {
        Identifier identifier = Identifier.fromNamespaceAndPath("example", "mountable_entity");
        ContentPatch patch = new ContentPatch(
            identifier,
            "entity",
            Map.of(
                "interaction.entity.action", "mount",
                "interaction.entity.hand", "main_hand"
            ),
            MappingOwnership.USER,
            "user/entities.json",
            MappingOwnership.USER.priority(),
            0
        );
        MetadataIndex metadataIndex = new MetadataIndex(
            Map.of(), Map.of(), Map.of(),
            Map.of(identifier, new IdentifierMapping(identifier, Identifier.fromNamespaceAndPath("example", "mountable_entity"), MappingOwnership.USER, "user/entities.json", 1000, 0)),
            Map.of(), Map.of(identifier, List.of(patch)), List.of(), MetadataIndex.Summary.empty()
        );

        CompatibilityObject object = new EntityAnalyzer().analyze(
            new ContentInventory.ContentDescriptor("entity", "example", "example:mountable_entity", true, false, List.of()),
            emptyInventory(),
            metadataIndex
        );

        assertEquals("mount", object.inventoryFacts().get("interaction.entity.action"));
        assertEquals(SupportLevel.ADAPTED, object.supportResults().get("interaction").level());
        assertFalse(object.runtimeRequirements().contains("entity_interaction_bridge"));
    }

    @Test
    void automaticallyMapsRegisteredEntityWithoutManualMetadata() {
        MetadataIndex metadataIndex = new MetadataIndex(
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            List.of(),
            MetadataIndex.Summary.empty()
        );
        ContentInventory.ContentDescriptor descriptor = new ContentInventory.ContentDescriptor("entity", "citadel", "citadel:citadel_entity", true, false, List.of());

        CompatibilityObject object = new EntityAnalyzer().analyze(descriptor, emptyInventory(), metadataIndex);

        assertEquals("visual_only_runtime", object.inventoryFacts().get("behavior_tag"));
        assertEquals(SupportLevel.ADAPTED, object.supportResults().get("presentation").level());
        assertEquals(SupportLevel.AUTOMATIC, object.supportResults().get("content").level());
        assertTrue(org.geysermc.hydraulic.compat.runtime.CompatibilityDecisions.allowsCustomEntityRegistration(object));
    }

    private static ContentInventory.ModContentInventory emptyInventory() {
        return new ContentInventory.ModContentInventory(
            "example",
            "example",
            "Example",
            "1.0.0",
            List.of(),
            new org.geysermc.hydraulic.compat.model.ModFingerprint(
                "example",
                "example",
                "1.0.0",
                "test",
                "1.21.0",
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false
            ),
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