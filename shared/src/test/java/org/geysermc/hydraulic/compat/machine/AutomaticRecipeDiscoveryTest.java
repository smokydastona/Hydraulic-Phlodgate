package org.geysermc.hydraulic.compat.machine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutomaticRecipeDiscoveryTest {
    @Test
    void discoversRecipeWithoutHydraulicMetadata(@TempDir Path root) throws IOException {
        Path recipePath = root.resolve("data/create/recipes/pressing/iron_plate.json");
        Files.createDirectories(recipePath.getParent());
        Files.writeString(recipePath, """
            {
              "type": "create:pressing",
              "ingredients": [{"item": "minecraft:iron_ingot", "count": 1}],
              "results": [{"item": "create:iron_sheet", "count": 1}],
              "processingTime": 40
            }
            """);

        AutomaticRecipeDiscovery.DiscoveryReport report = AutomaticRecipeDiscovery.discover(List.of(root));

        assertEquals(1, report.scanned());
        assertEquals(1, report.compiled());
        assertEquals(0, report.malformed());
        assertEquals(0, report.unsupported());
        AutomaticRecipeDiscovery.DiscoveredRecipe discovered = report.recipes().getFirst();
        assertEquals("create:pressing/iron_plate", discovered.recipeId());
        assertNotNull(discovered.compiledRecipe());
        assertNotNull(discovered.normalizedRecipe());
        assertEquals(1, AutomaticRecipeDiscovery.normalizeAll(report).size());
        assertEquals(40, discovered.compiledRecipe().totalProcessingTicks());
    }

    @Test
    void reportsUnimplementedEnvironmentalRequirementsAsUnsupported(@TempDir Path root) throws IOException {
        Path recipePath = root.resolve("data/create/recipes/mixing/heated.json");
        Files.createDirectories(recipePath.getParent());
        Files.writeString(recipePath, """
            {"type":"create:mixing","ingredients":[{"item":"minecraft:iron_ingot"}],"results":[{"item":"minecraft:iron_block"}],"heat":"heated"}
            """);

        AutomaticRecipeDiscovery.DiscoveryReport report = AutomaticRecipeDiscovery.discover(List.of(root));

        assertEquals(1, report.unsupported());
        assertTrue(AutomaticRecipeDiscovery.normalizeAll(report).isEmpty());
    }

    @Test
    void reportsMalformedAndUnsupportedRecipes(@TempDir Path root) throws IOException {
        Path recipes = root.resolve("data/example/recipes");
        Files.createDirectories(recipes);
        Files.writeString(recipes.resolve("malformed.json"), "{not-json");
        Files.writeString(recipes.resolve("unsupported.json"), "{\"type\":\"example:unknown\"}");

        AutomaticRecipeDiscovery.DiscoveryReport report = AutomaticRecipeDiscovery.discover(List.of(root));

        assertEquals(2, report.scanned());
        assertEquals(1, report.malformed());
        assertEquals(1, report.unsupported());
        assertTrue(report.recipes().stream().allMatch(recipe -> recipe.compiledRecipe() == null));
    }
}
