package org.geysermc.hydraulic.compat.capability;

import org.geysermc.hydraulic.compat.CompatibilityStatus;
import org.geysermc.hydraulic.compat.ir.CompiledCompatibilityPlan;
import org.geysermc.hydraulic.compat.model.Confidence;
import org.geysermc.hydraulic.compat.model.SupportLevel;
import org.geysermc.hydraulic.compat.runtime.RuntimeBridgeKind;
import org.geysermc.hydraulic.compat.runtime.TransferBridgeFactory;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DynamicCapabilityBinderTest {

    public static class DynamicTestInventory {
        public int getContainerSize() {
            return 9;
        }

        public TransferBridgeFactory.ItemStackView getItem(int slot) {
            return new TransferBridgeFactory.ItemStackView("minecraft:iron_ore", 16);
        }

        public int insertItem(int slot, TransferBridgeFactory.ItemStackView item, String side, boolean simulate) {
            return item.count();
        }

        public int extractItem(int slot, TransferBridgeFactory.ItemStackView item, int count, String side, boolean simulate) {
            return count;
        }
    }

    @Test
    @DisplayName("DynamicCapabilityBinder binds item bridge from compiled plan and live target")
    void dynamicCapabilityBinderBindsItemBridge() {
        CompiledCompatibilityPlan plan = new CompiledCompatibilityPlan(
            "test_mod",
            "block",
            "test_mod:furnace",
            "test_mod:furnace",
            SupportLevel.ADAPTED,
            CompatibilityStatus.COMPLETE,
            90,
            new Confidence(0.95, "test"),
            List.of(),
            List.of("item_transfer_bridge"),
            List.of(RuntimeBridgeKind.ITEM_TRANSFER),
            Map.of("can_insert", "true", "can_extract", "true"),
            true,
            null,
            true,
            null,
            true,
            true,
            false,
            false,
            true,
            ContainerType.FURNACE,
            null,
            List.of(),
            List.of(),
            List.of(),
            null,
            false,
            false,
            SupportLevel.ADAPTED,
            "furnace",
            List.of()
        );

        DynamicTestInventory target = new DynamicTestInventory();
        DynamicCapabilityBinder.BoundBridges bound = DynamicCapabilityBinder.bind(plan, target);

        assertNotNull(bound);
        assertNotNull(bound.itemTransferBridge());
        assertTrue(bound.boundBridgeKinds().contains(RuntimeBridgeKind.ITEM_TRANSFER));
        assertTrue(bound.boundBridgeKinds().contains(RuntimeBridgeKind.MENU_CONTAINER));
        assertEquals(ContainerType.FURNACE, bound.menuFallbackType());
        assertTrue(bound.isFullyBound());
    }
}
