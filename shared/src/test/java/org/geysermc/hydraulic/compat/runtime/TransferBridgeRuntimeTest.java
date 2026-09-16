package org.geysermc.hydraulic.compat.runtime;

import net.minecraft.resources.Identifier;
import org.geysermc.hydraulic.compat.CompatibilityStatus;
import org.geysermc.hydraulic.compat.adapter.AdapterBinding;
import org.geysermc.hydraulic.compat.adapter.AdapterFeature;
import org.geysermc.hydraulic.compat.ir.CompiledCompatibilityPlan;
import org.geysermc.hydraulic.compat.model.Confidence;
import org.geysermc.hydraulic.compat.model.SupportLevel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferBridgeRuntimeTest {
    @Test
    void unsupportedRuntimeTargetsDoNotBecomeSilentNoOpBridges() {
        CompiledCompatibilityPlan itemPlan = runtimePlan(RuntimeBridgeKind.ITEM_TRANSFER, Map.of("can_insert", "true"));
        CompiledCompatibilityPlan fluidPlan = runtimePlan(RuntimeBridgeKind.FLUID_TRANSFER, Map.of("can_insert_fluid", "true"));
        CompiledCompatibilityPlan energyPlan = runtimePlan(RuntimeBridgeKind.ENERGY_TRANSFER, Map.of("can_receive_energy", "true"));
        Object unsupported = new Object();

        assertNull(TransferBridgeFactory.createItemTransfer(itemPlan, unsupported));
        assertNull(TransferBridgeFactory.createFluidTransfer(fluidPlan, unsupported));
        assertNull(TransferBridgeFactory.createEnergyTransfer(energyPlan, unsupported));

        assertTrue(!new TransferBridgeFactory.ItemTransferBridge() {
            @Override
            public boolean canInsert(Identifier blockIdentifier) {
                return true;
            }

            @Override
            public boolean canExtract(Identifier blockIdentifier) {
                return true;
            }

            @Override
            public String inventoryType(Identifier blockIdentifier) {
                return "metadata";
            }
        }.executable());
    }

    @Test
    void incompatibleReflectiveStackTypesFailClosed() {
        CompiledCompatibilityPlan plan = runtimePlan(
            RuntimeBridgeKind.ITEM_TRANSFER,
            Map.of("can_insert", "true", "can_extract", "true")
        );

        assertNull(TransferBridgeFactory.createItemTransfer(plan, new ForeignStackInventory()));
    }

    @Test
    void declaredDirectionsMustHaveConcreteRuntimeOperations() {
        CompiledCompatibilityPlan itemPlan = runtimePlan(RuntimeBridgeKind.ITEM_TRANSFER, Map.of("can_insert", "true", "can_extract", "true"));
        CompiledCompatibilityPlan fluidPlan = runtimePlan(RuntimeBridgeKind.FLUID_TRANSFER, Map.of("can_insert_fluid", "true", "can_extract_fluid", "true"));
        CompiledCompatibilityPlan energyPlan = runtimePlan(RuntimeBridgeKind.ENERGY_TRANSFER, Map.of("can_receive_energy", "true", "can_provide_energy", "true"));

        assertNull(TransferBridgeFactory.createItemTransfer(itemPlan, new InsertOnlyInventory()));
        assertNull(TransferBridgeFactory.createFluidTransfer(fluidPlan, new FillOnlyTank()));
        assertNull(TransferBridgeFactory.createEnergyTransfer(energyPlan, new ReceiveOnlyStorage()));
    }

    @Test
    void emptyDispatchTableDoesNotCreateRuntimeBridges() {
        RuntimeDispatchTable dispatchTable = RuntimeDispatchTable.empty();
        Identifier block = Identifier.fromNamespaceAndPath("hydraulic", "missing_machine");

        assertNull(dispatchTable.itemTransfer(block, new Object()));
        assertNull(dispatchTable.itemTransaction(block, new Object()));
        assertNull(dispatchTable.fluidTransfer(block, new Object()));
        assertNull(dispatchTable.fluidTransaction(block, new Object()));
        assertNull(dispatchTable.energyTransfer(block, new Object()));
        assertNull(dispatchTable.energyTransaction(block, new Object()));
        assertNull(dispatchTable.machineBehavior(block));
        assertNull(dispatchTable.machineInventory(block));
        assertNull(dispatchTable.inventoryAccess(block, new Object()));
        assertNull(dispatchTable.automationAccess(block, new Object()));
        assertNull(dispatchTable.resourceAutomationAccess(block, new Object(), new Object(), new Object()));
        assertNull(dispatchTable.machineProcessing(block, null, 0, 1, List.of()));
    }

    @Test
    void itemBridgeUsesActualRuntimeInventorySemantics() {
        TestInventory inventory = new TestInventory();
        CompiledCompatibilityPlan plan = runtimePlan(RuntimeBridgeKind.ITEM_TRANSFER, Map.of("can_insert", "true", "can_extract", "true", "inventory_type", "generic"));
        TransferBridgeFactory.ItemTransferBridge bridge = TransferBridgeFactory.createItemTransfer(plan, inventory);

        assertNotNull(bridge);
        assertTrue(bridge.executable());
        assertTrue(bridge.canInsert(Identifier.fromNamespaceAndPath("hydraulic", "test_machine")));
        assertTrue(bridge.canExtract(Identifier.fromNamespaceAndPath("hydraulic", "test_machine")));
        assertEquals(2, bridge.slotCount(Identifier.fromNamespaceAndPath("hydraulic", "test_machine")));

        TransferBridgeFactory.OperationResult simulated = bridge.insertResult(
            Identifier.fromNamespaceAndPath("hydraulic", "test_machine"),
            new TransferBridgeFactory.ItemStackView("minecraft:iron_ingot", 8),
            1,
            "input",
            true
        );
        assertEquals(TransferBridgeFactory.OperationStatus.COMPLETED, simulated.status());
        assertTrue(simulated.simulated());
        assertEquals(0, bridge.itemAt(Identifier.fromNamespaceAndPath("hydraulic", "test_machine"), 1).count());

        int inserted = bridge.insert(
            Identifier.fromNamespaceAndPath("hydraulic", "test_machine"),
            new TransferBridgeFactory.ItemStackView("minecraft:iron_ingot", 8),
            1,
            "input",
            false
        );
        assertEquals(8, inserted);
        assertEquals(8, bridge.itemAt(Identifier.fromNamespaceAndPath("hydraulic", "test_machine"), 1).count());

        int extracted = bridge.extract(
            Identifier.fromNamespaceAndPath("hydraulic", "test_machine"),
            new TransferBridgeFactory.ItemStackView("minecraft:iron_ingot", 8),
            1,
            "output",
            false
        );
        assertEquals(8, extracted);
        assertEquals(0, bridge.itemAt(Identifier.fromNamespaceAndPath("hydraulic", "test_machine"), 1).count());

        TransferBridgeFactory.OperationResult rejected = bridge.insertResult(
            Identifier.fromNamespaceAndPath("hydraulic", "test_machine"),
            new TransferBridgeFactory.ItemStackView("minecraft:diamond", 1),
            1,
            "input",
            false
        );
        assertEquals(TransferBridgeFactory.OperationStatus.REJECTED, rejected.status());
        assertEquals(2, bridge.operationMetrics().snapshot().attempts());
        assertEquals(1, bridge.operationMetrics().snapshot().simulated());
    }

    @Test
    void itemBridgePreservesSidedAutomationOperations() {
        Identifier machine = Identifier.fromNamespaceAndPath("hydraulic", "test_machine");
        CompiledCompatibilityPlan plan = runtimePlan(RuntimeBridgeKind.ITEM_TRANSFER, Map.of("can_insert", "true", "can_extract", "true", "inventory_type", "sided"));
        SidedInventory inventory = new SidedInventory();
        TransferBridgeFactory.ItemTransferBridge bridge = TransferBridgeFactory.createItemTransfer(plan, inventory);

        assertNotNull(bridge);
        assertEquals(1, bridge.insert(machine, new TransferBridgeFactory.ItemStackView("minecraft:stone", 1), 0, "north", false));
        assertEquals("north", inventory.lastSide);
        assertEquals(1, bridge.extract(machine, new TransferBridgeFactory.ItemStackView("minecraft:stone", 1), 0, "south", false));
        assertEquals("south", inventory.lastSide);
    }

    @Test
    void itemTransactionSimulatesAllOperationsBeforeMutation() {
        Identifier machine = Identifier.fromNamespaceAndPath("hydraulic", "test_machine");
        TestInventory inventory = new TestInventory();
        CompiledCompatibilityPlan plan = runtimePlan(RuntimeBridgeKind.ITEM_TRANSFER, Map.of("can_insert", "true", "can_extract", "true", "inventory_type", "generic"));
        TransferBridgeFactory.ItemTransferBridge transfer = TransferBridgeFactory.createItemTransfer(plan, inventory);
        ItemTransferTransaction transaction = new ItemTransferTransaction(transfer)
            .add(new TransferRequest(machine, TransferDirection.INSERT, new TransferBridgeFactory.ItemStackView("minecraft:iron_ingot", 1), 1, null))
            .add(new TransferRequest(machine, TransferDirection.INSERT, new TransferBridgeFactory.ItemStackView("minecraft:diamond", 1), 1, null));

        TransferResult result = transaction.execute();

        assertFalse(result.committed());
        assertEquals(0, result.moved());
        assertTrue(result.stateChanges().changes().isEmpty());
        assertTrue(transfer.itemAt(machine, 1).isEmpty());
    }

    @Test
    void itemTransactionCommitsMultipleValidatedOperations() {
        Identifier machine = Identifier.fromNamespaceAndPath("hydraulic", "test_machine");
        TestInventory inventory = new TestInventory();
        CompiledCompatibilityPlan plan = runtimePlan(RuntimeBridgeKind.ITEM_TRANSFER, Map.of("can_insert", "true", "can_extract", "true", "inventory_type", "generic"));
        TransferBridgeFactory.ItemTransferBridge transfer = TransferBridgeFactory.createItemTransfer(plan, inventory);
        ItemTransferTransaction transaction = new ItemTransferTransaction(transfer)
            .add(new TransferRequest(machine, TransferDirection.INSERT, new TransferBridgeFactory.ItemStackView("minecraft:iron_ingot", 1), 1, null))
            .add(new TransferRequest(machine, TransferDirection.EXTRACT, new TransferBridgeFactory.ItemStackView("minecraft:stone", 1), 0, null));

        DirtyStateTracker dirtyStateTracker = new DirtyStateTracker();
        TransferResult result = transaction.execute(dirtyStateTracker);

        assertTrue(result.committed());
        assertEquals(2, result.moved());
        assertTrue(dirtyStateTracker.dirty());
        assertEquals(2, dirtyStateTracker.drain().changes().size());
        assertFalse(dirtyStateTracker.dirty());
        assertEquals("minecraft:iron_ingot", transfer.itemAt(machine, 1).itemId());
        assertTrue(transfer.itemAt(machine, 0).isEmpty());
    }

    @Test
    void automationBridgeDelegatesSidedOperationsAndCompiledFiltering() {
        Identifier machine = Identifier.fromNamespaceAndPath("hydraulic", "test_machine");
        CompiledCompatibilityPlan plan = runtimePlan(
            List.of(RuntimeBridgeKind.ITEM_TRANSFER, RuntimeBridgeKind.AUTOMATION_ACCESS),
            Map.of(
                "can_insert", "true",
                "can_extract", "true",
                "sided_insert", "true",
                "sided_extract", "true",
                "filtering", "tag"
            )
        );
        TransferBridgeFactory.ItemTransferBridge transfer = TransferBridgeFactory.createItemTransfer(plan, new SidedInventory());
        MachineBridgeFactory.AutomationAccess automation = MachineBridgeFactory.createAutomation(plan, transfer);

        assertNotNull(automation);
        assertTrue(automation.supportsSidedInsertion(machine));
        assertTrue(automation.supportsSidedExtraction(machine));
        assertEquals("tag", automation.filterType(machine));
        assertEquals(1, automation.insert(machine, new TransferBridgeFactory.ItemStackView("minecraft:stone", 1), 0, "north", false));
        assertEquals(1, automation.extract(machine, new TransferBridgeFactory.ItemStackView("minecraft:stone", 1), 0, "south", false));
    }

    @Test
    void machineProcessingConsumesInputAndProducesOutputAfterDuration() {
        Identifier machine = Identifier.fromNamespaceAndPath("hydraulic", "test_machine");
        TestInventory inventory = new TestInventory();
        CompiledCompatibilityPlan plan = runtimePlan(
            List.of(RuntimeBridgeKind.ITEM_TRANSFER, RuntimeBridgeKind.MACHINE_BEHAVIOR, RuntimeBridgeKind.MACHINE_INVENTORY),
            Map.of("can_insert", "true", "can_extract", "true", "inventory_type", "generic", "has_processing", "true", "has_inventory", "true")
        );
        TransferBridgeFactory.ItemTransferBridge transfer = TransferBridgeFactory.createItemTransfer(plan, inventory);
        MachineProcessingBridge processing = MachineBridgeFactory.createProcessing(
            plan,
            transfer,
            0,
            1,
            List.of(new MachineProcessingBridge.MachineRecipe(
                new TransferBridgeFactory.ItemStackView("minecraft:stone", 1),
                new TransferBridgeFactory.ItemStackView("minecraft:iron_ingot", 1),
                2
            ))
        );

        assertNotNull(processing);
        assertFalse(processing.active());
        assertEquals(2, processing.duration(machine));
        assertTrue(processing.tick(machine));
        assertTrue(processing.active());
        assertEquals(1, processing.progress());
        assertTrue(processing.tick(machine));
        assertEquals(2, processing.progress());
        assertTrue(processing.tick(machine));
        assertFalse(processing.active());
        assertEquals(0, processing.progress());
        assertTrue(transfer.itemAt(machine, 0).isEmpty());
        assertEquals("minecraft:iron_ingot", transfer.itemAt(machine, 1).itemId());
    }

    @Test
    void machineProcessingRecordsCommittedInventoryChanges() {
        Identifier machine = Identifier.fromNamespaceAndPath("hydraulic", "test_machine");
        TestInventory inventory = new TestInventory();
        CompiledCompatibilityPlan plan = runtimePlan(
            List.of(RuntimeBridgeKind.ITEM_TRANSFER, RuntimeBridgeKind.MACHINE_BEHAVIOR, RuntimeBridgeKind.MACHINE_INVENTORY),
            Map.of("can_insert", "true", "can_extract", "true", "has_processing", "true", "has_inventory", "true")
        );
        TransferBridgeFactory.ItemTransferBridge transfer = TransferBridgeFactory.createItemTransfer(plan, inventory);
        MachineProcessingBridge processing = MachineBridgeFactory.createProcessing(
            plan,
            transfer,
            0,
            1,
            List.of(new MachineProcessingBridge.MachineRecipe(
                new TransferBridgeFactory.ItemStackView("minecraft:stone", 1),
                new TransferBridgeFactory.ItemStackView("minecraft:iron_ingot", 1),
                1
            ))
        );
        DirtyStateTracker dirty = new DirtyStateTracker();

        assertTrue(processing.tick(machine));
        assertTrue(processing.tick(machine, dirty));

        assertEquals(2, dirty.drain().changes().size());
    }

    @Test
    void inventoryAccessReadsRealRuntimeInventoryState() {
        Identifier machine = Identifier.fromNamespaceAndPath("hydraulic", "test_machine");
        CompiledCompatibilityPlan plan = runtimePlan(
            List.of(RuntimeBridgeKind.ITEM_TRANSFER, RuntimeBridgeKind.MACHINE_INVENTORY),
            Map.of(
                "can_insert", "true",
                "can_extract", "true",
                "has_inventory", "true",
                "inventory_layout", "machine",
                "slot_semantics", "input,output"
            )
        );
        TransferBridgeFactory.ItemTransferBridge transfer = TransferBridgeFactory.createItemTransfer(plan, new TestInventory());
        MachineBridgeFactory.InventoryAccess inventory = MachineBridgeFactory.createInventoryAccess(plan, transfer);

        assertNotNull(inventory);
        assertEquals(2, inventory.slotCount(machine));
        assertEquals("minecraft:stone", inventory.itemAt(machine, 0).itemId());
        assertEquals("machine", inventory.inventoryLayout(machine));
        assertEquals("input,output", inventory.slotSemantics(machine));
    }

    @Test
    void machineProcessingCompilesRecipesFromPlanFacts() {
        Identifier machine = Identifier.fromNamespaceAndPath("hydraulic", "test_machine");
        TestInventory inventory = new TestInventory();
        CompiledCompatibilityPlan plan = runtimePlan(
            List.of(RuntimeBridgeKind.ITEM_TRANSFER, RuntimeBridgeKind.MACHINE_BEHAVIOR, RuntimeBridgeKind.MACHINE_INVENTORY),
            Map.ofEntries(
                Map.entry("can_insert", "true"),
                Map.entry("can_extract", "true"),
                Map.entry("has_processing", "true"),
                Map.entry("has_inventory", "true"),
                Map.entry("machine.input_slot", "0"),
                Map.entry("machine.output_slot", "1"),
                Map.entry("machine.processing.recipe.0.input", "minecraft:stone"),
                Map.entry("machine.processing.recipe.0.input_count", "1"),
                Map.entry("machine.processing.recipe.0.output", "minecraft:iron_ingot"),
                Map.entry("machine.processing.recipe.0.output_count", "1"),
                Map.entry("machine.processing.recipe.0.duration", "1")
            )
        );
        TransferBridgeFactory.ItemTransferBridge transfer = TransferBridgeFactory.createItemTransfer(plan, inventory);

        MachineProcessingBridge processing = MachineBridgeFactory.createProcessing(plan, transfer);

        assertNotNull(processing);
        assertEquals(1, processing.duration(machine));
        assertTrue(processing.tick(machine));
        assertTrue(processing.tick(machine));
        assertEquals("minecraft:iron_ingot", transfer.itemAt(machine, 1).itemId());
        assertTrue(transfer.operationMetrics().snapshot().attempts() >= 4);
    }

    @Test
    void malformedMachineRecipeFactsFailClosed() {
        CompiledCompatibilityPlan plan = runtimePlan(
            List.of(RuntimeBridgeKind.ITEM_TRANSFER, RuntimeBridgeKind.MACHINE_BEHAVIOR, RuntimeBridgeKind.MACHINE_INVENTORY),
            Map.of(
                "can_insert", "true",
                "can_extract", "true",
                "has_processing", "true",
                "has_inventory", "true",
                "machine.input_slot", "0",
                "machine.output_slot", "1",
                "machine.processing.recipe.0.input", "minecraft:stone",
                "machine.processing.recipe.0.output", "minecraft:iron_ingot",
                "machine.processing.recipe.0.duration", "invalid"
            )
        );

        assertNull(MachineBridgeFactory.createProcessing(plan, TransferBridgeFactory.createItemTransfer(plan, new TestInventory())));
    }

    @Test
    void machineProcessingRequiresInventoryAndProcessingCapabilities() {
        CompiledCompatibilityPlan plan = runtimePlan(
            List.of(RuntimeBridgeKind.ITEM_TRANSFER),
            Map.of("can_insert", "true", "can_extract", "true")
        );
        TransferBridgeFactory.ItemTransferBridge transfer = TransferBridgeFactory.createItemTransfer(plan, new TestInventory());

        assertNull(MachineBridgeFactory.createProcessing(
            plan,
            transfer,
            0,
            1,
            List.of(new MachineProcessingBridge.MachineRecipe(
                new TransferBridgeFactory.ItemStackView("minecraft:stone", 1),
                new TransferBridgeFactory.ItemStackView("minecraft:iron_ingot", 1),
                1
            ))
        ));

        assertNull(MachineBridgeFactory.createProcessing(
            runtimePlan(
                List.of(RuntimeBridgeKind.ITEM_TRANSFER, RuntimeBridgeKind.MACHINE_BEHAVIOR, RuntimeBridgeKind.MACHINE_INVENTORY),
                Map.of("can_insert", "true", "can_extract", "true", "has_processing", "true", "has_inventory", "true")
            ),
            new TransferBridgeFactory.ItemTransferBridge() {
                @Override
                public boolean canInsert(Identifier blockIdentifier) {
                    return true;
                }

                @Override
                public boolean canExtract(Identifier blockIdentifier) {
                    return true;
                }

                @Override
                public String inventoryType(Identifier blockIdentifier) {
                    return "metadata";
                }
            },
            0,
            1,
            List.of(new MachineProcessingBridge.MachineRecipe(
                new TransferBridgeFactory.ItemStackView("minecraft:stone", 1),
                new TransferBridgeFactory.ItemStackView("minecraft:iron_ingot", 1),
                1
            ))
        ));
    }

    @Test
    void machineProcessingResetsWhenInputRecipeChangesMidCycle() {
        Identifier machine = Identifier.fromNamespaceAndPath("hydraulic", "test_machine");
        TestInventory inventory = new TestInventory();
        CompiledCompatibilityPlan plan = runtimePlan(
            List.of(RuntimeBridgeKind.ITEM_TRANSFER, RuntimeBridgeKind.MACHINE_BEHAVIOR, RuntimeBridgeKind.MACHINE_INVENTORY),
            Map.of("can_insert", "true", "can_extract", "true", "inventory_type", "generic", "has_processing", "true", "has_inventory", "true")
        );
        TransferBridgeFactory.ItemTransferBridge transfer = TransferBridgeFactory.createItemTransfer(plan, inventory);
        MachineProcessingBridge processing = MachineBridgeFactory.createProcessing(
            plan,
            transfer,
            0,
            1,
            List.of(
                new MachineProcessingBridge.MachineRecipe(
                    new TransferBridgeFactory.ItemStackView("minecraft:stone", 1),
                    new TransferBridgeFactory.ItemStackView("minecraft:iron_ingot", 1),
                    2
                ),
                new MachineProcessingBridge.MachineRecipe(
                    new TransferBridgeFactory.ItemStackView("minecraft:iron_ingot", 1),
                    new TransferBridgeFactory.ItemStackView("minecraft:gold_ingot", 1),
                    2
                )
            )
        );

        assertNotNull(processing);
        assertFalse(processing.active());
        assertTrue(processing.tick(machine));
        assertTrue(processing.active());
        assertEquals(1, processing.progress());
        processing.reset();
        assertFalse(processing.active());
        assertEquals(0, processing.progress());
        transfer.extract(machine, new TransferBridgeFactory.ItemStackView("minecraft:stone", 1), 0, null, false);
        transfer.insert(machine, new TransferBridgeFactory.ItemStackView("minecraft:iron_ingot", 1), 0, null, false);

        assertTrue(processing.tick(machine));
        assertEquals(1, processing.progress());
        assertTrue(processing.tick(machine));
        assertEquals(2, processing.progress());
        assertTrue(processing.tick(machine));
        assertEquals("minecraft:gold_ingot", transfer.itemAt(machine, 1).itemId());
    }

    @Test
    void fluidAndEnergyBridgesExposeRealRuntimeCapabilities() {
        TestTank tank = new TestTank();
        TestEnergyStorage storage = new TestEnergyStorage();
        CompiledCompatibilityPlan fluidPlan = runtimePlan(RuntimeBridgeKind.FLUID_TRANSFER, Map.of("can_insert_fluid", "true", "can_extract_fluid", "true", "tank_type", "generic"));
        CompiledCompatibilityPlan energyPlan = runtimePlan(RuntimeBridgeKind.ENERGY_TRANSFER, Map.of("can_receive_energy", "true", "can_provide_energy", "true", "energy_type", "generic"));

        TransferBridgeFactory.FluidTransferBridge fluidBridge = TransferBridgeFactory.createFluidTransfer(fluidPlan, tank);
        TransferBridgeFactory.EnergyTransferBridge energyBridge = TransferBridgeFactory.createEnergyTransfer(energyPlan, storage);

        assertNotNull(fluidBridge);
        assertTrue(fluidBridge.executable());
        assertTrue(fluidBridge.canInsertFluid(Identifier.fromNamespaceAndPath("hydraulic", "test_machine")));
        assertTrue(fluidBridge.canExtractFluid(Identifier.fromNamespaceAndPath("hydraulic", "test_machine")));
        assertEquals(1000, fluidBridge.tankCapacity(Identifier.fromNamespaceAndPath("hydraulic", "test_machine"), 0));
        assertEquals(250, fluidBridge.insertFluid(Identifier.fromNamespaceAndPath("hydraulic", "test_machine"), new TransferBridgeFactory.FluidStackView("minecraft:water", 250), 0, "input", false));
        assertEquals(
            TransferBridgeFactory.OperationStatus.PARTIAL,
            fluidBridge.insertFluidResult(Identifier.fromNamespaceAndPath("hydraulic", "test_machine"), new TransferBridgeFactory.FluidStackView("minecraft:water", 1000), 0, "input", true).status()
        );

        assertNotNull(energyBridge);
        assertTrue(energyBridge.executable());
        assertTrue(energyBridge.canReceiveEnergy(Identifier.fromNamespaceAndPath("hydraulic", "test_machine")));
        assertTrue(energyBridge.canProvideEnergy(Identifier.fromNamespaceAndPath("hydraulic", "test_machine")));
        assertEquals(1000, energyBridge.getMaxEnergy(Identifier.fromNamespaceAndPath("hydraulic", "test_machine")));
        assertEquals(250, energyBridge.receiveEnergy(Identifier.fromNamespaceAndPath("hydraulic", "test_machine"), 250, "input", false));
    }

    @Test
    void transferOperationFailuresBecomeStructuredResults() {
        Identifier machine = Identifier.fromNamespaceAndPath("hydraulic", "test_machine");
        CompiledCompatibilityPlan plan = runtimePlan(RuntimeBridgeKind.ITEM_TRANSFER, Map.of("can_insert", "true", "can_extract", "true", "inventory_type", "generic"));
        TransferBridgeFactory.ItemTransferBridge bridge = TransferBridgeFactory.createItemTransfer(plan, new ThrowingInventory());

        TransferBridgeFactory.OperationResult result = bridge.insertResult(
            machine,
            new TransferBridgeFactory.ItemStackView("minecraft:stone", 1),
            0,
            null,
            false
        );

        assertEquals(TransferBridgeFactory.OperationStatus.FAILED, result.status());
        assertEquals("inventory failure", result.failureReason());
        assertFalse(result.successful());
    }

    @Test
    void fluidContainerBridgeTransfersNormalizedStateToTank() {
        Identifier machine = Identifier.fromNamespaceAndPath("hydraulic", "test_machine");
        TestTank tank = new TestTank();
        CompiledCompatibilityPlan plan = runtimePlan(
            RuntimeBridgeKind.FLUID_TRANSFER,
            Map.of("can_insert_fluid", "true", "can_extract_fluid", "true", "tank_type", "generic")
        );
        TransferBridgeFactory.FluidTransferBridge transfer = TransferBridgeFactory.createFluidTransfer(plan, tank);
        FluidContainerBridge bridge = new FluidContainerBridge(transfer, 0, 1000);
        FluidContainerBridge.ContainerState container = new FluidContainerBridge.ContainerState(1000);

        container.fill("minecraft:water", 250);

        assertEquals(250, bridge.transferToTank(machine, container, "input", false));
        assertTrue(container.isEmpty());
        assertEquals(250, bridge.transferFromTank(machine, container, "minecraft:water", "output", false));
        assertEquals(250, container.amount());
        assertEquals(2, transfer.operationMetrics().snapshot().attempts());
    }

    @Test
    void fluidContainerSimulationDoesNotMutateContainerState() {
        Identifier machine = Identifier.fromNamespaceAndPath("hydraulic", "test_machine");
        TestTank tank = new TestTank();
        CompiledCompatibilityPlan plan = runtimePlan(
            RuntimeBridgeKind.FLUID_TRANSFER,
            Map.of("can_insert_fluid", "true", "can_extract_fluid", "true", "tank_type", "generic")
        );
        TransferBridgeFactory.FluidTransferBridge transfer = TransferBridgeFactory.createFluidTransfer(plan, tank);
        FluidContainerBridge bridge = new FluidContainerBridge(transfer, 0, 1000);
        FluidContainerBridge.ContainerState container = new FluidContainerBridge.ContainerState(100);
        container.fill("minecraft:water", 100);

        assertEquals(0, bridge.transferFromTank(machine, container, "minecraft:water", "output", true));
        assertEquals(100, container.amount());
        assertEquals("minecraft:water", container.fluidId());
    }

    @Test
    void fluidContainerBridgeRejectsMismatchedTankFluid() {
        Identifier machine = Identifier.fromNamespaceAndPath("hydraulic", "test_machine");
        TestTank tank = new TestTank();
        CompiledCompatibilityPlan plan = runtimePlan(
            RuntimeBridgeKind.FLUID_TRANSFER,
            Map.of("can_insert_fluid", "true", "can_extract_fluid", "true", "tank_type", "generic")
        );
        TransferBridgeFactory.FluidTransferBridge transfer = TransferBridgeFactory.createFluidTransfer(plan, tank);
        FluidContainerBridge bridge = new FluidContainerBridge(transfer, 0, 1000);
        FluidContainerBridge.ContainerState lava = new FluidContainerBridge.ContainerState(1000);
        lava.fill("minecraft:lava", 250);

        assertEquals(0, bridge.transferToTank(machine, lava, null, false));
        assertEquals(0, bridge.transferFromTank(machine, new FluidContainerBridge.ContainerState(1000), "minecraft:lava", null, false));
    }

    private static CompiledCompatibilityPlan runtimePlan(RuntimeBridgeKind kind, Map<String, String> inventoryFacts) {
        return runtimePlan(List.of(kind), inventoryFacts);
    }

    private static CompiledCompatibilityPlan runtimePlan(List<RuntimeBridgeKind> kinds, Map<String, String> inventoryFacts) {
        List<String> requirements = kinds.stream().map(RuntimeBridgeKind::requirementId).toList();
        return new CompiledCompatibilityPlan(
            "testmod",
            "block",
            Identifier.fromNamespaceAndPath("hydraulic", "test_machine").toString(),
            Identifier.fromNamespaceAndPath("hydraulic", "test_machine").toString(),
            SupportLevel.ADAPTED,
            CompatibilityStatus.PARTIAL,
            80,
            new Confidence(0.8D, "test"),
            List.of(new AdapterBinding("test.runtime_bridge", AdapterFeature.MENU_FALLBACK_TRANSLATION, "test")),
            requirements,
            kinds,
            inventoryFacts,
            false,
            null,
            false,
            null,
            false,
            false,
            false,
            false,
            false,
            null,
            null,
            List.of(),
            List.of(),
            List.of(),
            null,
            false,
            false,
            SupportLevel.UNSUPPORTED,
            null,
            List.of()
        );
    }

    private static final class TestInventory {
        private final TransferBridgeFactory.ItemStackView[] slots = new TransferBridgeFactory.ItemStackView[] {
            new TransferBridgeFactory.ItemStackView("minecraft:stone", 1),
            new TransferBridgeFactory.ItemStackView("minecraft:air", 0)
        };

        public int getContainerSize() {
            return slots.length;
        }

        public TransferBridgeFactory.ItemStackView getItem(int slot) {
            return slots[slot];
        }

        public boolean canPlaceItem(int slot, TransferBridgeFactory.ItemStackView item) {
            return slot >= 0 && slot < slots.length && !item.isEmpty()
                && (item.itemId().equals("minecraft:stone")
                || item.itemId().equals("minecraft:iron_ingot")
                || item.itemId().equals("minecraft:gold_ingot"));
        }

        public int insertItem(int slot, TransferBridgeFactory.ItemStackView item, boolean simulate) {
            if (!canPlaceItem(slot, item)) {
                return 0;
            }
            TransferBridgeFactory.ItemStackView existing = slots[slot];
            int inserted = Math.min(item.count(), 64 - existing.count());
            if (!simulate) {
                slots[slot] = new TransferBridgeFactory.ItemStackView(item.itemId(), existing.count() + inserted);
            }
            return inserted;
        }

        public int extractItem(int slot, TransferBridgeFactory.ItemStackView item, int maxCount, boolean simulate) {
            TransferBridgeFactory.ItemStackView existing = slots[slot];
            if (!existing.matches(item)) {
                return 0;
            }
            int extracted = Math.min(maxCount, existing.count());
            if (!simulate) {
                slots[slot] = new TransferBridgeFactory.ItemStackView("minecraft:air", 0);
            }
            return extracted;
        }
    }

    private static final class SidedInventory {
        private String lastSide;

        public int getContainerSize() {
            return 1;
        }

        public int insertItem(int slot, TransferBridgeFactory.ItemStackView item, String side, boolean simulate) {
            this.lastSide = side;
            return "north".equals(side) ? item.count() : 0;
        }

        public int extractItem(int slot, TransferBridgeFactory.ItemStackView item, int maxCount, String side, boolean simulate) {
            this.lastSide = side;
            return "south".equals(side) ? maxCount : 0;
        }
    }

    private static final class TestTank {
        private int amount;
        private String fluidId;

        public int getTanks() {
            return 1;
        }

        public int getTankCapacity(int tank) {
            return 1000;
        }

        public TransferBridgeFactory.FluidStackView getFluidInTank(int tank) {
            return new TransferBridgeFactory.FluidStackView(fluidId == null ? "minecraft:empty" : fluidId, amount);
        }

        public int fill(int tank, TransferBridgeFactory.FluidStackView fluid, boolean simulate) {
            if (!fluid.fluidId().equals("minecraft:water")) {
                return 0;
            }
            int space = 1000 - amount;
            int inserted = Math.min(fluid.amount(), space);
            if (!simulate) {
                fluidId = fluid.fluidId();
                amount += inserted;
            }
            return inserted;
        }

        public int drain(int tank, int amount, boolean simulate) {
            if (fluidId == null || !fluidId.equals("minecraft:water")) {
                return 0;
            }
            int extracted = Math.min(amount, this.amount);
            if (!simulate) {
                this.amount -= extracted;
                if (this.amount == 0) {
                    this.fluidId = null;
                }
            }
            return extracted;
        }
    }

    private static final class InsertOnlyInventory {
        public int insertItem(int slot, TransferBridgeFactory.ItemStackView item, boolean simulate) {
            return item.count();
        }
    }

    private static final class ForeignStackInventory {
        public int getContainerSize() {
            return 1;
        }

        public int insertItem(int slot, ForeignStack item, boolean simulate) {
            return item.count;
        }

        public int extractItem(int slot, ForeignStack item, int maxCount, boolean simulate) {
            return Math.min(item.count, maxCount);
        }
    }

    private static final class ForeignStack {
        private final int count = 1;
    }

    private static final class ThrowingInventory {
        public int getContainerSize() {
            return 1;
        }

        public int insertItem(int slot, TransferBridgeFactory.ItemStackView item, boolean simulate) {
            throw new IllegalStateException("inventory failure");
        }

        public int extractItem(int slot, TransferBridgeFactory.ItemStackView item, int maxCount, boolean simulate) {
            return 0;
        }
    }

    private static final class FillOnlyTank {
        public int fill(int tank, TransferBridgeFactory.FluidStackView fluid, boolean simulate) {
            return fluid.amount();
        }
    }

    private static final class ReceiveOnlyStorage {
        public int receiveEnergy(int amount, boolean simulate) {
            return amount;
        }
    }

    private static final class TestEnergyStorage {
        private int energy;

        public int getMaxEnergyStored() {
            return 1000;
        }

        public int getEnergyStored() {
            return energy;
        }

        public boolean canReceive() {
            return true;
        }

        public boolean canExtract() {
            return true;
        }

        public int receiveEnergy(int maxReceive, boolean simulate) {
            int inserted = Math.min(maxReceive, 1000 - energy);
            if (!simulate) {
                energy += inserted;
            }
            return inserted;
        }

        public int extractEnergy(int maxExtract, boolean simulate) {
            int extracted = Math.min(maxExtract, energy);
            if (!simulate) {
                energy -= extracted;
            }
            return extracted;
        }
    }
}