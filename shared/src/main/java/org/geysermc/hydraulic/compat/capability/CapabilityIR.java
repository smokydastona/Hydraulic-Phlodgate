package org.geysermc.hydraulic.compat.capability;

import org.geysermc.hydraulic.compat.CompatibilityStatus;
import org.geysermc.hydraulic.compat.model.CompatibilityFinding;
import org.geysermc.hydraulic.compat.model.CompatibilityObject;
import org.geysermc.hydraulic.compat.model.SupportLevel;
import org.geysermc.hydraulic.compat.model.SupportResult;
import org.geysermc.hydraulic.compat.ir.CompiledCompatibilityPlan;
import org.geysermc.hydraulic.compat.runtime.RuntimeBridgeKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Universal Capability Intermediate Representation (IR).
 * Represents discovered, required, provided, and adapted capabilities for any content object.
 */
public record CapabilityIR(
    @NotNull String objectId,
    @NotNull String contentType,
    @NotNull SupportLevel overallLevel,
    @NotNull CompatibilityStatus overallStatus,
    int overallScore,
    @NotNull List<CapabilityDeclaration> declarations,
    @NotNull List<RuntimeBridgeKind> requiredBridges,
    @NotNull Map<String, String> facts
) {
    public CapabilityIR {
        declarations = List.copyOf(declarations);
        requiredBridges = List.copyOf(requiredBridges);
        facts = Collections.unmodifiableMap(new LinkedHashMap<>(facts));
    }

    public enum RequirementKind {
        MANDATORY,
        RECOMMENDED,
        OPTIONAL
    }

    public enum ComplianceStatus {
        PASS,
        PARTIAL,
        FAIL,
        NOT_APPLICABLE
    }

    public enum DegradationAction {
        NONE,
        APPROXIMATE,
        SIMPLIFY,
        SCRIPT,
        OMIT
    }

    public record CapabilityDeclaration(
        @NotNull Capability capability,
        @NotNull RequirementKind requirementKind,
        @NotNull SupportLevel supportLevel,
        @NotNull CompatibilityStatus compatibilityStatus,
        @NotNull ComplianceStatus complianceStatus,
        @NotNull List<RuntimeBridgeKind> requiredBridges,
        @NotNull DegradationAction degradationAction,
        @NotNull List<String> evidence,
        @NotNull List<String> diagnostics
    ) {
        public CapabilityDeclaration {
            requiredBridges = List.copyOf(requiredBridges);
            evidence = List.copyOf(evidence);
            diagnostics = List.copyOf(diagnostics);
        }

        public boolean isMandatory() {
            return this.requirementKind == RequirementKind.MANDATORY;
        }

        public boolean isSupported() {
            return this.complianceStatus == ComplianceStatus.PASS || this.complianceStatus == ComplianceStatus.PARTIAL;
        }

        public boolean isPassing() {
            return this.complianceStatus == ComplianceStatus.PASS;
        }
    }

    public static final class Canonical {
        public static final Capability BLOCK_ENTITY_DATA = new Capability(CapabilityDomain.STATE_DATA, "block_entity_data", "Block entity state serialization and sync.");
        public static final Capability MACHINE_INVENTORY = new Capability(CapabilityDomain.BEHAVIOR, "machine_inventory", "Machine inventory slot roles and sided capacity.");
        public static final Capability ITEM_TRANSFER = new Capability(CapabilityDomain.BEHAVIOR, "item_transfer", "Item insertion, extraction, and simulation.");
        public static final Capability FLUID_RUNTIME = new Capability(CapabilityDomain.BEHAVIOR, "fluid_runtime", "Fluid identity, tank capacity, and representation.");
        public static final Capability FLUID_TRANSFER = new Capability(CapabilityDomain.BEHAVIOR, "fluid_transfer", "Fluid fill, drain, and sided tank access.");
        public static final Capability ENERGY_TRANSFER = new Capability(CapabilityDomain.BEHAVIOR, "energy_transfer", "Energy buffer, receive/extract rates, and power network.");
        public static final Capability MENU_CONTAINER = new Capability(CapabilityDomain.INTERACTION, "menu_container", "Container menu slots, transactions, and fallback UI.");
        public static final Capability AUTOMATION_ACCESS = new Capability(CapabilityDomain.BEHAVIOR, "automation_access", "Sided automation, pipes, and routing filters.");
        public static final Capability MACHINE_PROCESSING = new Capability(CapabilityDomain.BEHAVIOR, "machine_processing", "Recipe processing, progress, and conversion cycles.");
        public static final Capability PRESENTATION = new Capability(CapabilityDomain.PRESENTATION, "presentation", "Block/item/entity geometry, models, and textures.");
        public static final Capability NETWORK_SYNC = new Capability(CapabilityDomain.BEHAVIOR, "network_sync", "Bidirectional state change events and packet synchronization.");
        public static final Capability ENTITY_INTERACTION = new Capability(CapabilityDomain.INTERACTION, "entity_interaction", "Entity right-click interaction, prompts, and mounting.");

        private Canonical() {
        }
    }

    public boolean hasCapability(@NotNull String capabilityName) {
        return this.declarations.stream().anyMatch(d -> d.capability().name().equalsIgnoreCase(capabilityName));
    }

    @Nullable
    public CapabilityDeclaration getDeclaration(@NotNull String capabilityName) {
        return this.declarations.stream()
            .filter(d -> d.capability().name().equalsIgnoreCase(capabilityName))
            .findFirst()
            .orElse(null);
    }

    @NotNull
    public List<CapabilityDeclaration> getDeclarationsInDomain(@NotNull CapabilityDomain domain) {
        return this.declarations.stream()
            .filter(d -> d.capability().domain() == domain)
            .toList();
    }

    @NotNull
    public List<CapabilityDeclaration> getMissingMandatoryDeclarations() {
        return this.declarations.stream()
            .filter(d -> d.isMandatory() && !d.isSupported())
            .toList();
    }

    public boolean isFullyCompliant() {
        return getMissingMandatoryDeclarations().isEmpty() && this.overallLevel != SupportLevel.UNSUPPORTED;
    }

    @NotNull
    public static CapabilityIR from(@NotNull CompatibilityObject object) {
        List<CapabilityDeclaration> declarations = new ArrayList<>();
        List<RuntimeBridgeKind> requiredBridges = RuntimeBridgeKind.resolve(object.runtimeRequirements());

        for (Map.Entry<String, SupportResult> entry : object.supportResults().entrySet()) {
            String domainKey = entry.getKey();
            SupportResult result = entry.getValue();
            CapabilityDomain domain = mapDomain(domainKey);

            RequirementKind reqKind = "behavior".equals(domainKey) && "true".equals(object.inventoryFacts().get("has_processing"))
                ? RequirementKind.MANDATORY
                : RequirementKind.RECOMMENDED;

            ComplianceStatus compStatus = switch (result.level()) {
                case NATIVE, AUTOMATIC, ADAPTED -> ComplianceStatus.PASS;
                case APPROXIMATED -> ComplianceStatus.PARTIAL;
                case VISUAL_ONLY -> "presentation".equals(domainKey) ? ComplianceStatus.PASS : ComplianceStatus.FAIL;
                case UNSUPPORTED -> ComplianceStatus.FAIL;
            };

            DegradationAction action = switch (result.level()) {
                case APPROXIMATED -> DegradationAction.APPROXIMATE;
                case VISUAL_ONLY -> DegradationAction.SIMPLIFY;
                case UNSUPPORTED -> DegradationAction.OMIT;
                default -> DegradationAction.NONE;
            };

            List<String> evidence = new ArrayList<>();
            evidence.add("domain:" + domainKey);
            evidence.add("level:" + result.level().name());
            evidence.add("status:" + result.status().name());

            List<String> diagnostics = object.findings().stream()
                .filter(f -> f.domain().equalsIgnoreCase(domainKey))
                .map(CompatibilityFinding::message)
                .toList();

            Capability cap = new Capability(domain, domainKey, "Evaluated compatibility for domain " + domainKey);
            declarations.add(new CapabilityDeclaration(
                cap,
                reqKind,
                result.level(),
                result.status(),
                compStatus,
                requiredBridges,
                action,
                evidence,
                diagnostics
            ));
        }

        return new CapabilityIR(
            object.javaIdentifier(),
            object.contentType(),
            object.overallLevel(),
            object.overallStatus(),
            object.overallScore(),
            declarations,
            requiredBridges,
            object.inventoryFacts()
        );
    }

    @NotNull
    public static CapabilityIR from(@NotNull CompiledCompatibilityPlan plan) {
        List<CapabilityDeclaration> declarations = new ArrayList<>();

        if (plan.allowsCustomRegistration()) {
            declarations.add(new CapabilityDeclaration(
                Canonical.PRESENTATION,
                RequirementKind.MANDATORY,
                SupportLevel.AUTOMATIC,
                CompatibilityStatus.COMPLETE,
                ComplianceStatus.PASS,
                List.of(),
                DegradationAction.NONE,
                List.of("allowsCustomRegistration=true"),
                List.of()
            ));
        }

        if (plan.requiresMenuBridge() || plan.hasMenuFallback()) {
            declarations.add(new CapabilityDeclaration(
                Canonical.MENU_CONTAINER,
                RequirementKind.RECOMMENDED,
                plan.hasMenuFallback() ? SupportLevel.ADAPTED : SupportLevel.UNSUPPORTED,
                plan.hasMenuFallback() ? CompatibilityStatus.COMPLETE : CompatibilityStatus.NONE,
                plan.hasMenuFallback() ? ComplianceStatus.PASS : ComplianceStatus.FAIL,
                List.of(RuntimeBridgeKind.MENU_CONTAINER),
                plan.hasMenuFallback() ? DegradationAction.APPROXIMATE : DegradationAction.OMIT,
                List.of("menuFallback=" + plan.menuFallbackContainerType()),
                List.of()
            ));
        }

        if (plan.requiresBlockEntityRuntime() || plan.hasBlockEntityPatch()) {
            declarations.add(new CapabilityDeclaration(
                Canonical.BLOCK_ENTITY_DATA,
                RequirementKind.RECOMMENDED,
                plan.hasBlockEntityPatch() ? SupportLevel.ADAPTED : SupportLevel.UNSUPPORTED,
                plan.hasBlockEntityPatch() ? CompatibilityStatus.COMPLETE : CompatibilityStatus.NONE,
                plan.hasBlockEntityPatch() ? ComplianceStatus.PASS : ComplianceStatus.FAIL,
                List.of(RuntimeBridgeKind.BLOCK_ENTITY_DATA),
                plan.hasBlockEntityPatch() ? DegradationAction.NONE : DegradationAction.OMIT,
                List.of("hasBlockEntityPatch=" + plan.hasBlockEntityPatch()),
                List.of()
            ));
        }

        if (plan.requiresFluidRuntime()) {
            declarations.add(new CapabilityDeclaration(
                Canonical.FLUID_TRANSFER,
                RequirementKind.RECOMMENDED,
                SupportLevel.ADAPTED,
                CompatibilityStatus.PARTIAL,
                ComplianceStatus.PARTIAL,
                List.of(RuntimeBridgeKind.FLUID_TRANSFER),
                DegradationAction.APPROXIMATE,
                List.of("requiresFluidRuntime=true"),
                List.of()
            ));
        }

        if (plan.requiresRuntimeBridge(RuntimeBridgeKind.ITEM_TRANSFER)) {
            declarations.add(new CapabilityDeclaration(
                Canonical.ITEM_TRANSFER,
                RequirementKind.MANDATORY,
                SupportLevel.ADAPTED,
                CompatibilityStatus.COMPLETE,
                ComplianceStatus.PASS,
                List.of(RuntimeBridgeKind.ITEM_TRANSFER),
                DegradationAction.NONE,
                List.of("requiresRuntimeBridge=ITEM_TRANSFER"),
                List.of()
            ));
        }

        if (plan.requiresRuntimeBridge(RuntimeBridgeKind.MACHINE_BEHAVIOR)) {
            declarations.add(new CapabilityDeclaration(
                Canonical.MACHINE_PROCESSING,
                RequirementKind.MANDATORY,
                SupportLevel.ADAPTED,
                CompatibilityStatus.COMPLETE,
                ComplianceStatus.PASS,
                List.of(RuntimeBridgeKind.MACHINE_BEHAVIOR),
                DegradationAction.NONE,
                List.of("requiresRuntimeBridge=MACHINE_BEHAVIOR"),
                List.of()
            ));
        }

        return new CapabilityIR(
            plan.javaIdentifier(),
            plan.contentType(),
            plan.overallLevel(),
            plan.overallStatus(),
            plan.overallScore(),
            declarations,
            plan.runtimeBridgeKinds(),
            plan.inventoryFacts()
        );
    }

    private static CapabilityDomain mapDomain(String domainKey) {
        return switch (domainKey.toLowerCase()) {
            case "content" -> CapabilityDomain.CONTENT;
            case "presentation" -> CapabilityDomain.PRESENTATION;
            case "state_data", "state" -> CapabilityDomain.STATE_DATA;
            case "interaction" -> CapabilityDomain.INTERACTION;
            case "behavior" -> CapabilityDomain.BEHAVIOR;
            default -> CapabilityDomain.BEHAVIOR;
        };
    }
}
