package org.geysermc.hydraulic.compat.runtime;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FluidContainerBridgeTest {
    private static final Identifier MACHINE = Identifier.fromNamespaceAndPath("hydraulic", "fluid_machine");

    @Test
    void movesFluidOnlyWhenTankIdentityMatches() {
        MutableTank tank = new MutableTank("minecraft:water", 500, 1_000);
        FluidContainerBridge.ContainerState container = new FluidContainerBridge.ContainerState(1_000);
        container.fill("minecraft:water", 500);

        int moved = new FluidContainerBridge(tank, 0, 1_000).transferToTank(MACHINE, container, "up", false);

        assertEquals(500, moved);
        assertEquals(1_000, tank.amount);
        assertEquals(0, container.amount());
    }

    @Test
    void rejectsCrossFluidTransferWithoutMutatingContainer() {
        MutableTank tank = new MutableTank("minecraft:lava", 500, 1_000);
        FluidContainerBridge.ContainerState container = new FluidContainerBridge.ContainerState(1_000);
        container.fill("minecraft:water", 500);

        int moved = new FluidContainerBridge(tank, 0, 1_000).transferToTank(MACHINE, container, null, false);

        assertEquals(0, moved);
        assertEquals(500, tank.amount);
        assertEquals(500, container.amount());
        assertEquals("minecraft:water", container.fluidId());
    }

    private static final class MutableTank implements TransferBridgeFactory.FluidTransferBridge {
        private String fluidId;
        private int amount;
        private final int capacity;

        private MutableTank(String fluidId, int amount, int capacity) {
            this.fluidId = fluidId;
            this.amount = amount;
            this.capacity = capacity;
        }

        @Override
        public boolean executable() {
            return true;
        }

        @Override
        public boolean canInsertFluid(@NotNull Identifier blockIdentifier) {
            return true;
        }

        @Override
        public boolean canExtractFluid(@NotNull Identifier blockIdentifier) {
            return true;
        }

        @Override
        public @Nullable String tankType(@NotNull Identifier blockIdentifier) {
            return "test";
        }

        @Override
        public @NotNull TransferBridgeFactory.FluidStackView tankAt(@NotNull Identifier blockIdentifier, int tank) {
            return new TransferBridgeFactory.FluidStackView(this.fluidId, this.amount);
        }

        @Override
        public int insertFluid(@NotNull Identifier blockIdentifier, @NotNull TransferBridgeFactory.FluidStackView fluid, int tank, @Nullable String side, boolean simulate) {
            if (!this.fluidId.equals(fluid.fluidId())) {
                return 0;
            }
            int moved = Math.min(fluid.amount(), this.capacity - this.amount);
            if (!simulate) {
                this.amount += moved;
            }
            return moved;
        }

        @Override
        public int extractFluid(@NotNull Identifier blockIdentifier, @NotNull TransferBridgeFactory.FluidStackView fluid, int tank, @Nullable String side, boolean simulate) {
            if (!this.fluidId.equals(fluid.fluidId())) {
                return 0;
            }
            int moved = Math.min(fluid.amount(), this.amount);
            if (!simulate) {
                this.amount -= moved;
            }
            return moved;
        }
    }
}