package org.geysermc.hydraulic.compat.fluid;

import net.minecraft.resources.Identifier;
import org.geysermc.hydraulic.compat.runtime.TransferBridgeFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Universal Fluid Runtime Engine (Phase 4).
 * Provides multi-tank abstractions, bidirectional container ↔ tank transfers,
 * fluid identity enforcement, and Bedrock-compatible world fluid approximations.
 */
public final class UniversalFluidRuntime {

    public record UniversalFluidTank(
        int tankIndex,
        int capacity,
        @NotNull TransferBridgeFactory.FluidStackView fluid,
        boolean allowInsertion,
        boolean allowExtraction,
        @NotNull List<String> fluidWhitelist
    ) {
        public UniversalFluidTank {
            capacity = Math.max(1, capacity);
            fluidWhitelist = List.copyOf(fluidWhitelist);
        }

        public boolean canAccept(@NotNull String fluidId) {
            if (!allowInsertion) return false;
            if (!fluidWhitelist.isEmpty() && !fluidWhitelist.contains(fluidId)) return false;
            return isEmpty() || fluid.fluidId().equals(fluidId);
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
            int index = tanks.size();
            tanks.add(new UniversalFluidTank(
                index,
                capacity,
                new TransferBridgeFactory.FluidStackView("minecraft:empty", 0),
                canInsert,
                canExtract,
                whitelist
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
            UniversalFluidTank tank = getTank(tankIndex);
            if (tank == null) {
                return new FluidTransferTransactionResult(stack.amount(), 0, stack.fluidId(), false, "Invalid tank index");
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
                    tank.fluidWhitelist()
                ));
            }

            return new FluidTransferTransactionResult(stack.amount(), toFill, stack.fluidId(), true, null);
        }

        public FluidTransferTransactionResult drain(int tankIndex, int maxDrain, boolean simulate) {
            UniversalFluidTank tank = getTank(tankIndex);
            if (tank == null) {
                return new FluidTransferTransactionResult(maxDrain, 0, "minecraft:empty", false, "Invalid tank index");
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
                    tank.fluidWhitelist()
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
