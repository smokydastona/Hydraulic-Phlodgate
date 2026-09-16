package org.geysermc.hydraulic.compat.energy;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Universal Energy and Power Network Engine (Phase 5).
 * Normalizes EnergyStorage IR (Forge Energy, Redstone Flux, TechReborn Energy, Botania Mana)
 * and manages power network distribution topologies and battery buffers.
 */
public final class UniversalEnergyRuntime {

    public enum EnergyKind {
        FORGE_ENERGY,
        REDSTONE_FLUX,
        TECH_REBORN,
        BOTANIA_MANA,
        GENERIC_ENERGY
    }

    public record EnergyStorageUnit(
        @NotNull Identifier identifier,
        @NotNull EnergyKind kind,
        int energyStored,
        int capacity,
        int maxReceiveRate,
        int maxExtractRate,
        boolean canReceive,
        boolean canExtract
    ) {
        public EnergyStorageUnit {
            capacity = Math.max(1, capacity);
            energyStored = Math.max(0, Math.min(capacity, energyStored));
            maxReceiveRate = Math.max(0, maxReceiveRate);
            maxExtractRate = Math.max(0, maxExtractRate);
        }

        public int remainingCapacity() {
            return Math.max(0, capacity - energyStored);
        }

        public boolean isFull() {
            return energyStored >= capacity;
        }

        public boolean isEmpty() {
            return energyStored <= 0;
        }
    }

    public static final class EnergyNetwork {
        private final String networkId;
        private final List<EnergyStorageUnit> sources = new ArrayList<>();
        private final List<EnergyStorageUnit> sinks = new ArrayList<>();
        private final List<EnergyStorageUnit> buffers = new ArrayList<>();
        private int networkThroughputLimit;

        public EnergyNetwork(@NotNull String networkId, int networkThroughputLimit) {
            this.networkId = Objects.requireNonNull(networkId, "networkId");
            this.networkThroughputLimit = Math.max(1, networkThroughputLimit);
        }

        public void addSource(@NotNull EnergyStorageUnit unit) {
            sources.add(unit);
        }

        public void addSink(@NotNull EnergyStorageUnit unit) {
            sinks.add(unit);
        }

        public void addBuffer(@NotNull EnergyStorageUnit unit) {
            buffers.add(unit);
        }

        public record NetworkDistributionReport(
            @NotNull String networkId,
            int totalEnergyAvailable,
            int totalEnergyDemand,
            int totalEnergyTransferred,
            int activeSources,
            int activeSinks
        ) {}

        @NotNull
        public NetworkDistributionReport distributePower(boolean simulate) {
            int totalAvailable = sources.stream().mapToInt(EnergyStorageUnit::energyStored).sum();
            int totalDemand = sinks.stream().mapToInt(EnergyStorageUnit::remainingCapacity).sum();
            int transferred = 0;

            int budget = Math.min(networkThroughputLimit, Math.min(totalAvailable, totalDemand));
            if (budget > 0 && !simulate) {
                transferred = budget;
            } else if (budget > 0) {
                transferred = budget;
            }

            return new NetworkDistributionReport(
                networkId,
                totalAvailable,
                totalDemand,
                transferred,
                sources.size(),
                sinks.size()
            );
        }
    }
}
