package org.geysermc.hydraulic.compat.runtime;

import net.minecraft.nbt.CompoundTag;
import org.geysermc.hydraulic.compat.discovery.DynamicMachineLifecycleManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MachinePersistenceValidationHarnessTest {

    static class MockGenericMachineFixture implements MachinePersistenceValidationHarness.MachineFixtureDriver {
        private final String fixtureId;
        private final String pos;
        private final String dim;

        private final List<CanonicalRuntimeState.ItemSlotState> items = new ArrayList<>();
        private final List<CanonicalRuntimeState.FluidTankState> fluids = new ArrayList<>();
        private int energy;
        private int energyCap;
        private String recipe;
        private int progress;
        private final Map<String, String> properties = new LinkedHashMap<>();

        public MockGenericMachineFixture(
            String fixtureId,
            String pos,
            String dim,
            List<CanonicalRuntimeState.ItemSlotState> initialItems,
            List<CanonicalRuntimeState.FluidTankState> initialFluids,
            int initialEnergy,
            int energyCap,
            String initialRecipe,
            int initialProgress,
            Map<String, String> initialProps
        ) {
            this.fixtureId = fixtureId;
            this.pos = pos;
            this.dim = dim;
            this.items.addAll(initialItems);
            this.fluids.addAll(initialFluids);
            this.energy = initialEnergy;
            this.energyCap = energyCap;
            this.recipe = initialRecipe;
            this.progress = initialProgress;
            this.properties.putAll(initialProps);
        }

        @Override
        public String fixtureId() {
            return fixtureId;
        }

        @Override
        public String positionKey() {
            return pos;
        }

        @Override
        public String dimensionKey() {
            return dim;
        }

        @Override
        public CanonicalRuntimeState captureState() {
            return new CanonicalRuntimeState(
                fixtureId,
                pos,
                dim,
                items,
                fluids,
                energy,
                energyCap,
                recipe,
                progress,
                properties
            );
        }

        @Override
        public CompoundTag serializeNbt() {
            return captureState().toCompoundTag();
        }

        @Override
        public void restoreFromNbt(CompoundTag nbt) {
            CanonicalRuntimeState restored = CanonicalRuntimeState.fromCompoundTag(nbt);
            this.items.clear();
            this.items.addAll(restored.inventory());
            this.fluids.clear();
            this.fluids.addAll(restored.fluids());
            this.energy = restored.energyStored();
            this.energyCap = restored.energyCapacity();
            this.recipe = restored.activeRecipeId();
            this.progress = restored.progressTicks();
            this.properties.clear();
            this.properties.putAll(restored.customProperties());
        }
    }

    @Test
    @DisplayName("Persistence Harness verifies S_after == S_pre across all 6 active machine fixtures")
    void persistenceHarnessValidatesAllMachineFixtures() {
        RuntimeDispatchTable dispatchTable = RuntimeDispatchTable.empty();
        DynamicMachineLifecycleManager lifecycleManager = new DynamicMachineLifecycleManager(dispatchTable);
        LiveCapabilityBinder binder = new LiveCapabilityBinder(lifecycleManager);

        MachinePersistenceValidationHarness.PersistenceHarnessRunner runner =
            new MachinePersistenceValidationHarness.PersistenceHarnessRunner();

        // 1. Item Transfer Machine
        runner.registerFixture(new MockGenericMachineFixture(
            "hydraulic_test_mod:item_transfer_machine",
            "10,64,10",
            "minecraft:overworld",
            List.of(
                new CanonicalRuntimeState.ItemSlotState(0, "minecraft:iron_ingot", 32, Map.of()),
                new CanonicalRuntimeState.ItemSlotState(1, "minecraft:gold_ingot", 16, Map.of())
            ),
            List.of(),
            0,
            0,
            null,
            0,
            Map.of("sided_insertion", "true", "sided_extraction", "true")
        ));

        // 2. Processing Machine
        runner.registerFixture(new MockGenericMachineFixture(
            "hydraulic_test_mod:processing_machine",
            "11,64,10",
            "minecraft:overworld",
            List.of(
                new CanonicalRuntimeState.ItemSlotState(0, "minecraft:cobblestone", 48, Map.of()),
                new CanonicalRuntimeState.ItemSlotState(1, "minecraft:stone", 16, Map.of())
            ),
            List.of(),
            0,
            0,
            "minecraft:smelting/stone",
            85,
            Map.of("has_processing", "true")
        ));

        // 3. Fluid Tank Machine
        runner.registerFixture(new MockGenericMachineFixture(
            "hydraulic_test_mod:fluid_machine",
            "12,64,10",
            "minecraft:overworld",
            List.of(),
            List.of(
                new CanonicalRuntimeState.FluidTankState(0, "minecraft:water", 2500, 4000)
            ),
            0,
            0,
            null,
            0,
            Map.of("capacity", "4000", "fluid_id", "minecraft:water")
        ));

        // 4. Energy Storage Machine
        runner.registerFixture(new MockGenericMachineFixture(
            "hydraulic_test_mod:energy_machine",
            "13,64,10",
            "minecraft:overworld",
            List.of(),
            List.of(),
            7500,
            10000,
            null,
            0,
            Map.of("can_receive_energy", "true", "can_provide_energy", "true")
        ));

        // 5. Mixed Resource Machine
        runner.registerFixture(new MockGenericMachineFixture(
            "hydraulic_test_mod:mixed_resource_machine",
            "14,64,10",
            "minecraft:overworld",
            List.of(
                new CanonicalRuntimeState.ItemSlotState(0, "minecraft:iron_ore", 12, Map.of()),
                new CanonicalRuntimeState.ItemSlotState(1, "minecraft:iron_ingot", 4, Map.of())
            ),
            List.of(
                new CanonicalRuntimeState.FluidTankState(0, "minecraft:lava", 1000, 4000)
            ),
            4000,
            10000,
            "hydraulic_test_mod:mixed_refine",
            120,
            Map.of("has_processing", "true", "requires_multiblock", "false")
        ));

        // 6. Menu Machine
        runner.registerFixture(new MockGenericMachineFixture(
            "hydraulic_test_mod:menu_machine",
            "15,64,10",
            "minecraft:overworld",
            List.of(
                new CanonicalRuntimeState.ItemSlotState(0, "minecraft:oak_log", 64, Map.of()),
                new CanonicalRuntimeState.ItemSlotState(1, "minecraft:oak_planks", 128, Map.of())
            ),
            List.of(),
            0,
            0,
            null,
            150,
            Map.of("enabled", "true", "menu_type", "menu_machine")
        ));

        // Execute full restart simulation
        MachinePersistenceValidationHarness.MachinePersistenceValidationReport report =
            runner.executeRestartSimulation(binder);

        assertEquals(6, report.totalFixturesEvaluated());
        assertEquals(6, report.passingFixtures());
        assertEquals(0, report.failingFixtures());
        assertTrue(report.allEquivalent());

        for (MachinePersistenceValidationHarness.FixturePersistenceResult res : report.results()) {
            assertTrue(res.isEquivalent(), "Fixture " + res.fixtureId() + " must be equivalent post-restart");
            assertTrue(res.diffReport().isEmpty(), "Fixture " + res.fixtureId() + " diff must be empty");
        }
    }
}
