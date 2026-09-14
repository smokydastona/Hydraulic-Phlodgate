package org.geysermc.hydraulic.compat.schema;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class LegacyModelNormalizerTest {

    @Test
    public void testNormalizeModelJsonSynthesizesParticleTexture() {
        JsonObject model = new JsonObject();
        JsonObject textures = new JsonObject();
        textures.addProperty("layer0", "item/custom_sword");
        model.add("textures", textures);

        JsonObject normalized = LegacyModelNormalizer.normalizeModelJson(model);
        assertTrue(normalized.has("textures"));
        assertEquals("item/custom_sword", normalized.getAsJsonObject("textures").get("particle").getAsString());
    }

    @Test
    public void testNormalizeModelJsonNormalizesDisplaySlots() {
        JsonObject model = new JsonObject();
        JsonObject display = new JsonObject();
        JsonObject thirdPerson = new JsonObject();
        thirdPerson.addProperty("scale", 0.5f);
        display.add("thirdperson", thirdPerson);
        model.add("display", display);

        JsonObject normalized = LegacyModelNormalizer.normalizeModelJson(model);
        assertTrue(normalized.getAsJsonObject("display").has("thirdperson_righthand"));
    }

    @Test
    public void testNormalizeModelClampsExtremeCoordinates() {
        JsonObject model = new JsonObject();
        JsonArray elements = new JsonArray();
        JsonObject element = new JsonObject();

        JsonArray from = new JsonArray();
        from.add(new JsonPrimitive(-120.0f));
        from.add(new JsonPrimitive(0.0f));
        from.add(new JsonPrimitive(0.0f));
        element.add("from", from);

        JsonArray to = new JsonArray();
        to.add(new JsonPrimitive(120.0f));
        to.add(new JsonPrimitive(16.0f));
        to.add(new JsonPrimitive(16.0f));
        element.add("to", to);

        elements.add(element);
        model.add("elements", elements);

        JsonObject normalized = LegacyModelNormalizer.normalizeModelJson(model);
        JsonArray normFrom = normalized.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonArray("from");
        JsonArray normTo = normalized.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonArray("to");

        assertEquals(-64.0f, normFrom.get(0).getAsFloat(), 0.001f);
        assertEquals(64.0f, normTo.get(0).getAsFloat(), 0.001f);
    }
}
