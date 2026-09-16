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
import org.geysermc.hydraulic.metadata.BlockMapping;
import org.geysermc.hydraulic.metadata.MetadataIndex;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BlockAnalyzer implements CompatibilityAnalyzer {
    @Override
    public @NotNull String kind() {
        return "block";
    }

    @Override
    public @NotNull CompatibilityObject analyze(@NotNull ContentInventory.ContentDescriptor descriptor, @NotNull ContentInventory.ModContentInventory inventory, @NotNull MetadataIndex metadataIndex) {
        Identifier identifier = Identifier.parse(descriptor.javaIdentifier());
        BlockMapping mapping = metadataIndex.blockMapping(identifier);
        List<ContentPatch> patches = metadataIndex.contentPatches(identifier);
        boolean behaviorRequired = mapping != null && mapping.rules().stream().anyMatch(rule -> rule.behaviorRequired())
            || patches.stream().anyMatch(patch -> patch.hasOperationPrefix("behavior.")
            || patch.hasOperationPrefix("interaction.")
            || patch.hasOperationPrefix("machine.")
            || patch.hasOperationPrefix("transfer."));
        String behaviorTag = mapping != null ? mapping.rules().stream().map(rule -> rule.behaviorTag()).filter(tag -> tag != null && !tag.isBlank()).findFirst().orElse(null) : null;
        if (behaviorTag == null) {
            behaviorTag = patches.stream().map(patch -> patch.operation("behavior.tag")).filter(tag -> tag != null && !tag.isBlank()).findFirst().orElse(null);
        }
        boolean visualPatch = patches.stream().anyMatch(patch -> patch.hasOperationPrefix("visual.") || patch.hasOperationPrefix("bedrock."));

        // Computed before the support results so "behavior" can reflect real fact completeness,
        // not just the presence of a machine/transfer declaration.
        Map<String, String> behaviorFacts = BehaviorFactExtractor.extractFacts(patches);
        org.geysermc.hydraulic.compat.discovery.SemanticDiscoveryEngine.DiscoveredSemanticProfile semanticProfile = org.geysermc.hydraulic.compat.discovery.SemanticDiscoveryEngine.discover(identifier, behaviorFacts, descriptor.registered());
        behaviorFacts = semanticProfile.facts();

        AnalyzerSupport.MachineBehaviorReadiness machineReadiness = AnalyzerSupport.machineBehaviorReadiness(behaviorFacts);
        boolean machineExecutable = machineReadiness == AnalyzerSupport.MachineBehaviorReadiness.EXECUTABLE;

        Capability registered = AnalyzerSupport.capability(CapabilityDomain.CONTENT, "registered", "Block exists in the Java registry.");
        Capability blockAsset = AnalyzerSupport.capability(CapabilityDomain.PRESENTATION, "block_asset", "Block has discoverable blockstate assets for conversion.");
        Capability stateTranslation = AnalyzerSupport.capability(CapabilityDomain.STATE_DATA, "state_translation", "Block has explicit state mapping or patch data.");
        Capability placement = AnalyzerSupport.capability(CapabilityDomain.INTERACTION, "placement", "Block can be placed by Bedrock clients.");
        Capability breaking = AnalyzerSupport.capability(CapabilityDomain.INTERACTION, "breaking", "Block can be broken by Bedrock clients.");
        Capability contextualUse = AnalyzerSupport.capability(CapabilityDomain.INTERACTION, "contextual_use", "Block use interaction can be represented without a runtime bridge.");
        Capability runtimeBehavior = AnalyzerSupport.capability(CapabilityDomain.BEHAVIOR, "runtime_behavior", "Special block behavior does not require a dedicated Bedrock bridge.");

        List<CapabilityRequirement> requirements = List.of(
            AnalyzerSupport.required(registered),
            AnalyzerSupport.required(blockAsset),
            AnalyzerSupport.required(stateTranslation),
            AnalyzerSupport.required(placement),
            AnalyzerSupport.required(breaking),
            AnalyzerSupport.required(contextualUse),
            AnalyzerSupport.required(runtimeBehavior)
        );

        List<CapabilityResult> results = List.of(
            AnalyzerSupport.result(registered, descriptor.registered(), "Registry lookup from BuiltInRegistries.BLOCK."),
            AnalyzerSupport.result(blockAsset, descriptor.assetPresent(), "PackManager found a matching blockstate asset for this block."),
            AnalyzerSupport.result(stateTranslation, mapping != null || !patches.isEmpty(), "Block metadata mappings and patches drive explicit state handling."),
            AnalyzerSupport.result(placement, true, "Current custom block path can place converted blocks."),
            AnalyzerSupport.result(breaking, true, "Current custom block path can break converted blocks."),
            AnalyzerSupport.result(contextualUse, !behaviorRequired, "Complex interaction still depends on future runtime bridges."),
            AnalyzerSupport.result(runtimeBehavior, !behaviorRequired || machineExecutable, machineExecutable
                ? "Machine processing, inventory, and item-transfer facts are structurally complete and executable via MachineBridgeFactory.createProcessing()."
                : "Behavior-required blocks still need behavior generation or bridges.")
        );

        CapabilityProfile profile = new CapabilityProfile(descriptor.javaIdentifier(), requirements, results);
        Map<String, SupportResult> supportResults = new LinkedHashMap<>();
        supportResults.put("content", AnalyzerSupport.support("content", SupportLevel.AUTOMATIC, List.of(results.get(0)), List.of("Registry discovery is backed by the current pack manager initialization flow.")));
        supportResults.put("presentation", AnalyzerSupport.support("presentation", visualPatch ? SupportLevel.ADAPTED : SupportLevel.AUTOMATIC, List.of(results.get(1)), List.of("Presentation coverage currently relies on discovered blockstate assets and optional metadata overrides.")));
        supportResults.put("state_data", AnalyzerSupport.support("state_data", mapping != null || !patches.isEmpty() ? SupportLevel.ADAPTED : SupportLevel.APPROXIMATED, List.of(results.get(2)), List.of("Generic state translation is not implemented yet; this score reflects explicit metadata and patch evidence only.")));
        supportResults.put("interaction", AnalyzerSupport.support("interaction", behaviorRequired ? SupportLevel.APPROXIMATED : SupportLevel.AUTOMATIC, List.of(results.get(3), results.get(4), results.get(5)), List.of("Placement and breaking are established, but contextual interaction remains conservative until bridges land.")));
        supportResults.put("behavior", AnalyzerSupport.support("behavior", machineExecutable ? SupportLevel.ADAPTED : (behaviorRequired ? SupportLevel.UNSUPPORTED : SupportLevel.AUTOMATIC), List.of(results.get(6)), List.of(machineExecutable
            ? "Declared machine.processing/machine.inventory/transfer.item facts form a complete, well-formed recipe and slot contract; MachineBridgeFactory.createProcessing() will bind a real executable bridge once a live block entity is queried."
            : "Behavior pack generation and runtime bridges are not yet implemented.")));

        List<CompatibilityFinding> findings = new ArrayList<>();
        if (!descriptor.assetPresent()) {
            findings.add(new CompatibilityFinding("block.asset.missing", CompatibilityFinding.Severity.WARNING, "presentation", "Block asset discovery failed for " + descriptor.javaIdentifier(), "The block does not currently have a discovered blockstate asset in this mod root.", "Add a blockstate asset or metadata patch for this block.", null));
        }
        if (machineExecutable) {
            findings.add(new CompatibilityFinding("block.behavior.executable", CompatibilityFinding.Severity.INFO, "behavior", "Block declares a structurally complete machine processing/inventory contract.", "machine.processing, machine.inventory, and transfer.item facts are well-formed with valid recipe and slot data.", "None - MachineBridgeFactory.createProcessing() can bind a real executable bridge to a live block entity.", null));
        } else if (behaviorRequired) {
            findings.add(new CompatibilityFinding("block.behavior.required", CompatibilityFinding.Severity.WARNING, "behavior", "Block declares behavior requirements that Hydraulic cannot satisfy yet.", behaviorTag != null ? "Metadata or patch data flagged behavior tag '" + behaviorTag + "'." : "Metadata or patch data flagged behavior-dependent handling.", "Implement a behavior bridge or adapter for this block family.", null));
        }

        List<String> metadataSources = new ArrayList<>();
        if (mapping != null) {
            mapping.rules().stream().map(rule -> rule.sourcePath()).distinct().forEach(metadataSources::add);
        }

        Map<String, String> inventoryFacts = new LinkedHashMap<>(AnalyzerSupport.inventoryFacts(descriptor.registered(), descriptor.assetPresent(), mapping != null ? 1 : 0, patches.size()));
        inventoryFacts.putAll(behaviorFacts);
        inventoryFacts.put("behavior_required", Boolean.toString(behaviorRequired));
        if (Boolean.parseBoolean(inventoryFacts.getOrDefault("has_processing", "false"))
            || Boolean.parseBoolean(inventoryFacts.getOrDefault("has_inventory", "false"))) {
            inventoryFacts.put("critical_capabilities", "processing,inventory");
        }
        if (behaviorTag != null) {
            inventoryFacts.put("behavior_tag", behaviorTag);
        }

        List<String> behaviorBridgeRequirements = BehaviorFactExtractor.runtimeRequirements(inventoryFacts);

        if (AnalyzerSupport.hasCriticalFailure(inventoryFacts, supportResults)) {
            inventoryFacts.put("critical_failure", "true");
            inventoryFacts.put("critical_failure_reason", "machine processing or inventory runtime is unavailable");
        }

        return AnalyzerSupport.object(
            descriptor.javaIdentifier(),
            descriptor.kind(),
            descriptor.modId(),
            inventoryFacts,
            profile,
            supportResults,
            behaviorBridgeRequirements,
            new Confidence(mapping != null || !patches.isEmpty() ? (descriptor.assetPresent() ? 0.92D : 0.68D) : (descriptor.assetPresent() ? 0.78D : 0.35D), "Inventory-backed block analyzer with metadata and patch signals."),
            AnalyzerSupport.provenance(this.getClass().getSimpleName(), mapping != null || !patches.isEmpty(), patches, metadataSources),
            findings
        );
    }
}