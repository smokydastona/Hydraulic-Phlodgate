package org.geysermc.hydraulic.compat.capability;

import net.minecraft.resources.Identifier;
import org.geysermc.hydraulic.compat.ir.CompiledCompatibilityPlan;
import org.geysermc.hydraulic.compat.runtime.BlockEntityPatchTemplate;
import org.geysermc.hydraulic.compat.runtime.FluidContainerBridge;
import org.geysermc.hydraulic.compat.runtime.MachineBridgeFactory;
import org.geysermc.hydraulic.compat.runtime.MachineProcessingBridge;
import org.geysermc.hydraulic.compat.runtime.MixedResourceMachineProcessingBridge;
import org.geysermc.hydraulic.compat.runtime.RuntimeBridgeKind;
import org.geysermc.hydraulic.compat.runtime.TransferBridgeFactory;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dynamic capability binder for translating compiled plans and discovered runtime capabilities
 * into concrete, executable Hydraulic bridges.
 */
public final class DynamicCapabilityBinder {

    private DynamicCapabilityBinder() {
    }

    public record BoundBridges(
        @NotNull CompiledCompatibilityPlan plan,
        @Nullable Object runtimeTarget,
        @Nullable TransferBridgeFactory.ItemTransferBridge itemTransferBridge,
        @Nullable TransferBridgeFactory.FluidTransferBridge fluidTransferBridge,
        @Nullable TransferBridgeFactory.EnergyTransferBridge energyTransferBridge,
        @Nullable MachineProcessingBridge machineProcessingBridge,
        @Nullable MixedResourceMachineProcessingBridge mixedResourceProcessingBridge,
        @Nullable FluidContainerBridge fluidContainerBridge,
        @Nullable ContainerType menuFallbackType,
        @Nullable BlockEntityPatchTemplate blockEntityPatchTemplate,
        @NotNull List<RuntimeBridgeKind> boundBridgeKinds,
        @NotNull List<String> bindingDiagnostics
    ) {
        public BoundBridges {
            boundBridgeKinds = List.copyOf(boundBridgeKinds);
            bindingDiagnostics = List.copyOf(bindingDiagnostics);
        }

        public boolean isFullyBound() {
            for (RuntimeBridgeKind required : plan.runtimeBridgeKinds()) {
                if (!boundBridgeKinds.contains(required)) {
                    return false;
                }
            }
            return true;
        }
    }

    @NotNull
    public static BoundBridges bind(@NotNull CompiledCompatibilityPlan plan, @Nullable Object runtimeTarget) {
        List<RuntimeBridgeKind> boundKinds = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();

        TransferBridgeFactory.ItemTransferBridge itemBridge = null;
        if (plan.requiresRuntimeBridge(RuntimeBridgeKind.ITEM_TRANSFER) || plan.requiresRuntimeBridge(RuntimeBridgeKind.MACHINE_INVENTORY)) {
            if (runtimeTarget != null) {
                itemBridge = TransferBridgeFactory.createItemTransfer(plan, runtimeTarget);
            }
            if (itemBridge != null) {
                boundKinds.add(RuntimeBridgeKind.ITEM_TRANSFER);
                if (plan.requiresRuntimeBridge(RuntimeBridgeKind.MACHINE_INVENTORY)) {
                    boundKinds.add(RuntimeBridgeKind.MACHINE_INVENTORY);
                }
            } else {
                diagnostics.add("Failed to bind executable ItemTransferBridge for " + plan.javaIdentifier());
            }
        }

        TransferBridgeFactory.FluidTransferBridge fluidBridge = null;
        if (plan.requiresRuntimeBridge(RuntimeBridgeKind.FLUID_TRANSFER)) {
            if (runtimeTarget != null) {
                fluidBridge = TransferBridgeFactory.createFluidTransfer(plan, runtimeTarget);
            }
            if (fluidBridge != null) {
                boundKinds.add(RuntimeBridgeKind.FLUID_TRANSFER);
            } else {
                diagnostics.add("Failed to bind executable FluidTransferBridge for " + plan.javaIdentifier());
            }
        }

        TransferBridgeFactory.EnergyTransferBridge energyBridge = null;
        if (plan.requiresRuntimeBridge(RuntimeBridgeKind.ENERGY_TRANSFER)) {
            if (runtimeTarget != null) {
                energyBridge = TransferBridgeFactory.createEnergyTransfer(plan, runtimeTarget);
            }
            if (energyBridge != null) {
                boundKinds.add(RuntimeBridgeKind.ENERGY_TRANSFER);
            } else {
                diagnostics.add("Failed to bind executable EnergyTransferBridge for " + plan.javaIdentifier());
            }
        }

        MachineProcessingBridge processingBridge = null;
        if (plan.requiresRuntimeBridge(RuntimeBridgeKind.MACHINE_BEHAVIOR) && itemBridge != null) {
            processingBridge = MachineBridgeFactory.createProcessing(plan, itemBridge);
            if (processingBridge != null) {
                boundKinds.add(RuntimeBridgeKind.MACHINE_BEHAVIOR);
            } else {
                diagnostics.add("Failed to bind MachineProcessingBridge for " + plan.javaIdentifier());
            }
        }

        MixedResourceMachineProcessingBridge mixedBridge = null;
        if (plan.requiresRuntimeBridge(RuntimeBridgeKind.MACHINE_BEHAVIOR) && itemBridge != null && fluidBridge != null && energyBridge != null) {
            mixedBridge = MachineBridgeFactory.createMixedProcessing(plan, itemBridge, fluidBridge, energyBridge);
            if (mixedBridge != null) {
                boundKinds.add(RuntimeBridgeKind.MACHINE_BEHAVIOR);
            }
        }

        FluidContainerBridge fluidContainerBridge = null;
        if (fluidBridge != null) {
            fluidContainerBridge = new FluidContainerBridge(fluidBridge, 0, 1000);
        }

        ContainerType menuFallback = plan.menuFallbackContainerType();
        if (menuFallback != null) {
            boundKinds.add(RuntimeBridgeKind.MENU_CONTAINER);
        }

        BlockEntityPatchTemplate bePatch = plan.blockEntityPatchTemplate();
        if (bePatch != null) {
            boundKinds.add(RuntimeBridgeKind.BLOCK_ENTITY_DATA);
        }

        return new BoundBridges(
            plan,
            runtimeTarget,
            itemBridge,
            fluidBridge,
            energyBridge,
            processingBridge,
            mixedBridge,
            fluidContainerBridge,
            menuFallback,
            bePatch,
            boundKinds,
            diagnostics
        );
    }
}
