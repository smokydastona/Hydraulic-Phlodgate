package org.geysermc.hydraulic.compat.analysis;

import org.geysermc.hydraulic.compat.CompatibilityStatus;
import org.geysermc.hydraulic.compat.adapter.AdapterBinding;
import org.geysermc.hydraulic.compat.adapter.AdapterFeature;
import org.geysermc.hydraulic.compat.adapter.CapabilityAdapterRegistry;
import org.geysermc.hydraulic.compat.capability.Capability;
import org.geysermc.hydraulic.compat.capability.CapabilityDomain;
import org.geysermc.hydraulic.compat.capability.CapabilityProfile;
import org.geysermc.hydraulic.compat.capability.CapabilityRequirement;
import org.geysermc.hydraulic.compat.capability.CapabilityResult;
import org.geysermc.hydraulic.compat.model.CompatibilityFinding;
import org.geysermc.hydraulic.compat.model.CompatibilityObject;
import org.geysermc.hydraulic.compat.model.Confidence;
import org.geysermc.hydraulic.compat.model.Provenance;
import org.geysermc.hydraulic.compat.model.ImplementationMaturity;
import org.geysermc.hydraulic.compat.model.SupportLevel;
import org.geysermc.hydraulic.compat.model.SupportResult;
import org.geysermc.hydraulic.compat.mapping.ContentPatch;
import org.geysermc.hydraulic.compat.runtime.MachineBridgeFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

final class AnalyzerSupport {
    private AnalyzerSupport() {
    }

    @NotNull
    static Capability capability(@NotNull CapabilityDomain domain, @NotNull String name, @NotNull String description) {
        return new Capability(domain, name, description);
    }

    @NotNull
    static CapabilityRequirement required(@NotNull Capability capability) {
        return new CapabilityRequirement(capability, true);
    }

    @NotNull
    static CapabilityRequirement optional(@NotNull Capability capability) {
        return new CapabilityRequirement(capability, false);
    }

    @NotNull
    static CapabilityResult result(@NotNull Capability capability, boolean supported, String details) {
        return new CapabilityResult(capability, supported, details);
    }

    @NotNull
    static SupportResult support(@NotNull String scope, @NotNull SupportLevel level, @NotNull List<CapabilityResult> results, @NotNull List<String> notes) {
        List<String> supportedCapabilities = new ArrayList<>();
        List<String> missingCapabilities = new ArrayList<>();
        for (CapabilityResult result : results) {
            if (result.supported()) {
                supportedCapabilities.add(result.capability().name());
            } else {
                missingCapabilities.add(result.capability().name());
            }
        }

        Integer score = results.isEmpty() ? null : (int) Math.round((supportedCapabilities.size() * 100D) / results.size());
        CompatibilityStatus status;
        if (results.isEmpty()) {
            status = CompatibilityStatus.UNKNOWN;
        } else if (supportedCapabilities.isEmpty()) {
            status = CompatibilityStatus.NONE;
        } else if (missingCapabilities.isEmpty()) {
            status = CompatibilityStatus.COMPLETE;
        } else {
            status = CompatibilityStatus.PARTIAL;
        }

        return new SupportResult(scope, level, status, score, supportedCapabilities, missingCapabilities, notes);
    }

    @NotNull
    static CompatibilityObject object(
        @NotNull String javaIdentifier,
        @NotNull String contentType,
        @NotNull String modId,
        @NotNull Map<String, String> inventoryFacts,
        @NotNull CapabilityProfile capabilityProfile,
        @NotNull Map<String, SupportResult> supportResults,
        @NotNull Confidence confidence,
        @NotNull List<Provenance> provenance,
        @NotNull List<CompatibilityFinding> findings
    ) {
        return object(javaIdentifier, contentType, modId, inventoryFacts, capabilityProfile, supportResults, null, confidence, provenance, findings);
    }

    @NotNull
    static CompatibilityObject object(
        @NotNull String javaIdentifier,
        @NotNull String contentType,
        @NotNull String modId,
        @NotNull Map<String, String> inventoryFacts,
        @NotNull CapabilityProfile capabilityProfile,
        @NotNull Map<String, SupportResult> supportResults,
        @Nullable List<String> customRuntimeRequirements,
        @NotNull Confidence confidence,
        @NotNull List<Provenance> provenance,
        @NotNull List<CompatibilityFinding> findings
    ) {
        boolean criticalFailure = hasCriticalFailure(inventoryFacts, supportResults);
        SupportLevel overallLevel = criticalFailure ? SupportLevel.VISUAL_ONLY : overallLevel(supportResults);
        CompatibilityStatus overallStatus = criticalFailure ? CompatibilityStatus.PARTIAL : overallStatus(supportResults);
        int overallScore = criticalFailure ? 0 : overallScore(supportResults);
        CompatibilityObject candidate = new CompatibilityObject(javaIdentifier, contentType, modId, inventoryFacts, capabilityProfile, List.of(), List.of(), supportResults, overallLevel, overallStatus, overallScore, confidence, provenance, findings);
        List<AdapterBinding> adapterBindings = CapabilityAdapterRegistry.bindings(candidate);
        List<String> runtimeRequirements = runtimeRequirements(contentType, inventoryFacts, supportResults, adapterBindings);
        if (customRuntimeRequirements != null && !customRuntimeRequirements.isEmpty()) {
            LinkedHashSet<String> merged = new LinkedHashSet<>(runtimeRequirements);
            merged.addAll(customRuntimeRequirements);
            runtimeRequirements = List.copyOf(merged);
        }
        return new CompatibilityObject(javaIdentifier, contentType, modId, inventoryFacts, capabilityProfile, adapterBindings, runtimeRequirements, supportResults, overallLevel, overallStatus, overallScore, confidence, provenance, findings, ImplementationMaturity.ARCHITECTURE_IMPLEMENTED);
    }

    @NotNull
    private static List<String> runtimeRequirements(
        @NotNull String contentType,
        @NotNull Map<String, String> inventoryFacts,
        @NotNull Map<String, SupportResult> supportResults,
        @NotNull List<AdapterBinding> adapterBindings
    ) {
        List<String> requirements = new ArrayList<>();

        switch (contentType) {
            case "block" -> {
                if (!hasFeature(adapterBindings, AdapterFeature.BLOCK_PLACEMENT)) {
                    maybeAdd(requirements, supportResults.get("interaction"), "block_placement_bridge");
                }
                if (!hasFeature(adapterBindings, AdapterFeature.BLOCK_CREATIVE_EXPOSURE)) {
                    maybeAdd(requirements, supportResults.get("behavior"), "block_behavior_bridge");
                }
            }
            case "item" -> {
                if (!hasFeature(adapterBindings, AdapterFeature.CUSTOM_ITEM_REGISTRATION)) {
                    maybeAdd(requirements, supportResults.get("presentation"), "item_registration_bridge");
                }
                if (Boolean.parseBoolean(inventoryFacts.getOrDefault("behavior_required", "false"))) {
                    maybeAdd(requirements, supportResults.get("behavior"), "item_behavior_bridge");
                }
            }
            case "entity" -> {
                maybeAdd(requirements, supportResults.get("interaction"), "entity_interaction_bridge");
                maybeAdd(requirements, supportResults.get("behavior"), "entity_behavior_bridge");
            }
            case "menu" -> {
                maybeAdd(requirements, supportResults.get("interaction"), "container_bridge");
                maybeAdd(requirements, supportResults.get("behavior"), "menu_behavior_bridge");
            }
            case "block_entity" -> {
                maybeAdd(requirements, supportResults.get("state_data"), "block_entity_data_bridge");
                maybeAdd(requirements, supportResults.get("interaction"), "block_entity_interaction_bridge");
                maybeAdd(requirements, supportResults.get("behavior"), "block_entity_behavior_bridge");
            }
            case "fluid" -> {
                maybeAdd(requirements, supportResults.get("presentation"), "fluid_translator");
                maybeAdd(requirements, supportResults.get("behavior"), "fluid_runtime_bridge");
            }
            default -> {
            }
        }

        if (Boolean.parseBoolean(inventoryFacts.getOrDefault("custom_networking", "false"))) {
            requirements.add("network_protocol_bridge");
        }

        return List.copyOf(requirements);
    }

    private static void maybeAdd(@NotNull List<String> requirements, SupportResult result, @NotNull String requirement) {
        if (result != null && result.level() != SupportLevel.NATIVE && result.level() != SupportLevel.AUTOMATIC && result.level() != SupportLevel.ADAPTED) {
            requirements.add(requirement);
        }
    }

    private static boolean hasFeature(@NotNull List<AdapterBinding> bindings, @NotNull AdapterFeature feature) {
        return bindings.stream().anyMatch(binding -> binding.feature() == feature);
    }

    @NotNull
    static Map<String, String> inventoryFacts(boolean registered, boolean assetPresent, int metadataCount, int patchCount) {
        Map<String, String> facts = new LinkedHashMap<>();
        facts.put("registered", Boolean.toString(registered));
        facts.put("asset_present", Boolean.toString(assetPresent));
        facts.put("metadata_count", Integer.toString(metadataCount));
        facts.put("patch_count", Integer.toString(patchCount));
        return facts;
    }

    @NotNull
    static List<Provenance> provenance(@NotNull String analyzerName, boolean overridden, @NotNull List<ContentPatch> patches, @NotNull List<String> metadataSources) {
        List<Provenance> provenance = new ArrayList<>();
        provenance.add(new Provenance("inventory", analyzerName, null, overridden));
        for (String source : metadataSources) {
            provenance.add(new Provenance("metadata-mapping", analyzerName, source, true));
        }
        for (ContentPatch patch : patches) {
            provenance.add(new Provenance("metadata-patch", analyzerName, patch.sourcePath(), true));
        }
        return List.copyOf(provenance);
    }

    @NotNull
    static SupportLevel overallLevel(@NotNull Map<String, SupportResult> supportResults) {
        boolean anyUnsupported = false;
        boolean anyVisualOnly = false;
        boolean anyApproximated = false;
        boolean anyAdapted = false;
        boolean allNative = !supportResults.isEmpty();

        for (SupportResult result : supportResults.values()) {
            anyUnsupported |= result.level() == SupportLevel.UNSUPPORTED;
            anyVisualOnly |= result.level() == SupportLevel.VISUAL_ONLY;
            anyApproximated |= result.level() == SupportLevel.APPROXIMATED;
            anyAdapted |= result.level() == SupportLevel.ADAPTED;
            allNative &= result.level() == SupportLevel.NATIVE;
        }

        if (anyUnsupported) {
            return SupportLevel.UNSUPPORTED;
        }
        if (anyVisualOnly) {
            return SupportLevel.VISUAL_ONLY;
        }
        if (anyApproximated) {
            return SupportLevel.APPROXIMATED;
        }
        if (anyAdapted) {
            return SupportLevel.ADAPTED;
        }
        if (allNative) {
            return SupportLevel.NATIVE;
        }
        return SupportLevel.AUTOMATIC;
    }

    @NotNull
    static CompatibilityStatus overallStatus(@NotNull Map<String, SupportResult> supportResults) {
        boolean hasComplete = false;
        boolean hasPartial = false;
        boolean hasNone = false;
        for (SupportResult result : supportResults.values()) {
            hasComplete |= result.status() == CompatibilityStatus.COMPLETE;
            hasPartial |= result.status() == CompatibilityStatus.PARTIAL;
            hasNone |= result.status() == CompatibilityStatus.NONE;
        }
        if (hasPartial || hasNone) {
            return CompatibilityStatus.PARTIAL;
        }
        if (hasComplete) {
            return CompatibilityStatus.COMPLETE;
        }
        return CompatibilityStatus.UNKNOWN;
    }

    static int overallScore(@NotNull Map<String, SupportResult> supportResults) {
        int scored = 0;
        int total = 0;
        for (SupportResult result : supportResults.values()) {
            if (result.scorePercent() != null) {
                scored += result.scorePercent();
                total++;
            }
        }
        return total == 0 ? 0 : (int) Math.round(scored / (double) total);
    }

    static boolean hasCriticalFailure(
        @NotNull Map<String, String> inventoryFacts,
        @NotNull Map<String, SupportResult> supportResults
    ) {
        SupportResult behavior = supportResults.get("behavior");
        if (behavior == null || behavior.level() == SupportLevel.NATIVE
            || behavior.level() == SupportLevel.AUTOMATIC || behavior.level() == SupportLevel.ADAPTED) {
            return false;
        }

        return booleanFact(inventoryFacts, "has_processing") || booleanFact(inventoryFacts, "has_inventory");
    }

    private static boolean booleanFact(@NotNull Map<String, String> inventoryFacts, @NotNull String key) {
        return Boolean.parseBoolean(inventoryFacts.getOrDefault(key, "false"));
    }

    enum MachineBehaviorReadiness {
        NOT_APPLICABLE,
        UNSUPPORTED,
        INSUFFICIENT,
        EXECUTABLE
    }

    @NotNull
    static MachineBehaviorReadiness machineBehaviorReadiness(@NotNull Map<String, String> facts) {
        boolean hasProcessing = booleanFact(facts, "has_processing");
        boolean hasInventory = booleanFact(facts, "has_inventory");
        if (!hasProcessing && !hasInventory) {
            return MachineBehaviorReadiness.NOT_APPLICABLE;
        }
        if (!hasProcessing || !hasInventory) {
            return MachineBehaviorReadiness.UNSUPPORTED;
        }
        if (!booleanFact(facts, "can_insert") || !booleanFact(facts, "can_extract")) {
            return MachineBehaviorReadiness.INSUFFICIENT;
        }
        if (!MachineBridgeFactory.hasExecutableProcessingContract(facts)) {
            return MachineBehaviorReadiness.INSUFFICIENT;
        }
        return MachineBehaviorReadiness.EXECUTABLE;
    }
}
