package org.geysermc.hydraulic.compat.runtime;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FluidBlockUseActionPlanTest {
    @Test
    void compilesExactDrainContract() {
        FluidBlockUseActionPlan plan = FluidBlockUseActionPlan.from(Map.of(
            "interaction.fluid.action", "drain_held_container",
            "interaction.fluid.input_item", "minecraft:water_bucket",
            "interaction.fluid.output_item", "minecraft:bucket",
            "interaction.fluid.id", "minecraft:water",
            "interaction.fluid.tank", "0",
            "interaction.fluid.amount", "1000",
            "interaction.fluid.side", "up",
            "interaction.fluid.property", "3"
        ));

        assertEquals(FluidBlockUseActionPlan.Action.DRAIN_HELD_CONTAINER, plan.action());
        assertEquals("minecraft:water_bucket", plan.inputItemId());
        assertEquals("minecraft:bucket", plan.outputItemId());
        assertEquals("minecraft:water", plan.fluidId());
        assertEquals(0, plan.tank());
        assertEquals(1000, plan.amount());
        assertEquals("up", plan.side());
        assertEquals(3, plan.propertyId());
    }

    @Test
    void rejectsIncompleteOrUnsafeContracts() {
        assertNull(FluidBlockUseActionPlan.from(Map.of()));
        assertNull(FluidBlockUseActionPlan.from(Map.of(
            "interaction.fluid.action", "drain_held_container",
            "interaction.fluid.input_item", "minecraft:water_bucket",
            "interaction.fluid.output_item", "minecraft:bucket",
            "interaction.fluid.id", "minecraft:water",
            "interaction.fluid.tank", "-1",
            "interaction.fluid.amount", "1000"
        )));
        assertNull(FluidBlockUseActionPlan.from(Map.of(
            "interaction.fluid.action", "fill_held_container",
            "interaction.fluid.input_item", "minecraft:bucket",
            "interaction.fluid.output_item", "minecraft:bucket",
            "interaction.fluid.id", "minecraft:water",
            "interaction.fluid.tank", "0",
            "interaction.fluid.amount", "1000"
        )));
    }
}