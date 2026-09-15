package org.geysermc.hydraulic.compat.automation;

import net.minecraft.resources.Identifier;
import org.geysermc.hydraulic.compat.runtime.TransferBridgeFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UniversalAutomationEngineTest {

    static class MockTestTransferBridge implements TransferBridgeFactory.ItemTransferBridge {
        private final List<TransferBridgeFactory.ItemStackView> slots = new ArrayList<>();

        public MockTestTransferBridge(int slotCount, TransferBridgeFactory.ItemStackView initialItem) {
            for (int i = 0; i < slotCount; i++) {
                slots.add(i == 0 ? initialItem : new TransferBridgeFactory.ItemStackView("minecraft:air", 0));
            }
        }

        @Override
        public boolean canInsert(Identifier blockIdentifier) {
            return true;
        }

        @Override
        public boolean canExtract(Identifier blockIdentifier) {
            return true;
        }

        @Override
        public String inventoryType(Identifier blockIdentifier) {
            return "mock_inventory";
        }

        @Override
        public int slotCount(Identifier blockIdentifier) {
            return slots.size();
        }

        @Override
        public TransferBridgeFactory.ItemStackView itemAt(Identifier blockIdentifier, int slot) {
            if (slot < 0 || slot >= slots.size()) return null;
            return slots.get(slot);
        }

        @Override
        public int insert(Identifier blockIdentifier, TransferBridgeFactory.ItemStackView item, int slot, String side, boolean simulate) {
            if (slot < 0 || slot >= slots.size()) return 0;
            TransferBridgeFactory.ItemStackView current = slots.get(slot);
            if (current.isEmpty() || current.matches(item)) {
                int toMove = Math.min(item.count(), 64 - current.count());
                if (!simulate && toMove > 0) {
                    slots.set(slot, new TransferBridgeFactory.ItemStackView(item.itemId(), current.count() + toMove));
                }
                return toMove;
            }
            return 0;
        }

        @Override
        public int extract(Identifier blockIdentifier, TransferBridgeFactory.ItemStackView item, int slot, String side, boolean simulate) {
            if (slot < 0 || slot >= slots.size()) return 0;
            TransferBridgeFactory.ItemStackView current = slots.get(slot);
            if (!current.isEmpty() && current.matches(item)) {
                int toExtract = Math.min(item.count(), current.count());
                if (!simulate && toExtract > 0) {
                    int remaining = current.count() - toExtract;
                    slots.set(slot, new TransferBridgeFactory.ItemStackView(remaining <= 0 ? "minecraft:air" : current.itemId(), remaining));
                }
                return toExtract;
            }
            return 0;
        }
    }

    @Test
    @DisplayName("Sided filter correctly allows and denies items")
    void sidedFilterAllowsAndDeniesItems() {
        UniversalAutomationEngine.SidedFilter filter = UniversalAutomationEngine.SidedFilter.allowOnly(
            "up",
            List.of("minecraft:iron_ore", "minecraft:gold_ore")
        );

        assertTrue(filter.allows(new TransferBridgeFactory.ItemStackView("minecraft:iron_ore", 10), "up"));
        assertFalse(filter.allows(new TransferBridgeFactory.ItemStackView("minecraft:iron_ore", 10), "down"));
        assertFalse(filter.allows(new TransferBridgeFactory.ItemStackView("minecraft:dirt", 10), "up"));
    }

    @Test
    @DisplayName("NetworkRouter successfully transfers items between source and destination")
    void networkRouterExecutesTransferCycle() {
        Identifier srcId = Identifier.parse("test:source");
        Identifier destId = Identifier.parse("test:destination");

        MockTestTransferBridge srcBridge = new MockTestTransferBridge(2, new TransferBridgeFactory.ItemStackView("minecraft:coal", 16));
        MockTestTransferBridge destBridge = new MockTestTransferBridge(2, new TransferBridgeFactory.ItemStackView("minecraft:air", 0));

        UniversalAutomationEngine.RouteNode srcNode = new UniversalAutomationEngine.RouteNode(
            srcId,
            0,
            UniversalAutomationEngine.SidedFilter.allowAll(),
            srcBridge,
            "up"
        );

        UniversalAutomationEngine.RouteNode destNode = new UniversalAutomationEngine.RouteNode(
            destId,
            10,
            UniversalAutomationEngine.SidedFilter.allowAll(),
            destBridge,
            "down"
        );

        UniversalAutomationEngine.TransferRoute route = new UniversalAutomationEngine.TransferRoute(
            "route-1",
            srcNode,
            List.of(destNode),
            8
        );

        UniversalAutomationEngine.NetworkRouter router = new UniversalAutomationEngine.NetworkRouter();
        router.registerRoute(route);

        UniversalAutomationEngine.AutomationCycleReport report = router.executeCycle("route-1", false);
        assertTrue(report.success());
        assertEquals(8, report.movedTransfer());
        assertEquals(8, srcBridge.itemAt(srcId, 0).count());
        assertEquals(8, destBridge.itemAt(destId, 0).count());
    }

    @Test
    @DisplayName("NetworkRouter invalidates routes when connected node is destroyed or unloaded")
    void networkRouterInvalidatesOnNodeDisruption() {
        Identifier srcId = Identifier.parse("test:source_node");
        Identifier destId1 = Identifier.parse("test:dest_node_1");
        Identifier destId2 = Identifier.parse("test:dest_node_2");

        MockTestTransferBridge bridge = new MockTestTransferBridge(1, new TransferBridgeFactory.ItemStackView("minecraft:iron_ingot", 10));

        UniversalAutomationEngine.RouteNode src = new UniversalAutomationEngine.RouteNode(srcId, 0, UniversalAutomationEngine.SidedFilter.allowAll(), bridge, null);
        UniversalAutomationEngine.RouteNode d1 = new UniversalAutomationEngine.RouteNode(destId1, 1, UniversalAutomationEngine.SidedFilter.allowAll(), bridge, null);
        UniversalAutomationEngine.RouteNode d2 = new UniversalAutomationEngine.RouteNode(destId2, 2, UniversalAutomationEngine.SidedFilter.allowAll(), bridge, null);

        UniversalAutomationEngine.TransferRoute r1 = new UniversalAutomationEngine.TransferRoute("dim:overworld:r1", src, List.of(d1), 4);
        UniversalAutomationEngine.TransferRoute r2 = new UniversalAutomationEngine.TransferRoute("dim:nether:r2", src, List.of(d2), 4);

        UniversalAutomationEngine.NetworkRouter router = new UniversalAutomationEngine.NetworkRouter();
        router.registerRoute(r1);
        router.registerRoute(r2);
        assertEquals(2, router.routeCount());

        // Invalidate dest_node_1 -> r1 removed, r2 remains
        router.invalidateNode(destId1);
        assertEquals(1, router.routeCount());
        assertNull(router.getRoute("dim:overworld:r1"));
        assertNotNull(router.getRoute("dim:nether:r2"));

        // Invalidate dimension "dim:nether" -> r2 removed
        router.invalidateRoutesForDimension("dim:nether");
        assertEquals(0, router.routeCount());
    }
}
