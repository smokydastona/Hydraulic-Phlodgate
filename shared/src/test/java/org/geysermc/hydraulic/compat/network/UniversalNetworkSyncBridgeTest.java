package org.geysermc.hydraulic.compat.network;

import net.minecraft.resources.Identifier;
import org.geysermc.hydraulic.compat.runtime.RuntimeTraceId;
import org.geysermc.hydraulic.compat.runtime.SyncBatch;
import org.geysermc.hydraulic.compat.runtime.SyncChange;
import org.geysermc.hydraulic.compat.runtime.SyncDeliveryStatus;
import org.geysermc.hydraulic.compat.runtime.SyncPriority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UniversalNetworkSyncBridgeTest {

    @Test
    @DisplayName("State batch coalescer coalesces changes by field")
    void stateBatchCoalescerCoalescesChanges() {
        UniversalNetworkSyncBridge.StateBatchCoalescer coalescer = new UniversalNetworkSyncBridge.StateBatchCoalescer();

        Identifier blockId = Identifier.parse("block:test");
        coalescer.addChange(new SyncChange(blockId, "inventory.slot.0", 1, 2, SyncPriority.NEXT_CYCLE));
        coalescer.addChange(new SyncChange(blockId, "inventory.slot.0", 2, 5, SyncPriority.NEXT_CYCLE));
        coalescer.addChange(new SyncChange(blockId, "container.property.0", 0, 10, SyncPriority.NEXT_CYCLE));

        assertEquals(2, coalescer.pendingCount());

        RuntimeTraceId traceId = new RuntimeTraceId("test-trace");
        SyncBatch batch = coalescer.buildBatch(traceId, SyncPriority.IMMEDIATE);

        assertEquals(2, batch.changes().size());
        assertEquals(0, coalescer.pendingCount());
    }

    @Test
    @DisplayName("Bidirectional action dispatcher records and retrieves execution results")
    void bidirectionalActionDispatcherDispatchesAction() {
        UniversalNetworkSyncBridge.BidirectionalActionDispatcher dispatcher = new UniversalNetworkSyncBridge.BidirectionalActionDispatcher();

        RuntimeTraceId traceId = new RuntimeTraceId("action-1");
        UniversalNetworkSyncBridge.ServerboundAction action = new UniversalNetworkSyncBridge.ServerboundAction(
            traceId,
            "BUTTON_CLICK",
            Identifier.parse("test:machine"),
            Map.of("buttonId", "start_process")
        );

        UniversalNetworkSyncBridge.ActionExecutionResult result = dispatcher.dispatchServerboundAction(action);
        assertNotNull(result);
        assertTrue(result.executed());
        assertEquals(SyncDeliveryStatus.APPLIED, result.status());

        UniversalNetworkSyncBridge.ActionExecutionResult retrieved = dispatcher.getResult(traceId.value());
        assertNotNull(retrieved);
        assertEquals(result, retrieved);
    }
}
