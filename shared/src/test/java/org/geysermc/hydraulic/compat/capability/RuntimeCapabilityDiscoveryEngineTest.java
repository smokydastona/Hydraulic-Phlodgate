package org.geysermc.hydraulic.compat.capability;

import org.geysermc.hydraulic.compat.runtime.TransferBridgeFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeCapabilityDiscoveryEngineTest {

    // Test mock target classes
    public static class SampleItemContainer {
        public int getContainerSize() {
            return 9;
        }

        public TransferBridgeFactory.ItemStackView getItem(int slot) {
            return new TransferBridgeFactory.ItemStackView("minecraft:stone", 64);
        }

        public int insertItem(int slot, TransferBridgeFactory.ItemStackView item, String side, boolean simulate) {
            return item.count();
        }

        public int extractItem(int slot, TransferBridgeFactory.ItemStackView item, int count, String side, boolean simulate) {
            return count;
        }
    }

    public static class SampleFluidTank {
        public int getTanks() {
            return 2;
        }

        public int getTankCapacity(int tank) {
            return 4000;
        }

        public TransferBridgeFactory.FluidStackView getFluid(int tank) {
            return new TransferBridgeFactory.FluidStackView("minecraft:water", 2000);
        }

        public int fill(int tank, TransferBridgeFactory.FluidStackView fluid, boolean simulate) {
            return fluid.amount();
        }

        public int drain(int tank, int amount, boolean simulate) {
            return amount;
        }
    }

    public static class SampleEnergyStorage {
        public int getEnergyStored() {
            return 50000;
        }

        public int getMaxEnergyStored() {
            return 100000;
        }

        public int receiveEnergy(int amount, boolean simulate) {
            return amount;
        }

        public int extractEnergy(int amount, boolean simulate) {
            return amount;
        }
    }

    public static class SampleComplexMachine extends SampleItemContainer {
        public int getProgress() {
            return 50;
        }

        public int getMaxProgress() {
            return 100;
        }

        public boolean isProcessing() {
            return true;
        }

        public void serverTick() {
        }
    }

    @Test
    @DisplayName("Discover item transfer capabilities on live Java object")
    void discoverItemTransferCapabilities() {
        SampleItemContainer container = new SampleItemContainer();
        RuntimeCapabilityDiscoveryEngine.DiscoveredCapabilities discovered = RuntimeCapabilityDiscoveryEngine.discover(container);

        assertTrue(discovered.hasItemTransfer());
        assertFalse(discovered.hasFluidTransfer());
        assertFalse(discovered.hasEnergyTransfer());
        assertEquals(9, discovered.detectedSlotCount());
        assertTrue(discovered.hasAnyTransfer());
        assertFalse(discovered.isComplexMachine());
    }

    @Test
    @DisplayName("Discover fluid transfer capabilities on live Java object")
    void discoverFluidTransferCapabilities() {
        SampleFluidTank tank = new SampleFluidTank();
        RuntimeCapabilityDiscoveryEngine.DiscoveredCapabilities discovered = RuntimeCapabilityDiscoveryEngine.discover(tank);

        assertFalse(discovered.hasItemTransfer());
        assertTrue(discovered.hasFluidTransfer());
        assertFalse(discovered.hasEnergyTransfer());
        assertEquals(2, discovered.detectedTankCount());
        assertEquals("4000", discovered.capabilityAttributes().get("fluid_capacity"));
    }

    @Test
    @DisplayName("Discover energy transfer capabilities on live Java object")
    void discoverEnergyTransferCapabilities() {
        SampleEnergyStorage energy = new SampleEnergyStorage();
        RuntimeCapabilityDiscoveryEngine.DiscoveredCapabilities discovered = RuntimeCapabilityDiscoveryEngine.discover(energy);

        assertFalse(discovered.hasItemTransfer());
        assertFalse(discovered.hasFluidTransfer());
        assertTrue(discovered.hasEnergyTransfer());
        assertEquals(100000, discovered.detectedEnergyCapacity());
        assertEquals("50000", discovered.capabilityAttributes().get("energy_stored"));
    }

    @Test
    @DisplayName("Discover complex processing machine capabilities")
    void discoverComplexMachineCapabilities() {
        SampleComplexMachine machine = new SampleComplexMachine();
        RuntimeCapabilityDiscoveryEngine.DiscoveredCapabilities discovered = RuntimeCapabilityDiscoveryEngine.discover(machine);

        assertTrue(discovered.hasItemTransfer());
        assertTrue(discovered.hasMachineProcessing());
        assertTrue(discovered.isComplexMachine());
        assertEquals("true", discovered.capabilityAttributes().get("has_processing_contract"));
    }

    @Test
    @DisplayName("Gracefully handle null target with zero crash")
    void handleNullTargetGracefully() {
        RuntimeCapabilityDiscoveryEngine.DiscoveredCapabilities discovered = RuntimeCapabilityDiscoveryEngine.discover(null);
        assertNotNull(discovered);
        assertFalse(discovered.hasItemTransfer());
        assertFalse(discovered.hasFluidTransfer());
        assertFalse(discovered.hasEnergyTransfer());
        assertFalse(discovered.hasMachineProcessing());
    }
}
