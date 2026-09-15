package org.geysermc.hydraulic.compat.analysis;

import net.minecraft.resources.Identifier;
import org.geysermc.hydraulic.compat.ContentInventory;
import org.geysermc.hydraulic.compat.capability.Capability;
import org.geysermc.hydraulic.compat.capability.CapabilityDomain;
import org.geysermc.hydraulic.compat.capability.CapabilityProfile;
import org.geysermc.hydraulic.compat.capability.CapabilityRequirement;
import org.geysermc.hydraulic.compat.capability.CapabilityResult;
import org.geysermc.hydraulic.compat.mapping.ContentPatch;
import org.geysermc.hydraulic.compat.model.CompatibilityFinding;
import org.geysermc.hydraulic.compat.model.CompatibilityObject;
import org.geysermc.hydraulic.compat.model.Confidence;
import org.geysermc.hydraulic.compat.model.SupportLevel;
import org.geysermc.hydraulic.compat.model.SupportResult;
import org.geysermc.hydraulic.metadata.IdentifierMapping;
import org.geysermc.hydraulic.metadata.MetadataIndex;
import org.geysermc.hydraulic.compat.runtime.EntityInteractionActionPlan;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EntityAnalyzer implements CompatibilityAnalyzer {
    @Override
    public @NotNull String kind() {
        return "entity";
    }

    @Override
    public @NotNull CompatibilityObject analyze(@NotNull ContentInventory.ContentDescriptor descriptor, @NotNull ContentInventory.ModContentInventory inventory, @NotNull MetadataIndex metadataIndex) {
        IdentifierMapping mapping = metadataIndex.entityMapping(Identifier.parse(descriptor.javaIdentifier()));
        List<ContentPatch> patches = metadataIndex.contentPatches(Identifier.parse(descriptor.javaIdentifier()));
        String interactionPrompt = patches.stream()
            .map(patch -> patch.operation("interaction.prompt"))
            .filter(prompt -> prompt != null && !prompt.isBlank())
            .findFirst()
            .orElse(null);
        boolean behaviorRequired = patches.stream().anyMatch(patch -> patch.booleanOperation("behavior.required") || patch.hasOperationPrefix("behavior."));
        String behaviorTag = patches.stream()
            .map(patch -> patch.operation("behavior.tag"))
            .filter(tag -> tag != null && !tag.isBlank())
            .findFirst()
            .orElse(null);
        Map<String, String> entityActionFacts = new LinkedHashMap<>();
        for (ContentPatch patch : patches) {
            putIfPresent(entityActionFacts, "interaction.entity.action", patch.operation("interaction.entity.action"));
            putIfPresent(entityActionFacts, "interaction.entity.hand", patch.operation("interaction.entity.hand"));
            putIfPresent(entityActionFacts, "interaction.entity.item", patch.operation("interaction.entity.item"));
        }
        EntityInteractionActionPlan entityActionPlan = EntityInteractionActionPlan.fromFacts(entityActionFacts);

        Capability registered = AnalyzerSupport.capability(CapabilityDomain.CONTENT, "registered", "Entity exists in the Java registry.");
        Capability presentation = AnalyzerSupport.capability(CapabilityDomain.PRESENTATION, "presentation_mapping", "Entity has explicit presentation mapping data.");
        Capability interaction = AnalyzerSupport.capability(CapabilityDomain.INTERACTION, "entity_interaction", "Entity interaction has a compatible Bedrock bridge.");
        Capability behavior = AnalyzerSupport.capability(CapabilityDomain.BEHAVIOR, "runtime_behavior", "Entity runtime behavior can be represented on Bedrock.");

        boolean hasPresentation = mapping != null || !patches.isEmpty() || descriptor.registered();
        List<CapabilityRequirement> requirements = List.of(
            AnalyzerSupport.required(registered),
            AnalyzerSupport.required(presentation),
            AnalyzerSupport.required(interaction),
            AnalyzerSupport.required(behavior)
        );
        boolean hasInteraction = interactionPrompt != null || entityActionPlan != null;
        List<CapabilityResult> results = List.of(
            AnalyzerSupport.result(registered, descriptor.registered(), "Registry lookup from BuiltInRegistries.ENTITY_TYPE."),
            AnalyzerSupport.result(presentation, hasPresentation, hasPresentation ? "Entity presentation mapping is available." : "Entity support currently depends on metadata or patch evidence only."),
            AnalyzerSupport.result(interaction, hasInteraction, interactionPrompt != null ? "Metadata patch declares a Bedrock interaction prompt for the existing Java interaction path." : entityActionPlan != null ? "Metadata patch declares a validated server-authoritative entity action." : "Runtime entity bridges are not implemented yet."),
            AnalyzerSupport.result(behavior, false, "Behavior representation for entities is not implemented yet.")
        );

        CapabilityProfile profile = new CapabilityProfile(descriptor.javaIdentifier(), requirements, results);
        Map<String, SupportResult> supportResults = new LinkedHashMap<>();
        supportResults.put("content", AnalyzerSupport.support("content", SupportLevel.AUTOMATIC, List.of(results.get(0)), List.of("Entity discovery is registry-backed.")));
        supportResults.put("presentation", AnalyzerSupport.support("presentation", hasPresentation ? SupportLevel.ADAPTED : SupportLevel.UNSUPPORTED, List.of(results.get(1)), List.of(mapping != null || !patches.isEmpty() ? "Entity presentation relies on explicit metadata." : "Entity presentation uses automatic visual-only mapping.")));
        supportResults.put("interaction", AnalyzerSupport.support("interaction", hasInteraction ? SupportLevel.ADAPTED : SupportLevel.UNSUPPORTED, List.of(results.get(2)), List.of(interactionPrompt != null ? "Metadata patches can surface a Bedrock interaction prompt while reusing the existing Java interaction packet path." : entityActionPlan != null ? "A compiled entity action is executed on the Java server thread." : "Entity interaction bridges are still missing.")));
        supportResults.put("behavior", AnalyzerSupport.support("behavior", SupportLevel.UNSUPPORTED, List.of(results.get(3)), List.of("Entity runtime translation is not implemented.")));

        List<CompatibilityFinding> findings = List.of(
            new CompatibilityFinding(
                "entity.bridge.partial",
                CompatibilityFinding.Severity.WARNING,
                hasInteraction ? "behavior" : "interaction",
                "Entity support is partial for " + descriptor.javaIdentifier(),
                hasInteraction
                    ? "Hydraulic has a metadata-backed entity interaction seam, but uncompiled entity behavior remains unsupported."
                    : "Current entity coverage is metadata-declared rather than interaction- or behavior-backed.",
                "Implement the remaining entity bridges before treating entity support as functional.",
                null
            )
        );
        List<String> metadataSources = new ArrayList<>();
        if (mapping != null) {
            metadataSources.add(mapping.sourcePath());
        }

        Map<String, String> inventoryFacts = AnalyzerSupport.inventoryFacts(descriptor.registered(), descriptor.assetPresent(), mapping != null ? 1 : 0, patches.size());
        if (interactionPrompt != null) {
            inventoryFacts.put("interaction_prompt", interactionPrompt);
        }
        inventoryFacts.putAll(entityActionFacts);
        inventoryFacts.put("behavior_required", Boolean.toString(behaviorRequired));
        if (behaviorTag == null || behaviorTag.isBlank()) {
            behaviorTag = "visual_only_runtime";
        }
        inventoryFacts.put("behavior_tag", behaviorTag);

        return AnalyzerSupport.object(
            descriptor.javaIdentifier(),
            descriptor.kind(),
            descriptor.modId(),
            inventoryFacts,
            profile,
            supportResults,
            new Confidence(entityActionPlan != null ? 0.62D : interactionPrompt != null ? 0.46D : mapping != null || !patches.isEmpty() ? 0.38D : 0.28D, entityActionPlan != null ? "Entity analysis is metadata-backed with a validated server-authoritative action contract." : interactionPrompt != null ? "Entity analysis is metadata-backed with an explicit interaction prompt bridge, but runtime behavior remains constrained." : "Entity analysis is registry-backed with automatic visual-only custom entity definition."),
            AnalyzerSupport.provenance(this.getClass().getSimpleName(), mapping != null || !patches.isEmpty(), patches, metadataSources),
            findings
        );
    }

    private static void putIfPresent(@NotNull Map<String, String> facts, @NotNull String key, @Nullable String value) {
        if (value != null && !value.isBlank()) {
            facts.put(key, value);
        }
    }
}