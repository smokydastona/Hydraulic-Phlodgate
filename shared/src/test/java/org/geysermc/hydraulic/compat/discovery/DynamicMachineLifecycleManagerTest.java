package org.geysermc.hydraulic.compat.discovery;

import net.minecraft.resources.Identifier;
import org.geysermc.hydraulic.compat.ir.CompiledCompatibilityPlan;
import org.geysermc.hydraulic.compat.runtime.RuntimeBridgeKind;
import org.geysermc.hydraulic.compat.runtime.RuntimeDispatchTable;
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
        assertTrue(plan.requiresRuntimeBridge(RuntimeBridgeKind.ITEM_TRANSFER));
        assertTrue(plan.requiresRuntimeBridge(RuntimeBridgeKind.MACHINE_INVENTORY));
        assertTrue(plan.requiresRuntimeBridge(RuntimeBridgeKind.MACHINE_BEHAVIOR));

        // Verify retrieval from RuntimeDispatchTable directly
        CompiledCompatibilityPlan lookedUp = dispatchTable.block(machineId);
        assertNotNull(lookedUp);
        assertEquals(plan.javaIdentifier(), lookedUp.javaIdentifier());
        assertEquals(1, manager.dynamicPlanCount());
    }
}
