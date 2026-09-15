package org.geysermc.hydraulic.compat.runtime;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FluidContainerBridge {
    private final TransferBridgeFactory.FluidTransferBridge tank;
    private final int tankIndex;
    private final int containerCapacity;

    public FluidContainerBridge(
        @NotNull TransferBridgeFactory.FluidTransferBridge tank,
        int tankIndex,
        int containerCapacity
    ) {
        if (tankIndex < 0 || containerCapacity <= 0) {
            throw new IllegalArgumentException("Fluid container dimensions must be positive");
        }
        this.tank = tank;
        this.tankIndex = tankIndex;
        this.containerCapacity = containerCapacity;
    }

    public int containerCapacity() {
        return this.containerCapacity;
    }

    @Nullable
    public TransferBridgeFactory.FluidStackView tankState(@NotNull Identifier blockIdentifier) {
        return this.tank.tankAt(blockIdentifier, this.tankIndex);
    }

    public int transferToTank(
        @NotNull Identifier blockIdentifier,
        @NotNull ContainerState container,
        @Nullable String side,
        boolean simulate
    ) {
        if (container.isEmpty()) {
            return 0;
        }
        TransferBridgeFactory.FluidStackView existing = this.tank.tankAt(blockIdentifier, this.tankIndex);
        if (existing != null && !isEmpty(existing) && !container.fluidId().equals(existing.fluidId())) {
            return 0;
        }
        TransferBridgeFactory.OperationResult result = this.tank.insertFluidResult(
            blockIdentifier,
            new TransferBridgeFactory.FluidStackView(container.fluidId(), Math.min(container.amount(), this.containerCapacity)),
            this.tankIndex,
            side,
            simulate
        );
        int moved = result.moved();
        if (!simulate) {
            container.remove(moved);
        }
        return moved;
    }

    public int transferFromTank(
        @NotNull Identifier blockIdentifier,
        @NotNull ContainerState container,
        @NotNull String fluidId,
        @Nullable String side,
        boolean simulate
    ) {
        if (!container.canAccept(fluidId)) {
            return 0;
        }
        TransferBridgeFactory.FluidStackView existing = this.tank.tankAt(blockIdentifier, this.tankIndex);
        if (existing == null || isEmpty(existing) || !fluidId.equals(existing.fluidId())) {
            return 0;
        }
        int requested = Math.min(container.remainingCapacity(), this.containerCapacity);
        if (requested <= 0) {
            return 0;
        }
        TransferBridgeFactory.OperationResult result = this.tank.extractFluidResult(
            blockIdentifier,
            new TransferBridgeFactory.FluidStackView(fluidId, requested),
            this.tankIndex,
            side,
            simulate
        );
        int moved = result.moved();
        if (!simulate) {
            container.add(fluidId, moved);
        }
        return moved;
    }

    private static boolean isEmpty(@NotNull TransferBridgeFactory.FluidStackView fluid) {
        return fluid.amount() <= 0 || "minecraft:empty".equals(fluid.fluidId());
    }

    public static final class ContainerState {
        private String fluidId;
        private int amount;
        private final int capacity;

        public ContainerState(int capacity) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("Fluid container capacity must be positive");
            }
            this.capacity = capacity;
        }

        @Nullable
        public String fluidId() {
            return this.fluidId;
        }

        public int amount() {
            return this.amount;
        }

        public int remainingCapacity() {
            return this.capacity - this.amount;
        }

        public boolean isEmpty() {
            return this.amount <= 0 || this.fluidId == null;
        }

        private boolean canAccept(@NotNull String fluidId) {
            return this.isEmpty() || this.fluidId.equals(fluidId);
        }

        public void fill(@NotNull String fluidId, int amount) {
            if (amount <= 0) {
                return;
            }
            if (!canAccept(fluidId) || this.amount + amount > this.capacity) {
                throw new IllegalStateException("Fluid container cannot accept the transferred stack");
            }
            this.fluidId = fluidId;
            this.amount += amount;
        }

        private void add(@NotNull String fluidId, int amount) {
            if (amount > 0) {
                fill(fluidId, amount);
            }
        }

        private void remove(int amount) {
            if (amount < 0 || amount > this.amount) {
                throw new IllegalStateException("Fluid container cannot remove the transferred amount");
            }
            this.amount -= amount;
            if (this.amount == 0) {
                this.fluidId = null;
            }
        }
    }
}
