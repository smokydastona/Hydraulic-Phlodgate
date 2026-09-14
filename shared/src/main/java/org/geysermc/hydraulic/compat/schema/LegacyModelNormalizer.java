package org.geysermc.hydraulic.compat.schema;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Normalizes legacy Minecraft/Forge model JSONs into version-agnostic Model IR.
 * Handles missing textures, element rotations, and legacy display transforms.
 */
public final class LegacyModelNormalizer {

    @NotNull
    public static JsonObject normalizeModelJson(@NotNull JsonObject inputJson) {
        Objects.requireNonNull(inputJson, "inputJson");
        JsonObject normalized = inputJson.deepCopy();

        // Ensure textures block exists
        if (!normalized.has("textures") || !normalized.get("textures").isJsonObject()) {
            normalized.add("textures", new JsonObject());
        }

        // If parent is missing and elements exist, synthesize particle texture if missing
        JsonObject textures = normalized.getAsJsonObject("textures");
        if (!textures.has("particle")) {
            // Pick first available texture as particle
            for (String key : textures.keySet()) {
                JsonElement elem = textures.get(key);
                if (elem != null && elem.isJsonPrimitive()) {
                    textures.addProperty("particle", elem.getAsString());
                    break;
                }
            }
        }

        // Normalize legacy display section if present
        if (normalized.has("display") && normalized.get("display").isJsonObject()) {
            JsonObject display = normalized.getAsJsonObject("display");
            // Normalize legacy slot names (e.g. thirdperson -> thirdperson_righthand)
            if (display.has("thirdperson") && !display.has("thirdperson_righthand")) {
                display.add("thirdperson_righthand", display.get("thirdperson"));
            }
            if (display.has("firstperson") && !display.has("firstperson_righthand")) {
                display.add("firstperson_righthand", display.get("firstperson"));
            }
        }

        // Normalize elements array bounds if present
        if (normalized.has("elements") && normalized.get("elements").isJsonArray()) {
            JsonArray elements = normalized.getAsJsonArray("elements");
            for (JsonElement elem : elements) {
                if (elem != null && elem.isJsonObject()) {
                    JsonObject box = elem.getAsJsonObject();
                    sanitizeBoxCoordinates(box, "from");
                    sanitizeBoxCoordinates(box, "to");
                }
            }
        }

        return normalized;
    }

    private static void sanitizeBoxCoordinates(JsonObject box, String field) {
        if (box.has(field) && box.get(field).isJsonArray()) {
            JsonArray coords = box.getAsJsonArray(field);
            if (coords.size() == 3) {
                // Ensure float values
                for (int i = 0; i < 3; i++) {
                    try {
                        float v = coords.get(i).getAsFloat();
                        // Clamp extreme negative/positive coordinates from broken models to Bedrock-safe bounds [-64, 64]
                        if (v < -64.0f) coords.set(i, new com.google.gson.JsonPrimitive(-64.0f));
                        else if (v > 64.0f) coords.set(i, new com.google.gson.JsonPrimitive(64.0f));
                    } catch (Exception ignored) {}
                }
            }
        }
    }
}
