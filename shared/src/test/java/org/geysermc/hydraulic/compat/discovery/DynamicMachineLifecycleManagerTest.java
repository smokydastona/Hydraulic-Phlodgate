package org.geysermc.hydraulic.compat.discovery;

import net.minecraft.resources.Identifier;
import org.geysermc.hydraulic.compat.ir.CompiledCompatibilityPlan;
import org.geysermc.hydraulic.compat.runtime.RuntimeBridgeKind;
import org.geysermc.hydraulic.compat.runtime.RuntimeDispatchTable;
import org.geysermc.hydraulic.compat.runtime.TransferBridgeFactory;
import org.geysermc.hydraulic.compat.model.SupportLevel;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class DynamicMachineLifecycleManagerTest {

    // Mock-like real POJO representing an unmapped third-party machine
    public static class SampleUnmappedMachine {
        private int storedEnergy = 5000;

        public int insertItem(int slot, int count) {
            return count;
        }

        public int extractItem(int slot, int count) {
            return count;
        }

        public int getContainerSize() {
            return 4;
        }

        public int getStoredEnergy() {
            return storedEnergy;
        }

        public void tick() {
            // Ticking machine
        }
    }

    @Test
    public void testDynamicMachineCompilationAndRegistration() {
        RuntimeDispatchTable dispatchTable = RuntimeDispatchTable.empty();
        DynamicMachineLifecycleManager manager = new DynamicMachineLifecycleManager(dispatchTable);

        Identifier machineId = Identifier.parse("custom_mod:crusher");
        SampleUnmappedMachine machine = new SampleUnmappedMachine();

        CompiledCompatibilityPlan plan = manager.registerAndCompile(machineId, machine, Map.of("category", "machine"));

        assertNotNull(plan);
        assertEquals(machineId.toString(), plan.javaIdentifier());
        assertEquals(SupportLevel.VISUAL_ONLY, plan.overallLevel());
        assertFalse(plan.requiresRuntimeBridge(RuntimeBridgeKind.ITEM_TRANSFER));
        assertFalse(plan.requiresRuntimeBridge(RuntimeBridgeKind.MACHINE_INVENTORY));
        assertFalse(plan.requiresRuntimeBridge(RuntimeBridgeKind.MACHINE_BEHAVIOR));

        // Verify retrieval from RuntimeDispatchTable directly
        CompiledCompatibilityPlan lookedUp = dispatchTable.block(machineId);
        assertNotNull(lookedUp);
        assertEquals(plan.javaIdentifier(), lookedUp.javaIdentifier());
        assertEquals(1, manager.dynamicPlanCount());
    }

    @Test
    void retainsTransferBridgesWhenTheRuntimeContractIsExecutable() {
        RuntimeDispatchTable dispatchTable = RuntimeDispatchTable.empty();
        DynamicMachineLifecycleManager manager = new DynamicMachineLifecycleManager(dispatchTable);
        Identifier machineId = Identifier.parse("custom_mod:storage");

        CompiledCompatibilityPlan plan = manager.registerAndCompile(
            machineId,
            new ExecutableStorage(),
            Map.of("category", "machine")
        );

        assertEquals(SupportLevel.ADAPTED, plan.overallLevel());
        assertTrue(plan.requiresRuntimeBridge(RuntimeBridgeKind.ITEM_TRANSFER));
        assertTrue(plan.requiresRuntimeBridge(RuntimeBridgeKind.MACHINE_INVENTORY));
        assertFalse(plan.requiresRuntimeBridge(RuntimeBridgeKind.MACHINE_BEHAVIOR));
    }

    @Test
    void compilesDiscoveredFluidTransferUsingSemanticDiscoveryFacts() {
        RuntimeDispatchTable dispatchTable = RuntimeDispatchTable.empty();
        DynamicMachineLifecycleManager manager = new DynamicMachineLifecycleManager(dispatchTable);
        Identifier machineId = Identifier.parse("custom_mod:fluid_storage");

        CompiledCompatibilityPlan plan = manager.registerAndCompile(
            machineId,
            new ExecutableFluidStorage(),
            Map.of("category", "machine")
        );

        assertEquals(SupportLevel.ADAPTED, plan.overallLevel());
        assertTrue(plan.requiresRuntimeBridge(RuntimeBridgeKind.FLUID_TRANSFER));
        assertEquals(plan, dispatchTable.block(machineId));
    }

    public static final class ExecutableStorage {
        public int getContainerSize() {
            return 1;
        }

        public TransferBridgeFactory.ItemStackView getItem(int slot) {
            return new TransferBridgeFactory.ItemStackView("minecraft:air", 0);
        }

        public int insertItem(TransferBridgeFactory.ItemStackView item, int slot, String side, boolean simulate) {
            return item.count();
        }

        public int extractItem(TransferBridgeFactory.ItemStackView item, int slot, String side, boolean simulate) {
            return item.count();
        }
    }

    public static final class ExecutableFluidStorage {
        public int getTanks() {
            return 1;
        }

        public TransferBridgeFactory.FluidStackView getFluidInTank(int tank) {
            return new TransferBridgeFactory.FluidStackView("minecraft:water", 0);
        }

        public int getTankCapacity(int tank) {
            return 1000;
        }

        public int fill(int tank, TransferBridgeFactory.FluidStackView fluid, boolean simulate) {
            return fluid.amount();
        }

        public int drain(int tank, int amount, boolean simulate) {
            return amount;
        }
    }
}
