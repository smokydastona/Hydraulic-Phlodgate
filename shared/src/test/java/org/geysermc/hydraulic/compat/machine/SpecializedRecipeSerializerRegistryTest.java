package org.geysermc.hydraulic.compat.machine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpecializedRecipeSerializerRegistryTest {

    @Test
    @DisplayName("Compile Create Sequenced Assembly recipe (Precision Mechanism)")
    void compileCreateSequencedAssemblyRecipe() {
        String json = """
        {
            "type": "create:sequenced_assembly",
            "ingredient": {
                "item": "create:brass_sheet"
            },
            "results": [
                {
                    "item": "create:precision_mechanism",
                    "count": 1
                }
            ],
            "loops": 5,
            "sequence": [
                {
                    "type": "create:deploying",
                    "ingredients": [
                        { "item": "create:brass_sheet" },
                        { "item": "create:cogwheel" }
                    ]
                },
                {
                    "type": "create:deploying",
                    "ingredients": [
                        { "item": "create:incomplete_precision_mechanism" },
                        { "item": "create:large_cogwheel" }
                    ]
                },
                {
                    "type": "create:deploying",
                    "ingredients": [
                        { "item": "create:incomplete_precision_mechanism" },
                        { "item": "minecraft:iron_nugget" }
                    ]
                }
            ]
        }
        """;

        UniversalMachineRuntime.UniversalRecipe recipe =
            DatapackRecipeCompiler.compileRecipeJson("create:precision_mechanism", json);

        assertNotNull(recipe);
        assertEquals("create:precision_mechanism", recipe.recipeId());
        assertEquals(300, recipe.totalProcessingTicks()); // 3 steps * 20 ticks * 5 loops = 300
        assertEquals(1, recipe.itemOutputs().size());
        assertEquals("create:precision_mechanism", recipe.itemOutputs().get(0).itemId());
        assertTrue(recipe.itemInputs().size() >= 4); // base + 3 intermediate deploy ingredients
    }

    @Test
    @DisplayName("Compile Mekanism Metallurgic Infusing recipe")
    void compileMekanismInfusionRecipe() {
        String json = """
        {
            "type": "mekanism:metallurgic_infusing",
            "itemInput": {
                "item": "minecraft:iron_ingot"
            },
            "output": {
                "item": "mekanism:enriched_iron",
                "count": 1
            }
        }
        """;

        UniversalMachineRuntime.UniversalRecipe recipe =
            DatapackRecipeCompiler.compileRecipeJson("mekanism:infusion_enriched_iron", json);

        assertNotNull(recipe);
        assertEquals("mekanism:infusion_enriched_iron", recipe.recipeId());
        assertEquals(1, recipe.itemInputs().size());
        assertEquals("minecraft:iron_ingot", recipe.itemInputs().get(0).itemId());
        assertEquals("mekanism:enriched_iron", recipe.itemOutputs().get(0).itemId());
    }

    @Test
    @DisplayName("Compile Farmer's Delight Pot Cooking recipe")
    void compileFarmersDelightCookingRecipe() {
        String json = """
        {
            "type": "farmersdelight:cooking",
            "ingredients": [
                { "item": "minecraft:beef" },
                { "item": "minecraft:carrot" },
                { "item": "minecraft:potato" }
            ],
            "result": {
                "item": "farmersdelight:beef_stew",
                "count": 1
            },
            "cookingtime": 200
        }
        """;

        UniversalMachineRuntime.UniversalRecipe recipe =
            DatapackRecipeCompiler.compileRecipeJson("farmersdelight:cooking/beef_stew", json);

        assertNotNull(recipe);
        assertEquals("farmersdelight:cooking/beef_stew", recipe.recipeId());
        assertEquals(200, recipe.totalProcessingTicks());
        assertEquals(3, recipe.itemInputs().size());
        assertEquals("farmersdelight:beef_stew", recipe.itemOutputs().get(0).itemId());
    }
}
