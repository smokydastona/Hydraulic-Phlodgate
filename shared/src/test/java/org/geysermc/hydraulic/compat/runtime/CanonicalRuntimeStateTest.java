package org.geysermc.hydraulic.compat.runtime;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalRuntimeStateTest {

    @Test
    @DisplayName("Identical states are equivalent and report zero diff")
    void testStateEquivalence() {
        CanonicalRuntimeState state1 = new CanonicalRuntimeState(
            "test_mod:machine_a",
            "10,64,-10",
            "minecraft:overworld",
            List.of(
                new CanonicalRuntimeState.ItemSlotState(0, "minecraft:iron_ingot", 64, Map.of()),
                new CanonicalRuntimeState.ItemSlotState(1, "minecraft:copper_ingot", 32, Map.of())
            ),
            List.of(
                new CanonicalRuntimeState.FluidTankState(0, "minecraft:water", 1000, 4000)
            ),
            5000,
            10000,
            "test_mod:recipe_refine_iron",
            100,
            Map.of("enabled", "true", "redstone_mode", "ignore")
        );

        CanonicalRuntimeState state2 = new CanonicalRuntimeState(
            "test_mod:machine_a",
            "10,64,-10",
            "minecraft:overworld",
            List.of(
                new CanonicalRuntimeState.ItemSlotState(0, "minecraft:iron_ingot", 64, Map.of()),
                new CanonicalRuntimeState.ItemSlotState(1, "minecraft:copper_ingot", 32, Map.of())
            ),
            List.of(
                new CanonicalRuntimeState.FluidTankState(0, "minecraft:water", 1000, 4000)
            ),
            5000,
            10000,
            "test_mod:recipe_refine_iron",
            100,
            Map.of("enabled", "true", "redstone_mode", "ignore")
        );

        assertTrue(state1.isEquivalentTo(state2));
        assertTrue(state1.diff(state2).isEmpty());
    }

    @Test
    @DisplayName("Different progress or energy fails equivalence and reports exact diff")
    void testStateDifference() {
        CanonicalRuntimeState state1 = new CanonicalRuntimeState(
            "test_mod:machine_a",
            "10,64,-10",
            "minecraft:overworld",
            List.of(),
            List.of(),
            5000,
            10000,
            "test_mod:recipe_a",
            50,
            Map.of()
        );

        CanonicalRuntimeState state2 = new CanonicalRuntimeState(
            "test_mod:machine_a",
            "10,64,-10",
            "minecraft:overworld",
            List.of(),
            List.of(),
            4500,
            10000,
            "test_mod:recipe_a",
            60,
            Map.of()
        );

        assertFalse(state1.isEquivalentTo(state2));
        List<String> diff = state1.diff(state2);
        assertEquals(2, diff.size());
        assertTrue(diff.get(0).contains("energyStored"));
        assertTrue(diff.get(1).contains("progressTicks"));
    }

    @Test
    @DisplayName("State serialization to and from NBT CompoundTag preserves exact data")
    void testNbtSerializationRoundTrip() {
        CanonicalRuntimeState original = new CanonicalRuntimeState(
            "create:crushing_wheel",
            "100,70,200",
            "minecraft:the_nether",
            List.of(
                new CanonicalRuntimeState.ItemSlotState(0, "minecraft:gold_ore", 16, Map.of())
            ),
            List.of(
                new CanonicalRuntimeState.FluidTankState(0, "minecraft:lava", 2000, 8000)
            ),
            8000,
            16000,
            "create:crushing/gold_ore",
            80,
            Map.of("rpm", "128", "overstressed", "false")
        );

        CompoundTag tag = original.toCompoundTag();
        CanonicalRuntimeState restored = CanonicalRuntimeState.fromCompoundTag(tag);

        assertTrue(original.isEquivalentTo(restored));
        assertEquals(original.diff(restored), List.of());
    }
}
