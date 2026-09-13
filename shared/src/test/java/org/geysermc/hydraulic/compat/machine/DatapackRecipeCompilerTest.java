package org.geysermc.hydraulic.compat.machine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DatapackRecipeCompilerTest {

    @Test
    @DisplayName("Compile vanilla smelting recipe JSON")
    void compileVanillaSmeltingRecipe() {
        String json = """
        {
            "type": "minecraft:smelting",
            "ingredient": {
                "item": "minecraft:raw_iron"
            },
            "result": {
                "id": "minecraft:iron_ingot",
                "count": 1
            },
            "cookingtime": 200
        }
        """;

        UniversalMachineRuntime.UniversalRecipe recipe =
            DatapackRecipeCompiler.compileRecipeJson("minecraft:iron_ingot_from_smelting", json);

        assertNotNull(recipe);
        assertEquals("minecraft:iron_ingot_from_smelting", recipe.recipeId());
        assertEquals(200, recipe.totalProcessingTicks());
        assertEquals(1, recipe.itemInputs().size());
        assertEquals("minecraft:raw_iron", recipe.itemInputs().get(0).itemId());
        assertEquals(1, recipe.itemInputs().get(0).count());
        assertEquals(1, recipe.itemOutputs().size());
        assertEquals("minecraft:iron_ingot", recipe.itemOutputs().get(0).itemId());
    }

    @Test
    @DisplayName("Compile modded mixed-resource machine processing recipe with energy and fluid")
    void compileModdedMixedProcessingRecipe() {
        String json = """
        {
            "type": "tech:infuser",
            "input": {
                "item": "minecraft:copper_ingot",
                "count": 2
            },
            "fluid_input": {
                "fluid": "minecraft:water",
                "amount": 250
            },
            "output": {
                "item": "tech:infused_copper",
                "count": 1
            },
            "energy": 40,
            "duration": 80
        }
        """;

        UniversalMachineRuntime.UniversalRecipe recipe =
            DatapackRecipeCompiler.compileRecipeJson("tech:infused_copper_recipe", json);

        assertNotNull(recipe);
        assertEquals("tech:infused_copper_recipe", recipe.recipeId());
        assertEquals(80, recipe.totalProcessingTicks());
        assertEquals(40, recipe.energyRequiredPerTick());
        assertEquals(1, recipe.itemInputs().size());
        assertEquals("minecraft:copper_ingot", recipe.itemInputs().get(0).itemId());
        assertEquals(2, recipe.itemInputs().get(0).count());
        assertEquals(1, recipe.fluidInputs().size());
        assertEquals("minecraft:water", recipe.fluidInputs().get(0).fluidId());
        assertEquals(250, recipe.fluidInputs().get(0).amount());
        assertEquals(1, recipe.itemOutputs().size());
        assertEquals("tech:infused_copper", recipe.itemOutputs().get(0).itemId());
    }
}
