package org.geysermc.hydraulic.compat.machine;

import net.minecraft.resources.Identifier;
import org.geysermc.hydraulic.compat.runtime.TransferBridgeFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UniversalMachineRuntimeTest {

    static class MockMachineItemBridge implements TransferBridgeFactory.ItemTransferBridge {
        private final List<TransferBridgeFactory.ItemStackView> slots = new ArrayList<>();

        public MockMachineItemBridge(TransferBridgeFactory.ItemStackView inputSlot, TransferBridgeFactory.ItemStackView outputSlot) {
            slots.add(inputSlot);
            slots.add(outputSlot);
        }

        @Override public boolean canInsert(Identifier id) { return true; }
        @Override public boolean canExtract(Identifier id) { return true; }
        @Override public String inventoryType(Identifier id) { return "machine"; }
        @Override public int slotCount(Identifier id) { return slots.size(); }

        @Override
        public TransferBridgeFactory.ItemStackView itemAt(Identifier id, int slot) {
            return slots.get(slot);
        }

        @Override
        public int insert(Identifier id, TransferBridgeFactory.ItemStackView item, int slot, String side, boolean simulate) {
            TransferBridgeFactory.ItemStackView current = slots.get(slot);
            if (current.isEmpty() || current.matches(item)) {
                int toInsert = Math.min(item.count(), 64 - current.count());
                if (!simulate && toInsert > 0) {
                    slots.set(slot, new TransferBridgeFactory.ItemStackView(item.itemId(), current.count() + toInsert));
                }
                return toInsert;
            }
            return 0;
        }

        @Override
        public int extract(Identifier id, TransferBridgeFactory.ItemStackView item, int slot, String side, boolean simulate) {
            TransferBridgeFactory.ItemStackView current = slots.get(slot);
            if (!current.isEmpty() && current.matches(item)) {
                int toExtract = Math.min(item.count(), current.count());
                if (!simulate && toExtract > 0) {
                    int rem = current.count() - toExtract;
                    slots.set(slot, new TransferBridgeFactory.ItemStackView(rem <= 0 ? "minecraft:air" : current.itemId(), rem));
                }
                return toExtract;
            }
            return 0;
        }
    }

    static class MockMachineEnergyBridge implements TransferBridgeFactory.EnergyTransferBridge {
        private int stored;
        public MockMachineEnergyBridge(int stored) { this.stored = stored; }
        @Override public boolean canReceiveEnergy(Identifier id) { return true; }
        @Override public boolean canProvideEnergy(Identifier id) { return true; }
        @Override public String energyType(Identifier id) { return "FE"; }
        @Override public int getEnergyStored(Identifier id) { return stored; }
        @Override public int getMaxEnergy(Identifier id) { return 10000; }
        @Override public int receiveEnergy(Identifier id, int amount, String side, boolean simulate) { return 0; }
        @Override
        public int extractEnergy(Identifier id, int amount, String side, boolean simulate) {
            int toExtract = Math.min(stored, amount);
            if (!simulate) {
                stored -= toExtract;
            }
            return toExtract;
        }
    }

    @Test
    @DisplayName("Machine execution context ticks recipe to completion")
    void machineExecutionContextTicksRecipe() {
        Identifier machineId = Identifier.parse("test:furnace");
        UniversalMachineRuntime.MachineExecutionContext ctx = new UniversalMachineRuntime.MachineExecutionContext(machineId);

        UniversalMachineRuntime.UniversalRecipe recipe = new UniversalMachineRuntime.UniversalRecipe(
            "smelt_iron",
            List.of(new TransferBridgeFactory.ItemStackView("minecraft:raw_iron", 1)),
            List.of(),
            10,
            3,
            List.of(new TransferBridgeFactory.ItemStackView("minecraft:iron_ingot", 1)),
            List.of(),
            0
        );
        ctx.registerRecipe(recipe);

        MockMachineItemBridge itemBridge = new MockMachineItemBridge(
            new TransferBridgeFactory.ItemStackView("minecraft:raw_iron", 2),
            new TransferBridgeFactory.ItemStackView("minecraft:air", 0)
        );
        MockMachineEnergyBridge energyBridge = new MockMachineEnergyBridge(100);

        // Tick 1
        ctx.tick(itemBridge, null, energyBridge);
        assertEquals(UniversalMachineRuntime.MachineState.RUNNING, ctx.state());
        assertEquals(1, ctx.currentProgressTicks());

        // Tick 2
        ctx.tick(itemBridge, null, energyBridge);
        assertEquals(UniversalMachineRuntime.MachineState.RUNNING, ctx.state());
        assertEquals(2, ctx.currentProgressTicks());

        // Tick 3 (Complete)
        ctx.tick(itemBridge, null, energyBridge);
        assertEquals(UniversalMachineRuntime.MachineState.COMPLETE, ctx.state());
        assertEquals(1, itemBridge.itemAt(machineId, 0).count()); // 1 raw iron consumed
        assertEquals(1, itemBridge.itemAt(machineId, 1).count()); // 1 iron ingot emitted
        assertEquals("minecraft:iron_ingot", itemBridge.itemAt(machineId, 1).itemId());
    }
}
