package org.geysermc.hydraulic.compat.runtime;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

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
    private final Map<Identifier, MachineStateSnapshot> lastStates = new LinkedHashMap<>();

    public MachineSynchronizationCoordinator(
        @NotNull DirtyStateTracker dirtyStateTracker,
        @NotNull SyncDispatcher syncDispatcher
    ) {
        this.dirtyStateTracker = dirtyStateTracker;
        this.syncDispatcher = syncDispatcher;
    }

    @NotNull
    public TickResult tickAndFlush(
        @NotNull MachineProcessingBridge machine,
        @NotNull Identifier blockIdentifier
    ) {
        boolean changed = machine.tick(blockIdentifier, dirtyStateTracker);
        recordMachineState(blockIdentifier, machine);
        List<SyncDeliveryResult> deliveries = syncDispatcher.flush();
        return new TickResult(changed, deliveries);
    }

    @NotNull
    public List<SyncDeliveryResult> flushPending() {
        return syncDispatcher.flush();
    }

    private void recordMachineState(@NotNull Identifier blockIdentifier, @NotNull MachineProcessingBridge machine) {
        MachineStateSnapshot current = new MachineStateSnapshot(machine.progress(), machine.active());
        MachineStateSnapshot previous = lastStates.put(blockIdentifier, current);
        if (previous == null || previous.progress() != current.progress()) {
            dirtyStateTracker.record(new StateChangeSet(List.of(
                new StateChangeSet.FieldChange(blockIdentifier, "machine.progress", previous == null ? null : previous.progress(), current.progress())
            )));
        }
        if (previous == null || previous.active() != current.active()) {
            dirtyStateTracker.record(new StateChangeSet(List.of(
                new StateChangeSet.FieldChange(blockIdentifier, "machine.active", previous == null ? null : previous.active(), current.active())
            )));
        }
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