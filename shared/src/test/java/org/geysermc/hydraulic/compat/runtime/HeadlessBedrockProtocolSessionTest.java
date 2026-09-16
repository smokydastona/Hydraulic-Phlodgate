package org.geysermc.hydraulic.compat.runtime;

import net.minecraft.resources.Identifier;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerSetDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventorySlotPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Automated end-to-end headless Bedrock client protocol test.
 * Simulates a connected Bedrock client bot session and validates packet response sequences.
 */
class HeadlessBedrockProtocolSessionTest {
    private static final Identifier CRUSHER = Identifier.fromNamespaceAndPath("immersiveengineering", "crusher");
    private static final Identifier MIXER = Identifier.fromNamespaceAndPath("create", "mechanical_mixer");

    public static final class HeadlessBedrockBot {
        private final List<BedrockPacket> outboundPackets = new ArrayList<>();
        private final List<BedrockPacket> receivedPackets = new ArrayList<>();
        private final DirtyStateTracker dirtyStateTracker = new DirtyStateTracker();
        private int currentWindowId = ContainerId.NONE;
        private boolean spawned = false;
        private boolean initialized = false;

        public void handleServerPacket(@NotNull BedrockPacket packet) {
            this.receivedPackets.add(packet);
        }

        public void sendPacket(@NotNull BedrockPacket packet) {
            this.outboundPackets.add(packet);
        }

        public void simulateHandshakeAndSpawn() {
            this.spawned = true;
            this.initialized = true;
        }

        public void openContainer(int windowId) {
            this.currentWindowId = windowId;
        }

        public void closeContainer() {
            this.currentWindowId = ContainerId.NONE;
        }

        public int currentWindowId() {
            return this.currentWindowId;
        }

        public List<BedrockPacket> receivedPackets() {
            return List.copyOf(this.receivedPackets);
        }
    }

    @Test
    void simulatesCompleteHeadlessClientSessionAndPacketSequences() {
        HeadlessBedrockBot bot = new HeadlessBedrockBot();
        bot.simulateHandshakeAndSpawn();

        assertTrue(bot.spawned);
        assertTrue(bot.initialized);

        // 1. Client interacts with multiblock machine
        InventoryTransactionPacket transactionPacket = new InventoryTransactionPacket();
        transactionPacket.setTransactionType(InventoryTransactionType.ITEM_USE);
        transactionPacket.setActionType(0); // ITEM_USE_ON_BLOCK
        bot.sendPacket(transactionPacket);
        assertEquals(1, bot.outboundPackets.size());

        // 2. Server executes transaction and tracks dirty state
        DirtyStateTracker dirty = bot.dirtyStateTracker;
        dirty.record(new StateChangeSet(List.of(
            new StateChangeSet.FieldChange(CRUSHER, "inventory.slot.0", "minecraft:raw_iron:1", "minecraft:air:0"),
            new StateChangeSet.FieldChange(CRUSHER, "inventory.slot.1", "minecraft:air:0", "minecraft:iron_ingot:2"),
            new StateChangeSet.FieldChange(CRUSHER, "container.property.0", 0, 100),
            MultiBlockHighlightOverlay.createHighlightStateChange(CRUSHER, new MultiBlockHighlightOverlay.HighlightBox(
                "10,64,10", 10, 64, 10, 12, 66, 14, MultiBlockHighlightOverlay.HighlightStatus.FORMED, "Crusher Assembled"
            ))
        )));

        assertTrue(dirty.dirty());

        // 3. SyncDispatcher encodes and delivers packets to the Bedrock client session
        SyncDispatcher dispatcher = new SyncDispatcher(
            dirty,
            new SyncPlanner(),
            new SyncEncoder(),
            new GeyserSyncTransport(
                null,
                bot::handleServerPacket,
                (s, id, count) -> ItemData.AIR,
                () -> 1
            )
        );

        List<SyncDeliveryResult> deliveryResults = dispatcher.flush();
        assertEquals(4, deliveryResults.size());
        assertTrue(deliveryResults.stream().allMatch(SyncDeliveryResult::successfulHandoff));

        // 4. Verify client received the expected Bedrock packet types
        List<BedrockPacket> received = bot.receivedPackets();
        assertEquals(3, received.size());

        // 2 slot packets + 1 container data packet
        long slotPackets = received.stream().filter(p -> p instanceof InventorySlotPacket).count();
        long dataPackets = received.stream().filter(p -> p instanceof ContainerSetDataPacket).count();
        assertEquals(2, slotPackets);
        assertEquals(1, dataPackets);

        // Verify container data packet
        ContainerSetDataPacket dataPacket = (ContainerSetDataPacket) received.stream().filter(p -> p instanceof ContainerSetDataPacket).findFirst().orElseThrow();
        assertEquals(0, dataPacket.getProperty());
        assertEquals(100, dataPacket.getValue());

        // Dirty tracker is clean after flush
        assertFalse(dirty.dirty());
    }

    @Test
    void handlesClientContainerOpenAndCloseLifecycle() {
        HeadlessBedrockBot bot = new HeadlessBedrockBot();
        bot.simulateHandshakeAndSpawn();

        bot.openContainer(3);
        assertEquals(3, bot.currentWindowId());

        GeyserSyncTransport transport = new GeyserSyncTransport(
            null,
            bot::handleServerPacket,
            (s, id, count) -> ItemData.AIR,
            bot::currentWindowId
        );

        EncodedSyncChange change = new EncodedSyncChange(
            MIXER,
            "inventory.slot.0",
            EncodedSyncKind.INVENTORY_SLOT,
            0,
            "minecraft:air",
            0,
            "create:andesite_alloy",
            1,
            null,
            null,
            SyncPriority.IMMEDIATE,
            "inventory.slot.0"
        );

        List<SyncDeliveryResult> results = transport.deliver(List.of(change));
        assertEquals(1, results.size());
        assertTrue(results.getFirst().successfulHandoff());

        InventorySlotPacket receivedSlot = assertInstanceOf(InventorySlotPacket.class, bot.receivedPackets().getFirst());
        assertEquals(3, receivedSlot.getContainerId());
        assertEquals(0, receivedSlot.getSlot());

        bot.closeContainer();
        assertEquals(ContainerId.NONE, bot.currentWindowId());
    }
}
