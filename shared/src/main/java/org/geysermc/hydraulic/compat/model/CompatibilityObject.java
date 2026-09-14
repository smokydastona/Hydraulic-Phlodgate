package org.geysermc.hydraulic.compat.model;

import org.geysermc.hydraulic.compat.CompatibilityStatus;
import org.geysermc.hydraulic.compat.adapter.AdapterBinding;
import org.geysermc.hydraulic.compat.capability.CapabilityProfile;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CompatibilityObject(
    @NotNull String javaIdentifier,
    @NotNull String contentType,
    @NotNull String modId,
    @NotNull Map<String, String> inventoryFacts,
    @NotNull CapabilityProfile capabilityProfile,
    @NotNull List<AdapterBinding> adapterBindings,
    @NotNull List<String> runtimeRequirements,
    @NotNull Map<String, SupportResult> supportResults,
    @NotNull SupportLevel overallLevel,
    @NotNull CompatibilityStatus overallStatus,
    int overallScore,
    @NotNull Confidence confidence,
    @NotNull List<Provenance> provenance,
    @NotNull List<CompatibilityFinding> findings,
    @NotNull ImplementationMaturity implementationMaturity
) {
    public CompatibilityObject(
        @NotNull String javaIdentifier,
        @NotNull String contentType,
        @NotNull String modId,
        @NotNull Map<String, String> inventoryFacts,
        @NotNull CapabilityProfile capabilityProfile,
        @NotNull List<AdapterBinding> adapterBindings,
        @NotNull List<String> runtimeRequirements,
        @NotNull Map<String, SupportResult> supportResults,
        @NotNull SupportLevel overallLevel,
        @NotNull CompatibilityStatus overallStatus,
        int overallScore,
        @NotNull Confidence confidence,
        @NotNull List<Provenance> provenance,
        @NotNull List<CompatibilityFinding> findings
    ) {
        this(javaIdentifier, contentType, modId, inventoryFacts, capabilityProfile, adapterBindings,
            runtimeRequirements, supportResults, overallLevel, overallStatus, overallScore,
            confidence, provenance, findings, ImplementationMaturity.UNKNOWN);
    }

    public CompatibilityObject {
        if (implementationMaturity == null) {
            implementationMaturity = ImplementationMaturity.UNKNOWN;
        }
        inventoryFacts = Collections.unmodifiableMap(new LinkedHashMap<>(inventoryFacts));
        adapterBindings = List.copyOf(adapterBindings);
        runtimeRequirements = List.copyOf(runtimeRequirements);
        supportResults = Collections.unmodifiableMap(new LinkedHashMap<>(supportResults));
        provenance = List.copyOf(provenance);
        findings = List.copyOf(findings);
    }

    @NotNull
    public CompatibilityContract contract() {
        return CompatibilityContract.from(this);
    }

    @NotNull
    public CompatibilityObject withInventoryFacts(@NotNull Map<String, String> updatedFacts) {
        return new CompatibilityObject(
            this.javaIdentifier,
            this.contentType,
            this.modId,
            updatedFacts,
            this.capabilityProfile,
            this.adapterBindings,
            this.runtimeRequirements,
            this.supportResults,
            this.overallLevel,
            this.overallStatus,
            this.overallScore,
            this.confidence,
            this.provenance,
            this.findings,
            this.implementationMaturity
        );
    }

    @NotNull
    public CompatibilityObject withImplementationMaturity(@NotNull ImplementationMaturity maturity) {
        return new CompatibilityObject(
            this.javaIdentifier,
            this.contentType,
            this.modId,
            this.inventoryFacts,
            this.capabilityProfile,
            this.adapterBindings,
            this.runtimeRequirements,
            this.supportResults,
            this.overallLevel,
            this.overallStatus,
            this.overallScore,
            this.confidence,
            this.provenance,
            this.findings,
            maturity
        );
    }
}