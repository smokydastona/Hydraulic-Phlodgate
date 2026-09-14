package org.geysermc.hydraulic.compat.machine;

import net.minecraft.resources.Identifier;
import org.geysermc.hydraulic.compat.runtime.DirtyStateTracker;
import org.geysermc.hydraulic.compat.runtime.StateChangeSet;
import org.geysermc.hydraulic.compat.runtime.TransferBridgeFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Universal Machine Runtime Engine (Phase 3).
 * Normalizes machine state machines (Idle, Running, Blocked, Powered, Complete),
 * dynamic multi-datapack recipe matching, energy/fluid consumption cycles,
 * and synchronized dirty-state broadcasting.
 */
public final class UniversalMachineRuntime {

    public enum MachineState {
        IDLE,
        RUNNING,
        BLOCKED_OUTPUT,
        INSUFFICIENT_ENERGY,
        INSUFFICIENT_FLUID,
        POWERED_OFF,
        COMPLETE
    }

    public record UniversalRecipe(
        @NotNull String recipeId,
        @NotNull List<TransferBridgeFactory.ItemStackView> itemInputs,
        @NotNull List<TransferBridgeFactory.FluidStackView> fluidInputs,
        int energyRequiredPerTick,
        int totalProcessingTicks,
        @NotNull List<TransferBridgeFactory.ItemStackView> itemOutputs,
        @NotNull List<TransferBridgeFactory.FluidStackView> fluidOutputs,
        int energyGenerated,
        @NotNull List<TransferBridgeFactory.ItemStackView> catalysts,
        @NotNull List<String> conditions
    ) {
        public UniversalRecipe {
            itemInputs = List.copyOf(itemInputs);
            fluidInputs = List.copyOf(fluidInputs);
            itemOutputs = List.copyOf(itemOutputs);
            fluidOutputs = List.copyOf(fluidOutputs);
            catalysts = List.copyOf(catalysts);
            conditions = List.copyOf(conditions);
        }

        public UniversalRecipe(
            @NotNull String recipeId,
            @NotNull List<TransferBridgeFactory.ItemStackView> itemInputs,
            @NotNull List<TransferBridgeFactory.FluidStackView> fluidInputs,
            int energyRequiredPerTick,
            int totalProcessingTicks,
            @NotNull List<TransferBridgeFactory.ItemStackView> itemOutputs,
            @NotNull List<TransferBridgeFactory.FluidStackView> fluidOutputs,
            int energyGenerated
        ) {
            this(recipeId, itemInputs, fluidInputs, energyRequiredPerTick, totalProcessingTicks,
                itemOutputs, fluidOutputs, energyGenerated, List.of(), List.of());
        }

        public boolean matches(
            @NotNull List<TransferBridgeFactory.ItemStackView> availableItems,
            @NotNull List<TransferBridgeFactory.FluidStackView> availableFluids,
            int availableEnergy
        ) {
            for (TransferBridgeFactory.ItemStackView input : itemInputs) {
                boolean found = false;
                for (TransferBridgeFactory.ItemStackView avail : availableItems) {
                    if (avail.matches(input) && avail.count() >= input.count()) {
                        found = true;
                        break;
                    }
                }
                if (!found) return false;
            }

            for (TransferBridgeFactory.FluidStackView fluidInput : fluidInputs) {
                boolean found = false;
                for (TransferBridgeFactory.FluidStackView avail : availableFluids) {
                    if (avail.fluidId().equals(fluidInput.fluidId()) && avail.amount() >= fluidInput.amount()) {
                        found = true;
                        break;
                    }
                }
                if (!found) return false;
            }

            for (TransferBridgeFactory.ItemStackView catalyst : catalysts) {
                boolean found = availableItems.stream().anyMatch(available ->
                    available.matches(catalyst) && available.count() >= catalyst.count());
                if (!found) return false;
            }

            return availableEnergy >= energyRequiredPerTick;
        }
    }

    public static final class MachineExecutionContext {
        private final Identifier machineId;
        private final List<UniversalRecipe> recipes = new ArrayList<>();
        private MachineState state = MachineState.IDLE;
        private int currentProgressTicks = 0;
        private UniversalRecipe activeRecipe = null;
        private final DirtyStateTracker dirtyStateTracker = new DirtyStateTracker();

        public MachineExecutionContext(@NotNull Identifier machineId) {
            this.machineId = Objects.requireNonNull(machineId, "machineId");
        }

        public void registerRecipe(@NotNull UniversalRecipe recipe) {
            this.recipes.add(recipe);
        }

        @NotNull
        public Identifier machineId() {
            return machineId;
        }

        @NotNull
        public MachineState state() {
            return state;
        }

        public int currentProgressTicks() {
            return currentProgressTicks;
        }

        @Nullable
        public UniversalRecipe activeRecipe() {
            return activeRecipe;
        }

        @NotNull
        public DirtyStateTracker dirtyStateTracker() {
            return dirtyStateTracker;
        }

        public void tick(
            @NotNull TransferBridgeFactory.ItemTransferBridge itemBridge,
            @Nullable TransferBridgeFactory.FluidTransferBridge fluidBridge,
            @Nullable TransferBridgeFactory.EnergyTransferBridge energyBridge
        ) {
            if (activeRecipe == null) {
                activeRecipe = matchRecipe(itemBridge, fluidBridge, energyBridge);
                if (activeRecipe == null) {
                    setState(MachineState.IDLE);
                    currentProgressTicks = 0;
                    return;
                }
            }

            // Check output capacity
            if (!hasOutputCapacity(activeRecipe, itemBridge, fluidBridge)) {
                setState(MachineState.BLOCKED_OUTPUT);
                return;
            }

            // Consume energy per tick
            if (activeRecipe.energyRequiredPerTick() > 0 && energyBridge != null) {
                int extracted = energyBridge.extractEnergy(machineId, activeRecipe.energyRequiredPerTick(), null, false);
                if (extracted < activeRecipe.energyRequiredPerTick()) {
                    setState(MachineState.INSUFFICIENT_ENERGY);
                    return;
                }
            }

            setState(MachineState.RUNNING);
            currentProgressTicks++;
            dirtyStateTracker.record(new StateChangeSet(List.of(
                new StateChangeSet.FieldChange(machineId, "machine.progress", null, currentProgressTicks)
            )));

            if (currentProgressTicks >= activeRecipe.totalProcessingTicks()) {
                completeProcessing(activeRecipe, itemBridge, fluidBridge, energyBridge);
                currentProgressTicks = 0;
                activeRecipe = null;
                setState(MachineState.COMPLETE);
            }
        }

        private void setState(MachineState newState) {
            if (this.state != newState) {
                this.state = newState;
                dirtyStateTracker.record(new StateChangeSet(List.of(
                    new StateChangeSet.FieldChange(machineId, "machine.state", null, newState.name())
                )));
            }
        }

        @Nullable
        private UniversalRecipe matchRecipe(
            TransferBridgeFactory.ItemTransferBridge itemBridge,
            TransferBridgeFactory.FluidTransferBridge fluidBridge,
            TransferBridgeFactory.EnergyTransferBridge energyBridge
        ) {
            List<TransferBridgeFactory.ItemStackView> items = new ArrayList<>();
            int slotCount = itemBridge.slotCount(machineId);
            for (int i = 0; i < slotCount; i++) {
                TransferBridgeFactory.ItemStackView stack = itemBridge.itemAt(machineId, i);
                if (stack != null && !stack.isEmpty()) {
                    items.add(stack);
                }
            }

            List<TransferBridgeFactory.FluidStackView> fluids = new ArrayList<>();
            if (fluidBridge != null) {
                int tankCount = fluidBridge.tankCount(machineId);
                for (int i = 0; i < tankCount; i++) {
                    TransferBridgeFactory.FluidStackView f = fluidBridge.tankAt(machineId, i);
                    if (f != null && f.amount() > 0) {
                        fluids.add(f);
                    }
                }
            }

            int energyStored = energyBridge != null ? energyBridge.getEnergyStored(machineId) : 0;

            for (UniversalRecipe recipe : recipes) {
                if (recipe.matches(items, fluids, energyStored)) {
                    return recipe;
                }
            }
            return null;
        }

        private boolean hasOutputCapacity(
            UniversalRecipe recipe,
            TransferBridgeFactory.ItemTransferBridge itemBridge,
            TransferBridgeFactory.FluidTransferBridge fluidBridge
        ) {
            for (TransferBridgeFactory.ItemStackView output : recipe.itemOutputs()) {
                int inserted = itemBridge.insert(machineId, output, 1, null, true);
                if (inserted < output.count()) {
                    return false;
                }
            }
            return true;
        }

        private void completeProcessing(
            UniversalRecipe recipe,
            TransferBridgeFactory.ItemTransferBridge itemBridge,
            TransferBridgeFactory.FluidTransferBridge fluidBridge,
            TransferBridgeFactory.EnergyTransferBridge energyBridge
        ) {
            // Consume inputs
            for (TransferBridgeFactory.ItemStackView input : recipe.itemInputs()) {
                itemBridge.extract(machineId, input, 0, null, false);
            }
            // Emit outputs
            for (TransferBridgeFactory.ItemStackView output : recipe.itemOutputs()) {
                itemBridge.insert(machineId, output, 1, null, false);
            }
        }
    }
}
