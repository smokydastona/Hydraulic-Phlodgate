package org.geysermc.hydraulic.compat.energy;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UniversalEnergyRuntimeTest {

    @Test
    @DisplayName("Energy storage unit bounds capacity and stored energy")
    void energyStorageUnitBoundsConstraints() {
        UniversalEnergyRuntime.EnergyStorageUnit unit = new UniversalEnergyRuntime.EnergyStorageUnit(
            Identifier.parse("test:battery"),
            UniversalEnergyRuntime.EnergyKind.FORGE_ENERGY,
            5000,
            10000,
            100,
            100,
            true,
            true
        );

        assertEquals(5000, unit.energyStored());
        assertEquals(10000, unit.capacity());
        assertEquals(5000, unit.remainingCapacity());
        assertFalse(unit.isFull());
        assertFalse(unit.isEmpty());
    }

    @Test
    @DisplayName("Energy network distributes power from sources to sinks")
    void energyNetworkDistributesPower() {
        UniversalEnergyRuntime.EnergyNetwork network = new UniversalEnergyRuntime.EnergyNetwork("net-1", 500);

        UniversalEnergyRuntime.EnergyStorageUnit source = new UniversalEnergyRuntime.EnergyStorageUnit(
            Identifier.parse("test:generator"),
            UniversalEnergyRuntime.EnergyKind.FORGE_ENERGY,
            1000,
            1000,
            0,
            200,
            false,
            true
        );

        UniversalEnergyRuntime.EnergyStorageUnit sink = new UniversalEnergyRuntime.EnergyStorageUnit(
            Identifier.parse("test:machine"),
            UniversalEnergyRuntime.EnergyKind.FORGE_ENERGY,
            0,
            1000,
            200,
            0,
            true,
            false
        );

        network.addSource(source);
        network.addSink(sink);

        UniversalEnergyRuntime.EnergyNetwork.NetworkDistributionReport report = network.distributePower(false);
        assertEquals(1000, report.totalEnergyAvailable());
        assertEquals(1000, report.totalEnergyDemand());
        assertEquals(500, report.totalEnergyTransferred()); // Capped by throughput limit
        assertEquals(1, report.activeSources());
        assertEquals(1, report.activeSinks());
    }
}
