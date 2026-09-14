package org.geysermc.hydraulic.compat.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns the server-tick boundary for a stateful machine bridge.
 * A machine tick records authoritative changes, then this coordinator immediately drains,
 * coalesces, encodes, and delivers them through the configured transport.
 */
public final class MachineSynchronizationCoordinator {
    private final DirtyStateTracker dirtyStateTracker;
    private final SyncDispatcher syncDispatcher;
    private final SessionAutoFlushCoordinator autoFlushCoordinator;
    private final Map<Identifier, MachineStateSnapshot> lastStates = new LinkedHashMap<>();

    public MachineSynchronizationCoordinator(
        @NotNull DirtyStateTracker dirtyStateTracker,
        @NotNull SyncDispatcher syncDispatcher
    ) {
        this(dirtyStateTracker, syncDispatcher, null);
    }

    public MachineSynchronizationCoordinator(
        @NotNull DirtyStateTracker dirtyStateTracker,
        @NotNull SyncDispatcher syncDispatcher,
        @Nullable SessionAutoFlushCoordinator autoFlushCoordinator
    ) {
        this.dirtyStateTracker = dirtyStateTracker;
        this.syncDispatcher = syncDispatcher;
        this.autoFlushCoordinator = autoFlushCoordinator;
    }

    public MachineSynchronizationCoordinator(
        @NotNull DirtyStateTracker dirtyStateTracker,
        @NotNull SessionAutoFlushCoordinator autoFlushCoordinator
    ) {
        this(
            dirtyStateTracker,
            new SyncDispatcher(dirtyStateTracker, new SyncPlanner(), new SyncEncoder(), changes -> List.of()),
            autoFlushCoordinator
        );
    }

    @NotNull
    public TickResult tickAndFlush(
        @NotNull MachineProcessingBridge machine,
        @NotNull Identifier blockIdentifier
    ) {
        return tickAndFlush(machine, blockIdentifier, null, null);
    }

    @NotNull
    public TickResult tickAndFlush(
        @NotNull MachineProcessingBridge machine,
        @NotNull Identifier blockIdentifier,
        @Nullable ServerLevel level,
        @Nullable BlockPos position
    ) {
        boolean changed = machine.tick(blockIdentifier, dirtyStateTracker);
        recordMachineState(blockIdentifier, machine);
        if (autoFlushCoordinator != null) {
            StateChangeSet pending = dirtyStateTracker.drain();
            List<SyncDeliveryResult> deliveries = pending.changes().isEmpty()
                ? List.of()
                : autoFlushCoordinator.autoFlushStateDeltas(pending, level, position);
            return new TickResult(changed, deliveries);
        }
        List<SyncDeliveryResult> deliveries = new ArrayList<>(syncDispatcher.flush());
        return new TickResult(changed, deliveries);
    }

    @NotNull
    public List<SyncDeliveryResult> flushPending() {
        return syncDispatcher.flush();
    }

    @Nullable
    private StateChangeSet recordMachineState(@NotNull Identifier blockIdentifier, @NotNull MachineProcessingBridge machine) {
        MachineStateSnapshot current = new MachineStateSnapshot(machine.progress(), machine.active());
        MachineStateSnapshot previous = lastStates.put(blockIdentifier, current);
        List<StateChangeSet.FieldChange> fieldChanges = new ArrayList<>();
        if (previous == null || previous.progress() != current.progress()) {
            fieldChanges.add(new StateChangeSet.FieldChange(blockIdentifier, "machine.progress", previous == null ? null : previous.progress(), current.progress()));
        }
        if (previous == null || previous.active() != current.active()) {
            fieldChanges.add(new StateChangeSet.FieldChange(blockIdentifier, "machine.active", previous == null ? null : previous.active(), current.active()));
        }
        if (!fieldChanges.isEmpty()) {
            StateChangeSet changeSet = new StateChangeSet(fieldChanges);
            dirtyStateTracker.record(changeSet);
            return changeSet;
        }
        return null;
    }

    private record MachineStateSnapshot(int progress, boolean active) {
    }

    public record TickResult(
        boolean machineChanged,
        @NotNull List<SyncDeliveryResult> deliveries
    ) {
        public TickResult {
            deliveries = List.copyOf(deliveries);
        }
    }
}