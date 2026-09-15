package org.geysermc.hydraulic.compat.fluid;

import org.geysermc.hydraulic.compat.runtime.TransferBridgeFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Universal Fluid Runtime Engine (Phase 4).
 * Provides multi-tank abstractions, bidirectional container ↔ tank transfers,
 * fluid identity enforcement, and Bedrock-compatible world fluid approximations.
 */
public final class UniversalFluidRuntime {

    public enum FluidSide {
        UP,
        DOWN,
        NORTH,
        SOUTH,
        EAST,
        WEST,
        INTERNAL;

        @NotNull
        public static FluidSide fromString(@Nullable String name) {
            if (name == null || name.isBlank()) return INTERNAL;
            try {
                return FluidSide.valueOf(name.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return INTERNAL;
            }
        }
    }

    public record UniversalFluidTank(
        int tankIndex,
        int capacity,
        @NotNull TransferBridgeFactory.FluidStackView fluid,
        boolean allowInsertion,
        boolean allowExtraction,
        @NotNull List<String> fluidWhitelist,
        @NotNull java.util.Set<FluidSide> accessibleSides
    ) {
        public UniversalFluidTank {
            capacity = Math.max(1, capacity);
            fluidWhitelist = List.copyOf(fluidWhitelist);
            accessibleSides = java.util.Set.copyOf(accessibleSides);
        }

        public UniversalFluidTank(
            int tankIndex,
            int capacity,
            @NotNull TransferBridgeFactory.FluidStackView fluid,
            boolean allowInsertion,
            boolean allowExtraction,
            @NotNull List<String> fluidWhitelist
        ) {
            this(tankIndex, capacity, fluid, allowInsertion, allowExtraction, fluidWhitelist, java.util.Set.of(FluidSide.values()));
        }

        public boolean canAccept(@NotNull String fluidId) {
            if (!allowInsertion) return false;
            if (!fluidWhitelist.isEmpty() && !fluidWhitelist.contains(fluidId)) return false;
            return isEmpty() || fluid.fluidId().equals(fluidId);
        }

        public boolean allowsSide(@NotNull FluidSide side) {
            return accessibleSides.contains(side) || accessibleSides.contains(FluidSide.INTERNAL);
        }

        public int remainingCapacity() {
            return Math.max(0, capacity - fluid.amount());
        }

        public boolean isFull() {
            return fluid.amount() >= capacity;
        }

        public boolean isEmpty() {
            return fluid.amount() <= 0 || "minecraft:empty".equals(fluid.fluidId());
        }
    }

    public record FluidTransferTransactionResult(
        int requestedAmount,
        int transferredAmount,
        @NotNull String fluidId,
        boolean success,
        @Nullable String failureReason
    ) {}

    public static final class FluidTankManager {
        private final List<UniversalFluidTank> tanks = new ArrayList<>();

        public void addTank(int capacity, boolean canInsert, boolean canExtract, List<String> whitelist) {
            addTank(capacity, canInsert, canExtract, whitelist, java.util.Set.of(FluidSide.values()));
        }

        public void addTank(int capacity, boolean canInsert, boolean canExtract, List<String> whitelist, java.util.Set<FluidSide> sides) {
            int index = tanks.size();
            tanks.add(new UniversalFluidTank(
                index,
                capacity,
                new TransferBridgeFactory.FluidStackView("minecraft:empty", 0),
                canInsert,
                canExtract,
                whitelist,
                sides
            ));
        }

        public int tankCount() {
            return tanks.size();
        }

        @Nullable
        public UniversalFluidTank getTank(int index) {
            if (index < 0 || index >= tanks.size()) return null;
            return tanks.get(index);
        }

        public FluidTransferTransactionResult fill(int tankIndex, @NotNull TransferBridgeFactory.FluidStackView stack, boolean simulate) {
            return fill(tankIndex, stack, FluidSide.INTERNAL, simulate);
        }

        public FluidTransferTransactionResult fill(int tankIndex, @NotNull TransferBridgeFactory.FluidStackView stack, @NotNull FluidSide side, boolean simulate) {
            UniversalFluidTank tank = getTank(tankIndex);
            if (tank == null) {
                return new FluidTransferTransactionResult(stack.amount(), 0, stack.fluidId(), false, "Invalid tank index");
            }
            if (!tank.allowsSide(side)) {
                return new FluidTransferTransactionResult(stack.amount(), 0, stack.fluidId(), false, "Fluid insertion forbidden on side: " + side);
            }
            if (!tank.canAccept(stack.fluidId())) {
                return new FluidTransferTransactionResult(stack.amount(), 0, stack.fluidId(), false, "Fluid rejected by tank whitelist or mismatch");
            }

            int space = tank.remainingCapacity();
            int toFill = Math.min(space, stack.amount());
            if (toFill <= 0) {
                return new FluidTransferTransactionResult(stack.amount(), 0, stack.fluidId(), false, "Tank is full");
            }

            if (!simulate) {
                int newAmount = tank.fluid().amount() + toFill;
                tanks.set(tankIndex, new UniversalFluidTank(
                    tank.tankIndex(),
                    tank.capacity(),
                    new TransferBridgeFactory.FluidStackView(stack.fluidId(), newAmount),
                    tank.allowInsertion(),
                    tank.allowExtraction(),
                    tank.fluidWhitelist(),
                    tank.accessibleSides()
                ));
            }

            return new FluidTransferTransactionResult(stack.amount(), toFill, stack.fluidId(), true, null);
        }

        public FluidTransferTransactionResult drain(int tankIndex, int maxDrain, boolean simulate) {
            return drain(tankIndex, maxDrain, FluidSide.INTERNAL, simulate);
        }

        public FluidTransferTransactionResult drain(int tankIndex, int maxDrain, @NotNull FluidSide side, boolean simulate) {
            UniversalFluidTank tank = getTank(tankIndex);
            if (tank == null) {
                return new FluidTransferTransactionResult(maxDrain, 0, "minecraft:empty", false, "Invalid tank index");
            }
            if (!tank.allowsSide(side)) {
                return new FluidTransferTransactionResult(maxDrain, 0, tank.fluid().fluidId(), false, "Fluid extraction forbidden on side: " + side);
            }
            if (!tank.allowExtraction() || tank.isEmpty()) {
                return new FluidTransferTransactionResult(maxDrain, 0, tank.fluid().fluidId(), false, "Tank is empty or extraction forbidden");
            }

            int toDrain = Math.min(maxDrain, tank.fluid().amount());
            String fluidId = tank.fluid().fluidId();

            if (!simulate) {
                int newAmount = tank.fluid().amount() - toDrain;
                String remainingId = newAmount <= 0 ? "minecraft:empty" : fluidId;
                tanks.set(tankIndex, new UniversalFluidTank(
                    tank.tankIndex(),
                    tank.capacity(),
                    new TransferBridgeFactory.FluidStackView(remainingId, Math.max(0, newAmount)),
                    tank.allowInsertion(),
                    tank.allowExtraction(),
                    tank.fluidWhitelist(),
                    tank.accessibleSides()
                ));
            }

            return new FluidTransferTransactionResult(maxDrain, toDrain, fluidId, true, null);
        }
    }

    public static final class WorldFluidApproximator {
        public record BedrockFluidVisual(
            @NotNull String bedrockBlockIdentifier,
            @NotNull String sourceTexture,
            @NotNull String flowingTexture,
            int tintColor,
            int luminosity
        ) {}

        private static final Map<String, BedrockFluidVisual> REGISTRY = new LinkedHashMap<>();

        public static void registerVisual(@NotNull String javaFluidId, @NotNull BedrockFluidVisual visual) {
            REGISTRY.put(javaFluidId, visual);
        }

        @NotNull
        public static BedrockFluidVisual approximate(@NotNull String javaFluidId) {
            BedrockFluidVisual visual = REGISTRY.get(javaFluidId);
            if (visual != null) {
                return visual;
            }
            // Generic fallback approximation
            return new BedrockFluidVisual(
                "minecraft:water",
                "textures/blocks/water_still",
                "textures/blocks/water_flow",
                0x3F76E4,
                0
            );
        }
    }
}
