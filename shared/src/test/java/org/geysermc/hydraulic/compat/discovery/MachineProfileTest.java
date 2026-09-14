package org.geysermc.hydraulic.compat.discovery;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MachineProfileTest {
    @Test
    void normalizesMultipleRecipesAndFluidFactsWithoutThrowing() {
        MachineProfile profile = MachineProfile.fromFacts(
            Identifier.fromNamespaceAndPath("example", "machine"),
            Map.ofEntries(
                Map.entry("can_insert", "true"),
                Map.entry("can_extract", "true"),
                Map.entry("can_insert_fluid", "true"),
                Map.entry("has_processing", "true"),
                Map.entry("machine.processing.recipe.0.input", "minecraft:iron_ingot"),
                Map.entry("machine.processing.recipe.0.output", "example:plate"),
                Map.entry("machine.processing.recipe.0.duration", "20"),
                Map.entry("machine.processing.recipe.0.fluid_input", "minecraft:water"),
                Map.entry("machine.processing.recipe.0.fluid_input_amount", "250"),
                Map.entry("machine.processing.recipe.1.input", "minecraft:copper_ingot"),
                Map.entry("machine.processing.recipe.1.output", "example:copper_plate"),
                Map.entry("machine.processing.recipe.1.input_count", "2"),
                Map.entry("machine.processing.recipe.1.output_count", "1"),
                Map.entry("machine.processing.recipe.1.duration", "invalid")
            ),
            List.of()
        );

        assertEquals(2, profile.recipes().size());
        assertEquals("minecraft:water", profile.recipes().getFirst().fluidInputs().getFirst().fluidId());
        assertEquals(250, profile.recipes().getFirst().fluidInputs().getFirst().amount());
        assertEquals(40, profile.recipes().get(1).duration());
    }
}
