package org.geysermc.hydraulic.compat.runtime;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SessionAutoFlushCoordinatorTest {

    @Test
    public void testSessionAutoFlushEmptySessionHandling() {
        SessionAutoFlushCoordinator coordinator = new SessionAutoFlushCoordinator();
        assertEquals(0, coordinator.activeSessionCount());

        StateChangeSet changeSet = new StateChangeSet(List.of(
            new StateChangeSet.FieldChange(Identifier.parse("custom:generator"), "machine.progress", 0, 1)
        ));

        List<SyncDeliveryResult> results = coordinator.autoFlushStateDeltas(changeSet);
        assertTrue(results.isEmpty());
    }

    @Test
    public void testCoordinatorIntegrationWithMachineSynchronization() {
        DirtyStateTracker tracker = new DirtyStateTracker();
        SyncDispatcher dispatcher = new SyncDispatcher(tracker, new SyncPlanner(), new SyncEncoder(), changes ->
            List.of(new SyncDeliveryResult(
                new EncodedSyncChange(
                    Identifier.fromNamespaceAndPath("hydraulic", "machine"),
                    "energy.amount",
                    EncodedSyncKind.GENERIC_STATE,
                    -1,
                    null,
                    0,
                    null,
                    0,
                    0,
                    100,
                    SyncPriority.IMMEDIATE,
                    "energy.amount"
                ),
                SyncDeliveryStatus.SENT,
                "Sent to client",
                RuntimeTraceId.create()
            ))
        );

        SessionAutoFlushCoordinator autoFlush = new SessionAutoFlushCoordinator();
        MachineSynchronizationCoordinator coordinator = new MachineSynchronizationCoordinator(tracker, dispatcher, autoFlush);

        assertNotNull(coordinator);
        List<SyncDeliveryResult> pending = coordinator.flushPending();
        assertTrue(pending.isEmpty());
    }
}
