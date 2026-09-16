package org.geysermc.hydraulic.compat.runtime;

import net.minecraft.resources.Identifier;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerSetDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventorySlotPacket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MultiClientSyncStressTest {

    static class SimulatedBedrockClientSession {
        final String sessionId;
        final List<BedrockPacket> receivedPackets = new CopyOnWriteArrayList<>();
        final GeyserSyncTransport transport;
        final SyncDispatcher dispatcher;
        final DirtyStateTracker dirtyStateTracker = new DirtyStateTracker();

        SimulatedBedrockClientSession(String sessionId, int containerId) {
            this.sessionId = sessionId;
            this.transport = new GeyserSyncTransport(
                null,
                receivedPackets::add,
                (session, itemId, count) -> ItemData.AIR,
                () -> containerId
            );
            this.dispatcher = new SyncDispatcher(this.dirtyStateTracker, new SyncPlanner(), new SyncEncoder(), this.transport);
        }

        void drainAndDispatch() {
            dispatcher.flush();
        }
    }

    static class ConcurrentTestMachineBridge implements TransferBridgeFactory.ItemTransferBridge {
        private final List<TransferBridgeFactory.ItemStackView> slots = new CopyOnWriteArrayList<>();

        public ConcurrentTestMachineBridge(TransferBridgeFactory.ItemStackView in, TransferBridgeFactory.ItemStackView out) {
            slots.add(in);
            slots.add(out);
        }

        @Override public boolean canInsert(Identifier id) { return true; }
        @Override public boolean canExtract(Identifier id) { return true; }
        @Override public String inventoryType(Identifier id) { return "machine"; }
        @Override public int slotCount(Identifier id) { return slots.size(); }

        @Override
        public synchronized TransferBridgeFactory.ItemStackView itemAt(Identifier id, int slot) {
            if (slot < 0 || slot >= slots.size()) return null;
            return slots.get(slot);
        }

        @Override
        public synchronized int insert(Identifier id, TransferBridgeFactory.ItemStackView item, int slot, String side, boolean simulate) {
            TransferBridgeFactory.ItemStackView cur = slots.get(slot);
            if (cur.isEmpty() || cur.matches(item)) {
                int toInsert = Math.min(item.count(), 64 - cur.count());
                if (!simulate && toInsert > 0) {
                    slots.set(slot, new TransferBridgeFactory.ItemStackView(item.itemId(), cur.count() + toInsert));
                }
                return toInsert;
            }
            return 0;
        }

        @Override
        public synchronized int extract(Identifier id, TransferBridgeFactory.ItemStackView item, int slot, String side, boolean simulate) {
            TransferBridgeFactory.ItemStackView cur = slots.get(slot);
            if (!cur.isEmpty() && cur.matches(item)) {
                int toExtract = Math.min(item.count(), cur.count());
                if (!simulate && toExtract > 0) {
                    int rem = cur.count() - toExtract;
                    slots.set(slot, new TransferBridgeFactory.ItemStackView(rem <= 0 ? "minecraft:air" : cur.itemId(), rem));
                }
                return toExtract;
            }
            return 0;
        }
    }

    @Test
    @DisplayName("High-concurrency multi-client synchronization stress test under heavy tick updates")
    void multiClientSynchronizationStressTest() throws InterruptedException {
        int clientCount = 8;
        int threadCount = 16;
        int iterationsPerThread = 50;
        Identifier machineId = Identifier.parse("hydraulic_test_mod:stress_machine");

        List<SimulatedBedrockClientSession> sessions = new ArrayList<>();
        for (int i = 0; i < clientCount; i++) {
            sessions.add(new SimulatedBedrockClientSession("client-" + i, 10 + i));
        }

        ConcurrentTestMachineBridge machineBridge = new ConcurrentTestMachineBridge(
            new TransferBridgeFactory.ItemStackView("minecraft:iron_ore", 64),
            new TransferBridgeFactory.ItemStackView("minecraft:air", 0)
        );

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger totalMutations = new AtomicInteger();

        for (int t = 0; t < threadCount; t++) {
            int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < iterationsPerThread; i++) {
                        RuntimeTraceId traceId = new RuntimeTraceId("trace-" + threadId + "-" + i);
                        int extracted = machineBridge.extract(
                            machineId,
                            new TransferBridgeFactory.ItemStackView("minecraft:iron_ore", 1),
                            0,
                            null,
                            false
                        );

                        if (extracted > 0) {
                            machineBridge.insert(
                                machineId,
                                new TransferBridgeFactory.ItemStackView("minecraft:iron_ingot", 1),
                                1,
                                null,
                                false
                            );

                            StateChangeSet changeSet = new StateChangeSet(List.of(
                                new StateChangeSet.FieldChange(machineId, "inventory.slot.0", null, machineBridge.itemAt(machineId, 0).count()),
                                new StateChangeSet.FieldChange(machineId, "inventory.slot.1", null, machineBridge.itemAt(machineId, 1).count()),
                                new StateChangeSet.FieldChange(machineId, "container.property.0", null, i)
                            ), traceId);

                            totalMutations.incrementAndGet();

                            // Broadcast to viewing client sessions
                            for (SimulatedBedrockClientSession session : sessions) {
                                session.dirtyStateTracker.record(changeSet);
                                session.drainAndDispatch();
                            }
                        }
                    }
                } catch (Exception e) {
                    fail("Exception in concurrent stress worker: " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Multi-client synchronization stress test timed out");
        assertTrue(totalMutations.get() > 0, "Expected mutations during stress test");

        // Verify all sessions received valid, non-corrupted packets
        for (SimulatedBedrockClientSession session : sessions) {
            assertFalse(session.receivedPackets.isEmpty(), "Session " + session.sessionId + " should have received packets");
            for (BedrockPacket packet : session.receivedPackets) {
                assertTrue(packet instanceof InventorySlotPacket || packet instanceof ContainerSetDataPacket);
            }
        }
    }
}
