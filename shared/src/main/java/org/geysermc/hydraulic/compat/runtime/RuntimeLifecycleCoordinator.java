package org.geysermc.hydraulic.compat.runtime;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.hydraulic.compat.CompatibilityRegistry;
import org.geysermc.hydraulic.compat.discovery.DynamicMachineLifecycleManager;
import org.geysermc.hydraulic.compat.discovery.SemanticDiscoveryEngine;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

public final class RuntimeLifecycleCoordinator {
    private static final Logger LOGGER = LoggerFactory.getLogger("HydraulicRuntimeLifecycle");
    private static volatile DynamicMachineLifecycleManager machineLifecycleManager;
    private static volatile LiveCapabilityBinder capabilityBinder;
    private static volatile CompatibilityRegistry compatibilityRegistry;
    private static volatile SessionAutoFlushCoordinator sessionAutoFlushCoordinator;
    private static final Map<BlockEntity, MachineSynchronizationCoordinator> machineSynchronizers = new WeakHashMap<>();

    private RuntimeLifecycleCoordinator() {
    }

    public static void install(@NotNull CompatibilityRegistry compatibilityRegistry) {
        LiveCapabilityBinder previousBinder = capabilityBinder;
        if (previousBinder != null) {
            previousBinder.clear();
        }
        RuntimeLifecycleCoordinator.compatibilityRegistry = compatibilityRegistry;
        machineLifecycleManager = new DynamicMachineLifecycleManager(compatibilityRegistry.dispatchTable());
        capabilityBinder = new LiveCapabilityBinder(machineLifecycleManager);
        sessionAutoFlushCoordinator = new SessionAutoFlushCoordinator();
        synchronized (machineSynchronizers) {
            machineSynchronizers.clear();
        }
    }

    public static void discoverBlockEntity(@NotNull BlockEntity blockEntity) {
        if (!(blockEntity.getLevel() instanceof ServerLevel)) {
            return;
        }

        LiveCapabilityBinder binder = capabilityBinder;
        if (binder == null) {
            return;
        }

        Identifier identifier = BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock());
        if (identifier == null) {
            return;
        }

        try {
            binder.bind(identifier, blockEntity, Map.of("category", "block_entity"));
        } catch (Throwable throwable) {
            LOGGER.warn("Dynamic block-entity discovery failed for {} and was skipped", identifier, throwable);
        }
    }

    public static void unbindBlockEntity(@NotNull BlockEntity blockEntity) {
        LiveCapabilityBinder binder = capabilityBinder;
        if (binder != null) {
            binder.unbind(blockEntity);
        }
        synchronized (machineSynchronizers) {
            machineSynchronizers.remove(blockEntity);
        }
    }

    public static void discoverMenu(@NotNull Identifier identifier, @NotNull AbstractContainerMenu menu) {
        try {
            SemanticDiscoveryEngine.discoverRuntimeObject(identifier, menu, Map.of("category", "menu"));
        } catch (Throwable throwable) {
            LOGGER.warn("Dynamic menu discovery failed for {} and was skipped", identifier, throwable);
        }
    }

    public static void tickBlockEntity(@NotNull BlockEntity blockEntity) {
        try {
            tickBlockEntitySafely(blockEntity);
        } catch (Throwable throwable) {
            LOGGER.warn("Runtime block-entity synchronization failed at {} and was skipped", blockEntity.getBlockPos(), throwable);
        }
    }

    private static void tickBlockEntitySafely(@NotNull BlockEntity blockEntity) {
        if (!(blockEntity.getLevel() instanceof ServerLevel level)) {
            return;
        }
        CompatibilityRegistry registry = compatibilityRegistry;
        SessionAutoFlushCoordinator autoFlush = sessionAutoFlushCoordinator;
        if (registry == null || autoFlush == null) {
            return;
        }

        try {
            List<GeyserSession> activeSessions = new ArrayList<>();
            for (var connection : GeyserApi.api().onlineConnections()) {
                if (connection instanceof GeyserSession session) {
                    activeSessions.add(session);
                }
            }
            autoFlush.reconcileSessions(activeSessions);
        } catch (Throwable throwable) {
            LOGGER.debug("Geyser sessions were unavailable during machine synchronization", throwable);
        }

        Identifier identifier = BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock());
        if (identifier == null) {
            return;
        }
        LiveCapabilityBinder binder = capabilityBinder;
        if (binder == null) {
            return;
        }
        LiveCapabilityBinder.LiveBinding binding = binder.resolve(blockEntity);
        if (binding == null) {
            binding = binder.bind(identifier, blockEntity, Map.of("category", "block_entity"));
        }
        if (!binding.identifier().equals(identifier)) {
            binding = binder.refresh(identifier, blockEntity, Map.of("category", "block_entity"));
        }
        TransferBridgeFactory.ItemTransferBridge inventory = registry.dispatchTable().itemTransfer(binding.identifier(), blockEntity);
        MachineProcessingBridge machine = registry.dispatchTable().machineProcessing(binding.identifier(), inventory);
        if (machine == null) {
            return;
        }

        MachineSynchronizationCoordinator synchronizer;
        synchronized (machineSynchronizers) {
            synchronizer = machineSynchronizers.computeIfAbsent(blockEntity, ignored -> new MachineSynchronizationCoordinator(
                createMachineTracker(),
                autoFlush));
        }
        synchronizer.tickAndFlush(machine, binding.identifier(), level, blockEntity.getBlockPos());
    }

    @NotNull
    private static DirtyStateTracker createMachineTracker() {
        return new DirtyStateTracker();
    }

}