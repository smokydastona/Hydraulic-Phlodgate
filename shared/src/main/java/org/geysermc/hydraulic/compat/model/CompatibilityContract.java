package org.geysermc.hydraulic.compat.model;

import org.geysermc.hydraulic.compat.CompatibilityStatus;
import org.geysermc.hydraulic.compat.ir.CompiledCompatibilityPlan;
import org.geysermc.hydraulic.compat.runtime.RuntimeBridgeKind;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed execution contract derived from analyzer output for one Java object.
 * The contract is the boundary between compatibility analysis and runtime planning.
 */
public record CompatibilityContract(
    @NotNull String contentType,
    @NotNull String javaIdentifier,
    @NotNull SupportLevel overallLevel,
    @NotNull CompatibilityStatus overallStatus,
    int overallScore,
    @NotNull Map<Domain, DomainContract> domains,
    @NotNull List<RuntimeBridgeKind> requiredBridges,
    boolean executable,
    @NotNull CapabilityExecutionStatus executionStatus
) {
    public CompatibilityContract {
        domains = Map.copyOf(new LinkedHashMap<>(domains));
        requiredBridges = List.copyOf(requiredBridges);
    }

    @NotNull
    public static CompatibilityContract from(@NotNull CompatibilityObject object) {
        Map<Domain, DomainContract> domains = new LinkedHashMap<>();
        for (Map.Entry<String, SupportResult> entry : object.supportResults().entrySet()) {
            Domain domain = Domain.fromScope(entry.getKey());
            if (domain == null) {
                continue;
            }
            SupportResult result = entry.getValue();
            domains.put(domain, DomainContract.from(result));
        }

        if (Boolean.parseBoolean(object.inventoryFacts().getOrDefault("custom_networking", "false"))) {
            domains.put(Domain.NETWORK, new DomainContract(
                SupportLevel.UNSUPPORTED,
                CompatibilityStatus.NONE,
                Action.OMIT,
                List.of(),
                List.of("custom_networking"),
                List.of("Custom networking was detected; no generic protocol bridge is available.")
            ));
        }
        if (Boolean.parseBoolean(object.inventoryFacts().getOrDefault("custom_rendering", "false"))) {
            DomainContract existingPresentation = domains.get(Domain.PRESENTATION);
            List<String> supported = existingPresentation == null ? List.of() : existingPresentation.supportedCapabilities();
            List<String> missing = new java.util.ArrayList<>(existingPresentation == null ? List.of() : existingPresentation.missingCapabilities());
            missing.add("custom_rendering");
            List<String> notes = new java.util.ArrayList<>(existingPresentation == null ? List.of() : existingPresentation.notes());
            notes.add("Custom rendering was detected; presentation may require approximation.");
            domains.put(Domain.PRESENTATION, new DomainContract(
                SupportLevel.APPROXIMATED,
                CompatibilityStatus.PARTIAL,
                Action.APPROXIMATE,
                supported,
                missing,
                notes
            ));
        }

        List<RuntimeBridgeKind> requiredBridges = RuntimeBridgeKind.resolve(object.runtimeRequirements());
        boolean executable = object.overallLevel() != SupportLevel.UNSUPPORTED
            && object.overallLevel() != SupportLevel.VISUAL_ONLY
            && object.supportResults().values().stream().noneMatch(result -> result.level() == SupportLevel.UNSUPPORTED)
            && !"true".equals(object.inventoryFacts().get("critical_failure"));
        return new CompatibilityContract(
            object.contentType(),
            object.javaIdentifier(),
            object.overallLevel(),
            object.overallStatus(),
            object.overallScore(),
            domains,
            requiredBridges,
            executable,
            executionStatus(object.overallLevel(), requiredBridges, executable, false)
        );
    }

    @NotNull
    public static CompatibilityContract from(@NotNull CompiledCompatibilityPlan plan) {
        Map<Domain, DomainContract> domains = new LinkedHashMap<>();
        domains.put(Domain.CONTENT, domain(
            plan.allowsCustomRegistration() ? SupportLevel.AUTOMATIC : SupportLevel.VISUAL_ONLY,
            plan.allowsCustomRegistration() ? CompatibilityStatus.COMPLETE : CompatibilityStatus.PARTIAL,
            plan.allowsCustomRegistration() ? List.of("registration") : List.of("registration"),
            plan.allowsCustomRegistration() ? List.of() : List.of("custom_registration"),
            "Compiled registration decision is authoritative for runtime consumers."
        ));
        if (plan.supportsBlockItemTextureFallback() || plan.supportsWearablePresentation() || plan.supportsAttachablePresentation()) {
            domains.put(Domain.PRESENTATION, domain(
                SupportLevel.AUTOMATIC,
                CompatibilityStatus.COMPLETE,
                List.of("generated_presentation"),
                List.of(),
                "Presentation is backed by compiled runtime flags."
            ));
        }
        if (plan.supportsBlockPlacement() || plan.requiresMenuBridge()) {
            domains.put(Domain.INTERACTION, domain(
                plan.requiresMenuBridge() ? SupportLevel.ADAPTED : SupportLevel.AUTOMATIC,
                plan.requiresMenuBridge() ? CompatibilityStatus.PARTIAL : CompatibilityStatus.COMPLETE,
                List.of(plan.supportsBlockPlacement() ? "placement" : "menu_fallback"),
                plan.requiresMenuBridge() ? List.of("menu_behavior") : List.of(),
                "Interaction is limited to the compiled bridge capabilities."
            ));
        }
        if (plan.behaviorLevel() != null) {
            domains.put(Domain.BEHAVIOR, domain(
                plan.behaviorLevel(),
                plan.behaviorLevel() == SupportLevel.UNSUPPORTED ? CompatibilityStatus.NONE : CompatibilityStatus.PARTIAL,
                List.of(),
                plan.behaviorLevel() == SupportLevel.UNSUPPORTED ? List.of("behavior_runtime") : List.of(),
                "Behavior status is taken from the compiled plan."
            ));
        }
        boolean executable = plan.overallLevel() != SupportLevel.UNSUPPORTED
            && plan.overallLevel() != SupportLevel.VISUAL_ONLY
            && !"true".equals(plan.inventoryFacts().get("critical_failure"));
        return new CompatibilityContract(
            plan.contentType(),
            plan.javaIdentifier(),
            plan.overallLevel(),
            plan.overallStatus(),
            plan.overallScore(),
            domains,
            plan.runtimeBridgeKinds(),
            executable,
            executionStatus(plan.overallLevel(), plan.runtimeBridgeKinds(), executable, true)
        );
    }

    @NotNull
    private static CapabilityExecutionStatus executionStatus(
        @NotNull SupportLevel level,
        @NotNull List<RuntimeBridgeKind> bridges,
        boolean executable,
        boolean compiled
    ) {
        if (level == SupportLevel.UNSUPPORTED || level == SupportLevel.VISUAL_ONLY) {
            return CapabilityExecutionStatus.ANALYZED;
        }
        if (executable && compiled) {
            return CapabilityExecutionStatus.EXECUTABLE;
        }
        if (!compiled) {
            return CapabilityExecutionStatus.ANALYZED;
        }
        if (compiled || !bridges.isEmpty()) {
            return CapabilityExecutionStatus.COMPILED;
        }
        return CapabilityExecutionStatus.TRANSLATABLE;
    }

    @NotNull
    private static DomainContract domain(
        @NotNull SupportLevel level,
        @NotNull CompatibilityStatus status,
        @NotNull List<String> supported,
        @NotNull List<String> missing,
        @NotNull String note
    ) {
        return new DomainContract(level, status, DomainContract.action(level), supported, missing, List.of(note));
    }

    public enum Domain {
        CONTENT,
        PRESENTATION,
        STATE,
        INTERACTION,
        BEHAVIOR,
        NETWORK;

        private static Domain fromScope(@NotNull String scope) {
            return switch (scope) {
                case "content" -> CONTENT;
                case "presentation" -> PRESENTATION;
                case "state_data" -> STATE;
                case "interaction" -> INTERACTION;
                case "behavior" -> BEHAVIOR;
                case "network" -> NETWORK;
                default -> null;
            };
        }
    }

    public record DomainContract(
        @NotNull SupportLevel level,
        @NotNull CompatibilityStatus status,
        @NotNull Action action,
        @NotNull List<String> supportedCapabilities,
        @NotNull List<String> missingCapabilities,
        @NotNull List<String> notes
    ) {
        public DomainContract {
            supportedCapabilities = List.copyOf(supportedCapabilities);
            missingCapabilities = List.copyOf(missingCapabilities);
            notes = List.copyOf(notes);
        }

        @NotNull
        private static DomainContract from(@NotNull SupportResult result) {
            return new DomainContract(
                result.level(),
                result.status(),
                Action.from(result),
                result.supportedCapabilities(),
                result.missingCapabilities(),
                result.notes()
            );
        }

        @NotNull
        private static Action action(@NotNull SupportLevel level) {
            return Action.from(level);
        }
    }

    public enum Action {
        NATIVE,
        ADAPT,
        APPROXIMATE,
        VISUAL_ONLY,
        OMIT;

        @NotNull
        private static Action from(@NotNull SupportResult result) {
            return from(result.level());
        }

        @NotNull
        private static Action from(@NotNull SupportLevel level) {
            return switch (level) {
                case NATIVE, AUTOMATIC -> NATIVE;
                case ADAPTED -> ADAPT;
                case APPROXIMATED -> APPROXIMATE;
                case VISUAL_ONLY -> VISUAL_ONLY;
                case UNSUPPORTED -> OMIT;
            };
        }
    }
}
