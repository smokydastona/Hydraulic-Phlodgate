package org.geysermc.hydraulic.compat.machine;

import org.geysermc.hydraulic.compat.runtime.TransferBridgeFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DynamicDatapackIngestionHookTest {

    @Test
    @DisplayName("DynamicDatapackIngestionHook safely handles null server without throwing")
    void handlesNullServerSafely() {
        int count = DynamicDatapackIngestionHook.ingest(null);
        assertEquals(0, count);
    }

    @Test
    @DisplayName("DynamicDatapackIngestionHook registers and queries compiled recipes")
    void registerAndQueryRecipes() {
        UniversalMachineRuntime.UniversalRecipe recipe = new UniversalMachineRuntime.UniversalRecipe(
            "custom:crush_gold",
            List.of(new TransferBridgeFactory.ItemStackView("minecraft:raw_gold", 1)),
            List.of(),
            20,
            100,
            List.of(new TransferBridgeFactory.ItemStackView("minecraft:gold_ingot", 2)),
            List.of(),
            0
        );

        DynamicDatapackIngestionHook.registerRecipe(recipe);

        UniversalMachineRuntime.UniversalRecipe retrieved = DynamicDatapackIngestionHook.getRecipe("custom:crush_gold");
        assertNotNull(retrieved);
        assertEquals(100, retrieved.totalProcessingTicks());

        List<UniversalMachineRuntime.UniversalRecipe> matches =
            DynamicDatapackIngestionHook.findRecipesForInput("minecraft:raw_gold");
        assertFalse(matches.isEmpty());
        assertEquals("custom:crush_gold", matches.get(0).recipeId());
    }
}
