package org.geysermc.hydraulic.compat.discovery;

import net.minecraft.resources.Identifier;
import org.geysermc.hydraulic.compat.CompatibilityStatus;
import org.geysermc.hydraulic.compat.adapter.AdapterBinding;
import org.geysermc.hydraulic.compat.adapter.AdapterFeature;
import org.geysermc.hydraulic.compat.ir.CompiledCompatibilityPlan;
import org.geysermc.hydraulic.compat.model.Confidence;
import org.geysermc.hydraulic.compat.model.SupportLevel;
import org.geysermc.hydraulic.compat.runtime.RuntimeBridgeKind;
import org.geysermc.hydraulic.compat.runtime.RuntimeDispatchTable;
import org.geysermc.hydraulic.compat.runtime.TransferBridgeFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the dynamic lifecycle of machine block entities on the live server.
 * When an unmapped legacy or modern block entity ticks or loads, this manager
 * discovers its capability contracts via {@link SemanticDiscoveryEngine} and
 * compiles a runtime plan into {@link RuntimeDispatchTable}.
 */
public final class DynamicMachineLifecycleManager {
    private final RuntimeDispatchTable dispatchTable;
    private final Map<Identifier, CompiledCompatibilityPlan> dynamicPlans = new ConcurrentHashMap<>();
    private final Map<Identifier, CompiledCompatibilityPlan> basePlans = new ConcurrentHashMap<>();
    private final Set<Identifier> initializedIdentifiers = ConcurrentHashMap.newKeySet();

    public DynamicMachineLifecycleManager(@NotNull RuntimeDispatchTable dispatchTable) {
        this.dispatchTable = Objects.requireNonNull(dispatchTable, "dispatchTable");
    }

    /**
     * Inspects a live machine block entity and dynamically compiles a runtime plan if not already registered.
     */
    @NotNull
    public synchronized CompiledCompatibilityPlan registerAndCompile(
        @NotNull Identifier identifier,
        @NotNull Object runtimeBlockEntity,
        @NotNull Map<String, String> initialFacts
    ) {
        Objects.requireNonNull(identifier, "identifier");
        Objects.requireNonNull(runtimeBlockEntity, "runtimeBlockEntity");

        if (initializedIdentifiers.add(identifier)) {
            CompiledCompatibilityPlan existing = dispatchTable.block(identifier);
            if (existing != null) {
                basePlans.put(identifier, existing);
            }
        }
        CompiledCompatibilityPlan basePlan = basePlans.get(identifier);

        SemanticDiscoveryEngine.DiscoveredSemanticProfile profile = SemanticDiscoveryEngine.discoverRuntimeObject(
            identifier,
            runtimeBlockEntity,
            initialFacts
        );

        CompiledCompatibilityPlan candidate = buildPlanFromProfile(identifier, profile, null);
        CompiledCompatibilityPlan discoveredPlan = buildPlanFromProfile(
            identifier,
            profile,
            validateExecutableBridges(runtimeBlockEntity, candidate)
        );
        CompiledCompatibilityPlan dynamicPlan = basePlan == null ? discoveredPlan : mergePlan(basePlan, discoveredPlan);
        dynamicPlans.put(identifier, dynamicPlan);
        dispatchTable.registerDynamicPlan(dynamicPlan);
        return dynamicPlan;
    }

    @NotNull
    private static CompiledCompatibilityPlan mergePlan(
        @NotNull CompiledCompatibilityPlan base,
        @NotNull CompiledCompatibilityPlan discovered
    ) {
        List<AdapterBinding> adapters = new ArrayList<>(base.adapterBindings());
        for (AdapterBinding adapter : discovered.adapterBindings()) {
            if (adapters.stream().noneMatch(existing -> existing.adapterId().equals(adapter.adapterId()))) {
                adapters.add(adapter);
            }
        }
        List<String> requirements = new ArrayList<>(base.runtimeRequirements());
        discovered.runtimeRequirements().stream().filter(requirement -> !requirements.contains(requirement)).forEach(requirements::add);
        List<RuntimeBridgeKind> bridgeKinds = new ArrayList<>(base.runtimeBridgeKinds());
        discovered.runtimeBridgeKinds().stream().filter(kind -> !bridgeKinds.contains(kind)).forEach(bridgeKinds::add);
        Map<String, String> facts = new java.util.LinkedHashMap<>(discovered.inventoryFacts());
        facts.putAll(base.inventoryFacts());

        return new CompiledCompatibilityPlan(
            base.modId(), base.contentType(), base.javaIdentifier(), base.resolvedIdentifier(),
            base.overallLevel(), base.overallStatus(), base.overallScore(), base.confidence(),
            adapters, requirements, bridgeKinds, facts,
            base.allowsCreativeExposure(), base.creativeExposureReason(), base.allowsCustomRegistration(),
            base.customRegistrationReason(), base.supportsBlockItemTextureFallback(), base.supportsBlockPlacement(),
            base.supportsWearablePresentation(), base.supportsAttachablePresentation(), base.requiresMenuBridge(),
            base.menuFallbackContainerType(), base.interactionPrompt(), base.menuRuntimeRequirements(),
            base.blockEntityRuntimeRequirements(), base.fluidRuntimeRequirements(), base.blockEntityPatchTemplate(),
            base.requiresBlockEntityRuntime(), base.requiresFluidRuntime(), base.behaviorLevel(), base.behaviorTag(),
            base.corpusEvidence()
        );
    }

    @Nullable
    public CompiledCompatibilityPlan getDynamicPlan(@NotNull Identifier identifier) {
        return dynamicPlans.get(identifier);
    }

    public int dynamicPlanCount() {
        return dynamicPlans.size();
    }

    @NotNull
    private static CompiledCompatibilityPlan buildPlanFromProfile(
        @NotNull Identifier identifier,
        @NotNull SemanticDiscoveryEngine.DiscoveredSemanticProfile profile,
        @Nullable List<RuntimeBridgeKind> executableBridges
    ) {
        Map<String, String> facts = profile.facts();
        List<RuntimeBridgeKind> bridgeKinds = new ArrayList<>();
        List<String> requirements = new ArrayList<>();

        if (supports(executableBridges, RuntimeBridgeKind.ITEM_TRANSFER)
            && ("true".equals(facts.get("has_inventory")) || "true".equals(facts.get("can_insert")) || "true".equals(facts.get("can_extract")))) {
            bridgeKinds.add(RuntimeBridgeKind.ITEM_TRANSFER);
            requirements.add("item_transfer_bridge");
            bridgeKinds.add(RuntimeBridgeKind.MACHINE_INVENTORY);
            requirements.add("machine_inventory_bridge");
        }
        if (supports(executableBridges, RuntimeBridgeKind.FLUID_TRANSFER)
            && ("true".equals(facts.get("has_fluid"))
                || "true".equals(facts.get("can_insert_fluid"))
                || "true".equals(facts.get("can_extract_fluid")))) {
            bridgeKinds.add(RuntimeBridgeKind.FLUID_TRANSFER);
            requirements.add("fluid_transfer_bridge");
        }
        if (supports(executableBridges, RuntimeBridgeKind.ENERGY_TRANSFER)
            && ("true".equals(facts.get("has_energy")) || "true".equals(facts.get("can_receive_energy")) || "true".equals(facts.get("can_provide_energy")))) {
            bridgeKinds.add(RuntimeBridgeKind.ENERGY_TRANSFER);
            requirements.add("energy_transfer_bridge");
        }
        if (supports(executableBridges, RuntimeBridgeKind.MACHINE_BEHAVIOR)
            && "true".equals(facts.get("has_processing"))
            && facts.containsKey("machine.processing.recipe.0.input")
            && facts.containsKey("machine.processing.recipe.0.output")) {
            bridgeKinds.add(RuntimeBridgeKind.MACHINE_BEHAVIOR);
            requirements.add("machine_behavior_bridge");
        }
        if (supports(executableBridges, RuntimeBridgeKind.MENU_CONTAINER) && "true".equals(facts.get("has_menu"))) {
            bridgeKinds.add(RuntimeBridgeKind.MENU_CONTAINER);
            requirements.add("menu_container_bridge");
        }

        SupportLevel level = bridgeKinds.isEmpty() ? SupportLevel.VISUAL_ONLY : SupportLevel.ADAPTED;
        int score = bridgeKinds.isEmpty() ? 0 : 90;

        return new CompiledCompatibilityPlan(
            identifier.getNamespace(),
            "block",
            identifier.toString(),
            identifier.toString(),
            level,
            CompatibilityStatus.PARTIAL,
            score,
            new Confidence(0.95D, "dynamic_semantic_discovery"),
            List.of(new AdapterBinding("dynamic.semantic.adapter", AdapterFeature.BLOCK_PLACEMENT, "Dynamically discovered semantic contract")),
            List.copyOf(requirements),
            List.copyOf(bridgeKinds),
            Map.copyOf(facts),
            true,
            null,
            true,
            null,
            true,
            true,
            false,
            false,
            bridgeKinds.contains(RuntimeBridgeKind.MENU_CONTAINER),
            null,
            null,
            List.of(),
            List.of(),
            List.of(),
            null,
            false,
            bridgeKinds.contains(RuntimeBridgeKind.FLUID_TRANSFER),
            level,
            "dynamic_machine",
            List.of()
        );
    }

    @NotNull
    private static List<RuntimeBridgeKind> validateExecutableBridges(
        @NotNull Object runtimeObject,
        @NotNull CompiledCompatibilityPlan candidate
    ) {
        List<RuntimeBridgeKind> executable = new ArrayList<>();
        TransferBridgeFactory.ItemTransferBridge item = TransferBridgeFactory.createItemTransfer(candidate, runtimeObject);
        if (item != null && item.executable()) {
            executable.add(RuntimeBridgeKind.ITEM_TRANSFER);
            executable.add(RuntimeBridgeKind.MACHINE_INVENTORY);
            if (candidate.inventoryFacts().containsKey("machine.processing.recipe.0.input")
                && candidate.inventoryFacts().containsKey("machine.processing.recipe.0.output")) {
                executable.add(RuntimeBridgeKind.MACHINE_BEHAVIOR);
            }
        }
        TransferBridgeFactory.FluidTransferBridge fluid = TransferBridgeFactory.createFluidTransfer(candidate, runtimeObject);
        if (fluid != null && fluid.executable()) {
            executable.add(RuntimeBridgeKind.FLUID_TRANSFER);
        }
        TransferBridgeFactory.EnergyTransferBridge energy = TransferBridgeFactory.createEnergyTransfer(candidate, runtimeObject);
        if (energy != null && energy.executable()) {
            executable.add(RuntimeBridgeKind.ENERGY_TRANSFER);
        }
        return List.copyOf(executable);
    }

    private static boolean supports(@Nullable List<RuntimeBridgeKind> executableBridges, @NotNull RuntimeBridgeKind kind) {
        return executableBridges == null || executableBridges.contains(kind);
    }
}
