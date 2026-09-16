package org.geysermc.hydraulic.compat.runtime;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiResourceTransactionTest {
    private static final Identifier MACHINE = Identifier.fromNamespaceAndPath("hydraulic", "mixed_machine");

    @Test
    void commitsItemFluidEnergyBatchAndRecordsOneDirtyChangeSet() {
        Fixture fixture = Fixture.ready();
        DirtyStateTracker dirty = new DirtyStateTracker();

        TransferResult result = fixture.transaction().execute(dirty);
        List<EncodedSyncChange> encoded = new SyncEncoder().encode(new SyncPlanner().plan(dirty.drain()));

        assertTrue(result.committed());
        assertEquals(804, result.moved());
        assertEquals(1, fixture.items.slot(0).count());
        assertEquals(1, fixture.items.slot(1).count());
        assertEquals("minecraft:iron_ingot", fixture.items.slot(2).itemId());
        assertEquals("minecraft:copper_ingot", fixture.items.slot(3).itemId());
        assertEquals(500, fixture.fluids.tank(0).amount());
        assertEquals(250, fixture.fluids.tank(1).amount());
        assertEquals(50, fixture.energy.energy);
        assertEquals(7, encoded.size());
        assertEquals(4, encoded.stream().filter(change -> change.kind() == EncodedSyncKind.INVENTORY_SLOT).count());
    }

    @Test
    void missingItemRejectsEverythingBeforeMutation() {
        Fixture fixture = Fixture.ready();
        fixture.items.slots[1] = new TransferBridgeFactory.ItemStackView("minecraft:air", 0);

        TransferResult result = fixture.transaction().execute(new DirtyStateTracker());

        assertRejectedWithoutMutation(result, fixture, 2, 0, 1000, 0, 100);
    }

    @Test
    void missingFluidRejectsEverythingBeforeMutation() {
        Fixture fixture = Fixture.ready();
        fixture.fluids.tanks[0] = new TransferBridgeFactory.FluidStackView("minecraft:empty", 0);

        TransferResult result = fixture.transaction().execute(new DirtyStateTracker());

        assertRejectedWithoutMutation(result, fixture, 2, 2, 0, 0, 100);
    }

    @Test
    void insufficientEnergyRejectsEverythingBeforeMutation() {
        Fixture fixture = Fixture.ready();
        fixture.energy.energy = 10;

        TransferResult result = fixture.transaction().execute(new DirtyStateTracker());

        assertRejectedWithoutMutation(result, fixture, 2, 2, 1000, 0, 10);
    }

    @Test
    void blockedOutputRejectsEverythingBeforeMutation() {
        Fixture fixture = Fixture.ready();
        fixture.items.slots[2] = new TransferBridgeFactory.ItemStackView("minecraft:diamond", 1);

        TransferResult result = fixture.transaction().execute(new DirtyStateTracker());

        assertRejectedWithoutMutation(result, fixture, 2, 2, 1000, 0, 100);
        assertEquals("minecraft:diamond", fixture.items.slot(2).itemId());
    }

    private static void assertRejectedWithoutMutation(
        TransferResult result,
        Fixture fixture,
        int slot0Count,
        int slot1Count,
        int tank0Amount,
        int tank1Amount,
        int energy
    ) {
        assertFalse(result.committed());
        assertTrue(result.stateChanges().changes().isEmpty());
        assertEquals(slot0Count, fixture.items.slot(0).count());
        assertEquals(slot1Count, fixture.items.slot(1).count());
        assertEquals(tank0Amount, fixture.fluids.tank(0).amount());
        assertEquals(tank1Amount, fixture.fluids.tank(1).amount());
        assertEquals(energy, fixture.energy.energy);
    }

    private record Fixture(
        TestItemBridge items,
        TestFluidBridge fluids,
        TestEnergyBridge energy
    ) {
        private static Fixture ready() {
            return new Fixture(
                new TestItemBridge(
                    new TransferBridgeFactory.ItemStackView("minecraft:stone", 2),
                    new TransferBridgeFactory.ItemStackView("minecraft:coal", 2),
                    new TransferBridgeFactory.ItemStackView("minecraft:air", 0),
                    new TransferBridgeFactory.ItemStackView("minecraft:air", 0)
                ),
                new TestFluidBridge(
                    new TransferBridgeFactory.FluidStackView("minecraft:water", 1000),
                    new TransferBridgeFactory.FluidStackView("minecraft:empty", 0)
                ),
                new TestEnergyBridge(100, 1000)
            );
        }

        private MultiResourceTransaction transaction() {
            return new MultiResourceTransaction()
                .addItem(this.items, new TransferRequest(MACHINE, TransferDirection.EXTRACT, new TransferBridgeFactory.ItemStackView("minecraft:stone", 1), 0, null))
                .addItem(this.items, new TransferRequest(MACHINE, TransferDirection.EXTRACT, new TransferBridgeFactory.ItemStackView("minecraft:coal", 1), 1, null))
                .addFluid(this.fluids, new FluidTransferRequest(MACHINE, TransferDirection.EXTRACT, new TransferBridgeFactory.FluidStackView("minecraft:water", 500), 0, null))
                .addEnergy(this.energy, new EnergyTransferRequest(MACHINE, TransferDirection.EXTRACT, 50, null))
                .addItem(this.items, new TransferRequest(MACHINE, TransferDirection.INSERT, new TransferBridgeFactory.ItemStackView("minecraft:iron_ingot", 1), 2, null))
                .addItem(this.items, new TransferRequest(MACHINE, TransferDirection.INSERT, new TransferBridgeFactory.ItemStackView("minecraft:copper_ingot", 1), 3, null))
                .addFluid(this.fluids, new FluidTransferRequest(MACHINE, TransferDirection.INSERT, new TransferBridgeFactory.FluidStackView("minecraft:steam", 250), 1, null));
        }
    }

    private static final class TestItemBridge implements TransferBridgeFactory.ItemTransferBridge {
        private final TransferBridgeFactory.ItemStackView[] slots;

        private TestItemBridge(TransferBridgeFactory.ItemStackView... slots) {
            this.slots = slots;
        }

        private TransferBridgeFactory.ItemStackView slot(int index) {
            return this.slots[index];
        }

        @Override
        public boolean executable() {
            return true;
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
            return "machine";
        }

        @Override
        public int slotCount(Identifier blockIdentifier) {
            return this.slots.length;
        }

        @Override
        public TransferBridgeFactory.ItemStackView itemAt(Identifier blockIdentifier, int slot) {
            return this.slots[slot];
        }

        @Override
        public int insert(Identifier blockIdentifier, TransferBridgeFactory.ItemStackView item, int slot, String side, boolean simulate) {
            TransferBridgeFactory.ItemStackView current = this.slots[slot];
            if (!current.isEmpty() && !current.matches(item)) {
                return 0;
            }
            int moved = Math.min(item.count(), 64 - current.count());
            if (!simulate && moved > 0) {
                this.slots[slot] = new TransferBridgeFactory.ItemStackView(item.itemId(), current.count() + moved);
            }
            return moved;
        }

        @Override
        public int extract(Identifier blockIdentifier, TransferBridgeFactory.ItemStackView item, int slot, String side, boolean simulate) {
            TransferBridgeFactory.ItemStackView current = this.slots[slot];
            if (!current.matches(item)) {
                return 0;
            }
            int moved = Math.min(item.count(), current.count());
            if (!simulate && moved > 0) {
                int remaining = current.count() - moved;
                this.slots[slot] = remaining == 0
                    ? new TransferBridgeFactory.ItemStackView("minecraft:air", 0)
                    : new TransferBridgeFactory.ItemStackView(current.itemId(), remaining);
            }
            return moved;
        }
    }

    private static final class TestFluidBridge implements TransferBridgeFactory.FluidTransferBridge {
        private final TransferBridgeFactory.FluidStackView[] tanks;

        private TestFluidBridge(TransferBridgeFactory.FluidStackView... tanks) {
            this.tanks = tanks;
        }

        private TransferBridgeFactory.FluidStackView tank(int index) {
            return this.tanks[index];
        }

        @Override
        public boolean executable() {
            return true;
        }

        @Override
        public boolean canInsertFluid(Identifier blockIdentifier) {
            return true;
        }

        @Override
        public boolean canExtractFluid(Identifier blockIdentifier) {
            return true;
        }

        @Override
        public String tankType(Identifier blockIdentifier) {
            return "machine";
        }

        @Override
        public int tankCount(Identifier blockIdentifier) {
            return this.tanks.length;
        }

        @Override
        public int tankCapacity(Identifier blockIdentifier, int tank) {
            return 1000;
        }

        @Override
        public TransferBridgeFactory.FluidStackView tankAt(Identifier blockIdentifier, int tank) {
            return this.tanks[tank];
        }

        @Override
        public int insertFluid(Identifier blockIdentifier, TransferBridgeFactory.FluidStackView fluid, int tank, String side, boolean simulate) {
            TransferBridgeFactory.FluidStackView current = this.tanks[tank];
            if (current.amount() > 0 && !current.fluidId().equals(fluid.fluidId())) {
                return 0;
            }
            int moved = Math.min(fluid.amount(), 1000 - current.amount());
            if (!simulate && moved > 0) {
                this.tanks[tank] = new TransferBridgeFactory.FluidStackView(fluid.fluidId(), current.amount() + moved);
            }
            return moved;
        }

        @Override
        public int extractFluid(Identifier blockIdentifier, TransferBridgeFactory.FluidStackView fluid, int tank, String side, boolean simulate) {
            TransferBridgeFactory.FluidStackView current = this.tanks[tank];
            if (!current.fluidId().equals(fluid.fluidId())) {
                return 0;
            }
            int moved = Math.min(fluid.amount(), current.amount());
            if (!simulate && moved > 0) {
                int remaining = current.amount() - moved;
                this.tanks[tank] = remaining == 0
                    ? new TransferBridgeFactory.FluidStackView("minecraft:empty", 0)
                    : new TransferBridgeFactory.FluidStackView(current.fluidId(), remaining);
            }
            return moved;
        }
    }

    private static final class TestEnergyBridge implements TransferBridgeFactory.EnergyTransferBridge {
        private int energy;
        private final int capacity;

        private TestEnergyBridge(int energy, int capacity) {
            this.energy = energy;
            this.capacity = capacity;
        }

        @Override
        public boolean executable() {
            return true;
        }

        @Override
        public boolean canReceiveEnergy(Identifier blockIdentifier) {
            return true;
        }

        @Override
        public boolean canProvideEnergy(Identifier blockIdentifier) {
            return true;
        }

        @Override
        public String energyType(Identifier blockIdentifier) {
            return "forge_energy";
        }

        @Override
        public int getEnergyStored(Identifier blockIdentifier) {
            return this.energy;
        }

        @Override
        public int getMaxEnergy(Identifier blockIdentifier) {
            return this.capacity;
        }

        @Override
        public int receiveEnergy(Identifier blockIdentifier, int amount, String side, boolean simulate) {
            int moved = Math.min(amount, this.capacity - this.energy);
            if (!simulate) {
                this.energy += moved;
            }
            return moved;
        }

        @Override
        public int extractEnergy(Identifier blockIdentifier, int amount, String side, boolean simulate) {
            int moved = Math.min(amount, this.energy);
            if (!simulate) {
                this.energy -= moved;
            }
            return moved;
        }
    }
}
