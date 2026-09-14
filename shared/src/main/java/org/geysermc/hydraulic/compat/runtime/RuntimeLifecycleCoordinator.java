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
    private static volatile CompatibilityRegistry compatibilityRegistry;
    private static volatile SessionAutoFlushCoordinator sessionAutoFlushCoordinator;
    private static final Map<BlockEntity, MachineSynchronizationCoordinator> machineSynchronizers = new WeakHashMap<>();

    private RuntimeLifecycleCoordinator() {
    }

    public static void install(@NotNull CompatibilityRegistry compatibilityRegistry) {
        RuntimeLifecycleCoordinator.compatibilityRegistry = compatibilityRegistry;
        machineLifecycleManager = new DynamicMachineLifecycleManager(compatibilityRegistry.dispatchTable());
        sessionAutoFlushCoordinator = new SessionAutoFlushCoordinator();
        synchronized (machineSynchronizers) {
            machineSynchronizers.clear();
        }
    }

    public static void discoverBlockEntity(@NotNull BlockEntity blockEntity) {
        if (!(blockEntity.getLevel() instanceof ServerLevel)) {
            return;
        }

        DynamicMachineLifecycleManager manager = machineLifecycleManager;
        if (manager == null) {
            return;
        }

        Identifier identifier = BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock());
        if (identifier == null) {
            return;
        }

        try {
            manager.registerAndCompile(identifier, blockEntity, Map.of("category", "block_entity"));
        } catch (Throwable throwable) {
            LOGGER.warn("Dynamic block-entity discovery failed for {} and was skipped", identifier, throwable);
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
        TransferBridgeFactory.ItemTransferBridge inventory = registry.dispatchTable().itemTransfer(identifier, blockEntity);
        MachineProcessingBridge machine = registry.dispatchTable().machineProcessing(identifier, inventory);
        if (machine == null) {
            return;
        }

        MachineSynchronizationCoordinator synchronizer;
        synchronized (machineSynchronizers) {
            synchronizer = machineSynchronizers.computeIfAbsent(blockEntity, ignored -> new MachineSynchronizationCoordinator(
                createMachineTracker(),
                autoFlush));
        }
        synchronizer.tickAndFlush(machine, identifier, level, blockEntity.getBlockPos());
    }

    @NotNull
    private static DirtyStateTracker createMachineTracker() {
        return new DirtyStateTracker();
    }

}