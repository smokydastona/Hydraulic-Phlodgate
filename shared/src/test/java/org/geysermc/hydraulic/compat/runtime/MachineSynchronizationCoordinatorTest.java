package org.geysermc.hydraulic.compat.runtime;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Map;
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

    @Test
    void snapshotsAndRestoresMachineStateAcrossRestarts() {
        DirtyStateTracker dirty = new DirtyStateTracker();
        MachineSynchronizationCoordinator coordinator = new MachineSynchronizationCoordinator(
            dirty,
            new SyncDispatcher(dirty, new SyncPlanner(), new SyncEncoder(), changes -> List.of())
        );

        Identifier machine = Identifier.fromNamespaceAndPath("example", "machine");
        TransferBridgeFactory.ItemTransferBridge inventory = new TransferBridgeFactory.ItemTransferBridge() {
            private final TransferBridgeFactory.ItemStackView item = new TransferBridgeFactory.ItemStackView("minecraft:stone", 2);

            @Override
            public boolean executable() { return true; }

            @Override
            public boolean canInsert(Identifier blockIdentifier) { return true; }

            @Override
            public boolean canExtract(Identifier blockIdentifier) { return true; }

            @Override
            public String inventoryType(Identifier blockIdentifier) { return "generic"; }

            @Override
            public int slotCount(Identifier blockIdentifier) { return 2; }

            @Override
            public TransferBridgeFactory.ItemStackView itemAt(Identifier blockIdentifier, int slot) { return item; }
        };
        MachineProcessingBridge bridge = new MachineProcessingBridge(
            null,
            inventory,
            1,
            1,
            List.of(new MachineProcessingBridge.MachineRecipe(
                new TransferBridgeFactory.ItemStackView("minecraft:stone", 1),
                new TransferBridgeFactory.ItemStackView("minecraft:iron_ingot", 1),
                1
            ))
        );

        coordinator.tickAndFlush(bridge, machine);
        Map<Identifier, MachineSynchronizationCoordinator.MachineStateSnapshot> snapshot = coordinator.snapshotState();

        MachineSynchronizationCoordinator restarted = new MachineSynchronizationCoordinator(
            new DirtyStateTracker(),
            new SyncDispatcher(new DirtyStateTracker(), new SyncPlanner(), new SyncEncoder(), changes -> List.of())
        );
        restarted.restoreState(snapshot);

        assertEquals(snapshot, restarted.snapshotState());
        assertEquals(1, restarted.snapshotState().get(machine).progress());
        assertTrue(restarted.snapshotState().get(machine).active());
    }
}
