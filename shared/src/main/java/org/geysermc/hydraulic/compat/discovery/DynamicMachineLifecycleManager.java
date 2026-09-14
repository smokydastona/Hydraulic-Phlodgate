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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    public DynamicMachineLifecycleManager(@NotNull RuntimeDispatchTable dispatchTable) {
        this.dispatchTable = Objects.requireNonNull(dispatchTable, "dispatchTable");
    }

    /**
     * Inspects a live machine block entity and dynamically compiles a runtime plan if not already registered.
     */
    @NotNull
    public CompiledCompatibilityPlan registerAndCompile(
        @NotNull Identifier identifier,
        @NotNull Object runtimeBlockEntity,
        @NotNull Map<String, String> initialFacts
    ) {
        Objects.requireNonNull(identifier, "identifier");
        Objects.requireNonNull(runtimeBlockEntity, "runtimeBlockEntity");

        CompiledCompatibilityPlan existing = dispatchTable.block(identifier);
        if (existing != null) {
            return existing;
        }

        SemanticDiscoveryEngine.DiscoveredSemanticProfile profile = SemanticDiscoveryEngine.discoverRuntimeObject(
            identifier,
            runtimeBlockEntity,
            initialFacts
        );

        CompiledCompatibilityPlan dynamicPlan = buildPlanFromProfile(identifier, profile);
        dynamicPlans.put(identifier, dynamicPlan);
        dispatchTable.registerDynamicPlan(dynamicPlan);
        return dynamicPlan;
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
        @NotNull SemanticDiscoveryEngine.DiscoveredSemanticProfile profile
    ) {
        Map<String, String> facts = profile.facts();
        List<RuntimeBridgeKind> bridgeKinds = new ArrayList<>();
        List<String> requirements = new ArrayList<>();

        if ("true".equals(facts.get("has_inventory")) || "true".equals(facts.get("can_insert")) || "true".equals(facts.get("can_extract"))) {
            bridgeKinds.add(RuntimeBridgeKind.ITEM_TRANSFER);
            requirements.add("item_transfer_bridge");
            bridgeKinds.add(RuntimeBridgeKind.MACHINE_INVENTORY);
            requirements.add("machine_inventory_bridge");
        }
        if ("true".equals(facts.get("has_tank")) || "true".equals(facts.get("can_fill")) || "true".equals(facts.get("can_drain"))) {
            bridgeKinds.add(RuntimeBridgeKind.FLUID_TRANSFER);
            requirements.add("fluid_transfer_bridge");
        }
        if ("true".equals(facts.get("has_energy")) || "true".equals(facts.get("can_receive_energy")) || "true".equals(facts.get("can_provide_energy"))) {
            bridgeKinds.add(RuntimeBridgeKind.ENERGY_TRANSFER);
            requirements.add("energy_transfer_bridge");
        }
        if ("true".equals(facts.get("is_ticking_machine")) || "true".equals(facts.get("has_processing")) || "true".equals(facts.get("machine.ticking.discovered"))) {
            bridgeKinds.add(RuntimeBridgeKind.MACHINE_BEHAVIOR);
            requirements.add("machine_behavior_bridge");
        }
        if ("true".equals(facts.get("has_menu"))) {
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
}
