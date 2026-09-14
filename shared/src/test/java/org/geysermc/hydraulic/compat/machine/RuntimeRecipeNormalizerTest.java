package org.geysermc.hydraulic.compat.machine;

import com.google.gson.JsonParser;
import org.geysermc.hydraulic.compat.model.Confidence;
import org.geysermc.hydraulic.compat.runtime.TransferBridgeFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeRecipeNormalizerTest {
    @Test
    void normalizesPortableRuntimeCodecJson() {
        RuntimeRecipeNormalizer.Result result = RuntimeRecipeNormalizer.normalizeEncoded(
            "custom:smelt_ore", "minecraft:smelting", "minecraft:smelting",
            JsonParser.parseString("""
                {"type":"minecraft:smelting","ingredient":{"item":"minecraft:raw_iron"},"result":{"id":"minecraft:iron_ingot"},"cookingtime":200}
                """).getAsJsonObject()
        );

        assertEquals(RuntimeRecipeNormalizer.Status.NORMALIZED, result.status());
        assertNotNull(result.recipe());
        assertEquals(RecipeIR.Source.RUNTIME_CODEC, result.recipe().source());
        assertEquals("minecraft:smelting", result.recipe().serializer());
        assertEquals(200, result.recipe().duration());
    }

    @Test
    void reportsOpaqueRuntimeSemanticsAsUnknown() {
        RuntimeRecipeNormalizer.Result result = RuntimeRecipeNormalizer.normalizeEncoded(
            "custom:opaque", "custom:opaque", "custom:machine",
            JsonParser.parseString("{\"type\":\"custom:opaque\",\"payload\":{\"binary\":\"AAE=\"}}").getAsJsonObject()
        );

        assertEquals(RuntimeRecipeNormalizer.Status.RECIPE_RUNTIME_UNKNOWN, result.status());
        assertNull(result.recipe());
        assertNotNull(result.reason());
    }

    @Test
    void rejectsUnregisteredCustomSerializerEvenWhenFieldsLookGeneric() {
        RuntimeRecipeNormalizer.Result result = RuntimeRecipeNormalizer.normalizeEncoded(
            "custom:deceptive", "custom:unregistered", "custom:machine",
            JsonParser.parseString("{\"input\":{\"item\":\"minecraft:stone\"},\"output\":{\"item\":\"minecraft:diamond\"}}").getAsJsonObject()
        );

        assertEquals(RuntimeRecipeNormalizer.Status.RECIPE_RUNTIME_UNKNOWN, result.status());
        assertTrue(result.reason().contains("No registered runtime recipe adapter"));
    }

    @Test
    void refusesExecutableProjectionWhenEvidenceContainsUnsupportedSemantics() {
        RecipeIR recipe = new RecipeIR(
            "custom:tagged", "custom:machine", List.of(), List.of("minecraft:logs"), List.of(), 0,
            List.of(new TransferBridgeFactory.ItemStackView("minecraft:charcoal", 1)), List.of(), List.of(),
            20, List.of(), List.of(), 0, "custom:machine", RecipeIR.Source.RUNTIME_CODEC,
            new Confidence(1.0D, "test")
        );

        assertThrows(IllegalStateException.class, recipe::executableRecipe);
    }
}