package org.geysermc.hydraulic.compat.runtime;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MachineSynchronizationCoordinatorTest {
    @Test
    void flushesCoalescedMachineStateThroughTransport() {
        DirtyStateTracker dirty = new DirtyStateTracker();
        List<EncodedSyncChange> delivered = new ArrayList<>();
        SyncTransport transport = changes -> {
            delivered.addAll(changes);
            return changes.stream()
                .map(change -> new SyncDeliveryResult(change, SyncDeliveryStatus.SENT, null))
                .toList();
        };
        MachineSynchronizationCoordinator coordinator = new MachineSynchronizationCoordinator(
            dirty,
            new SyncDispatcher(dirty, new SyncPlanner(), new SyncEncoder(), transport)
        );

        Identifier machine = Identifier.fromNamespaceAndPath("example", "machine");
        dirty.record(new StateChangeSet(List.of(
            new StateChangeSet.FieldChange(machine, "machine.progress", 1, 2),
            new StateChangeSet.FieldChange(machine, "machine.progress", 2, 3),
            new StateChangeSet.FieldChange(machine, "machine.active", false, true)
        )));

        List<SyncDeliveryResult> results = coordinator.flushPending();

        assertEquals(2, results.size());
        assertEquals(2, delivered.size());
        assertTrue(delivered.stream().anyMatch(change -> change.field().equals("machine.progress") && change.afterValue().equals(3)));
        assertTrue(delivered.stream().anyMatch(change -> change.field().equals("machine.active")));
        assertTrue(results.stream().allMatch(SyncDeliveryResult::successfulHandoff));
    }
}
