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
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MenuAnalyzer implements CompatibilityAnalyzer {
    @Override
    public @NotNull String kind() {
        return "menu";
    }

    @Override
    public @NotNull CompatibilityObject analyze(@NotNull ContentInventory.ContentDescriptor descriptor, @NotNull ContentInventory.ModContentInventory inventory, @NotNull MetadataIndex metadataIndex) {
        Identifier identifier = Identifier.parse(descriptor.javaIdentifier());
        IdentifierMapping mapping = metadataIndex.menuMapping(identifier);
        List<ContentPatch> patches = metadataIndex.contentPatches(identifier);
        boolean fallbackContainerBridge = metadataIndex.menuPatchTemplate(identifier) != null;
        boolean explicitActions = patches.stream().flatMap(patch -> patch.operations().entrySet().stream()).anyMatch(entry ->
            entry.getKey().startsWith("menu.button.")
                && ("button".equalsIgnoreCase(entry.getValue()) || "toggle".equalsIgnoreCase(entry.getValue()))
        );

        Capability registered = AnalyzerSupport.capability(CapabilityDomain.CONTENT, "registered", "Menu exists in the Java registry.");
        Capability mappingCapability = AnalyzerSupport.capability(CapabilityDomain.STATE_DATA, "menu_mapping", "Menu has explicit metadata or patch routing.");
        Capability interaction = AnalyzerSupport.capability(CapabilityDomain.INTERACTION, "container_interaction", "Menu interaction has a compatible Bedrock bridge.");
        Capability behavior = AnalyzerSupport.capability(CapabilityDomain.BEHAVIOR, "runtime_behavior", "Menu runtime behavior can be represented on Bedrock.");

        List<CapabilityRequirement> requirements = List.of(
            AnalyzerSupport.required(registered),
            AnalyzerSupport.required(mappingCapability),
            AnalyzerSupport.required(interaction),
            AnalyzerSupport.required(behavior)
        );
        List<CapabilityResult> results = List.of(
            AnalyzerSupport.result(registered, descriptor.registered(), "Menu registry discovered through BuiltInRegistries.MENU when available."),
            AnalyzerSupport.result(mappingCapability, mapping != null || !patches.isEmpty(), "Menu compatibility currently depends on explicit metadata or patches."),
            AnalyzerSupport.result(interaction, fallbackContainerBridge, fallbackContainerBridge ? "Metadata patch declares a compatible fallback Bedrock container layout." : "Menu runtime bridges are not implemented yet."),
            AnalyzerSupport.result(behavior, explicitActions, explicitActions ? "Explicit button and toggle actions execute through the authoritative Java menu handler." : "No explicit menu behavior contract is available.")
        );

        CapabilityProfile profile = new CapabilityProfile(descriptor.javaIdentifier(), requirements, results);
        Map<String, SupportResult> supportResults = new LinkedHashMap<>();
        supportResults.put("content", AnalyzerSupport.support("content", SupportLevel.AUTOMATIC, List.of(results.get(0)), List.of("Menus are discovered from the active runtime registry when available.")));
        supportResults.put("state_data", AnalyzerSupport.support("state_data", mapping != null || !patches.isEmpty() ? SupportLevel.ADAPTED : SupportLevel.UNSUPPORTED, List.of(results.get(1)), List.of("Menu routing is metadata-backed today.")));
        supportResults.put("interaction", AnalyzerSupport.support("interaction", fallbackContainerBridge ? SupportLevel.ADAPTED : SupportLevel.UNSUPPORTED, List.of(results.get(2)), List.of(fallbackContainerBridge ? "Metadata patches can route this menu through a compatible existing Geyser container translator." : "Generic Bedrock menu interaction bridges are not implemented.")));
        supportResults.put("behavior", AnalyzerSupport.support("behavior", explicitActions ? SupportLevel.ADAPTED : SupportLevel.UNSUPPORTED, List.of(results.get(3)), List.of(explicitActions ? "Explicit menu actions are metadata-backed and Java-authoritative." : "Menu behavior compatibility requires an explicit action contract.")));

        List<String> metadataSources = new ArrayList<>();
        if (mapping != null) {
            metadataSources.add(mapping.sourcePath());
        }

        Map<String, String> inventoryFacts = new LinkedHashMap<>(AnalyzerSupport.inventoryFacts(descriptor.registered(), descriptor.assetPresent(), mapping != null ? 1 : 0, patches.size()));
        String archetype = patches.stream().map(patch -> patch.operation("container.archetype")).filter(value -> value != null && !value.isBlank()).findFirst().orElse(null);
        if (archetype != null) {
            inventoryFacts.put("container.archetype", archetype);
        }
        for (String role : List.of("input", "output", "fuel", "upgrade", "fluid_input", "fluid_output", "catalyst", "player_inventory")) {
            String slots = patches.stream().map(patch -> patch.operation("container.slot." + role)).filter(value -> value != null && !value.isBlank()).findFirst().orElse(null);
            if (slots != null) {
                inventoryFacts.put("container.slot." + role, slots);
            }
        }
        for (ContentPatch patch : patches) {
            patch.operations().forEach((key, value) -> {
                if (key.startsWith("menu.button.") && ("button".equalsIgnoreCase(value) || "toggle".equalsIgnoreCase(value))) {
                    inventoryFacts.put(key, value.toLowerCase());
                }
            });
        }
        String findingDetail = fallbackContainerBridge
            ? explicitActions
                ? "Hydraulic can open this menu through a compatible fallback layout and execute its declared button actions; undeclared menu semantics and physical Bedrock behavior remain unverified."
                : "Hydraulic can open this menu through a compatible fallback container layout, but menu-specific behavior translation is still missing."
            : "Menu metadata exists, but there is no generic container bridge yet.";
        String findingAction = explicitActions
            ? "Validate the declared actions on a physical Bedrock client before treating menu support as complete."
            : "Implement the remaining menu behavior bridges before treating menu support as fully functional.";

        return AnalyzerSupport.object(
            descriptor.javaIdentifier(),
            descriptor.kind(),
            descriptor.modId(),
            inventoryFacts,
            profile,
            supportResults,
            new Confidence(fallbackContainerBridge ? 0.42D : mapping != null || !patches.isEmpty() ? 0.32D : 0.14D, fallbackContainerBridge ? "Menu analysis is metadata-backed with an explicit fallback container bridge." : "Menu analysis is currently metadata-backed and runtime-constrained."),
            AnalyzerSupport.provenance(this.getClass().getSimpleName(), mapping != null || !patches.isEmpty(), patches, metadataSources),
            List.of(new CompatibilityFinding("menu.bridge.partial", CompatibilityFinding.Severity.WARNING, fallbackContainerBridge ? "behavior" : "interaction", "Menu support is partial for " + descriptor.javaIdentifier(), findingDetail, findingAction, null))
        );
    }
}