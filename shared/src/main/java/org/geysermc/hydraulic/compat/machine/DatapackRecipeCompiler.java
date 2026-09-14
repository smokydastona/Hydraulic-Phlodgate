package org.geysermc.hydraulic.compat.machine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import org.geysermc.hydraulic.compat.runtime.TransferBridgeFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Universal Datapack and Modded Recipe Compiler (Phase 3 Extension).
 * Compiles Minecraft RecipeManager and modded recipe JSONs directly into
 * executable UniversalMachineRuntime.UniversalRecipe instances.
 */
public final class DatapackRecipeCompiler {

    private DatapackRecipeCompiler() {
    }

    @Nullable
    public static UniversalMachineRuntime.UniversalRecipe compileRecipeJson(@NotNull String recipeId, @NotNull String jsonContent) {
        try {
            JsonElement parsed = JsonParser.parseString(jsonContent);
            if (!parsed.isJsonObject()) {
                return null;
            }
            return compile(recipeId, parsed.getAsJsonObject());
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    public static UniversalMachineRuntime.UniversalRecipe compile(@NotNull String recipeId, @NotNull JsonObject json) {
        UniversalMachineRuntime.UniversalRecipe specialized =
            SpecializedRecipeSerializerRegistry.tryParseSpecialized(recipeId, json);
        if (specialized != null) {
            return specialized;
        }

        String type = json.has("type") && json.get("type").isJsonPrimitive()
            ? json.get("type").getAsString()
            : "minecraft:crafting";

        List<TransferBridgeFactory.ItemStackView> itemInputs = new ArrayList<>();
        List<TransferBridgeFactory.FluidStackView> fluidInputs = new ArrayList<>();
        List<TransferBridgeFactory.ItemStackView> itemOutputs = new ArrayList<>();
        List<TransferBridgeFactory.FluidStackView> fluidOutputs = new ArrayList<>();
        int duration = 100;
        int energyPerTick = 0;
        int energyGenerated = 0;

        // Extract duration / cooking time
        if (json.has("cookingtime") && json.get("cookingtime").isJsonPrimitive()) {
            duration = json.get("cookingtime").getAsInt();
        } else if (json.has("time") && json.get("time").isJsonPrimitive()) {
            duration = json.get("time").getAsInt();
        } else if (json.has("processingTime") && json.get("processingTime").isJsonPrimitive()) {
            duration = json.get("processingTime").getAsInt();
        } else if (json.has("duration") && json.get("duration").isJsonPrimitive()) {
            duration = json.get("duration").getAsInt();
        }

        // Extract energy
        if (json.has("energy") && json.get("energy").isJsonPrimitive()) {
            energyPerTick = json.get("energy").getAsInt();
        } else if (json.has("energyPerTick") && json.get("energyPerTick").isJsonPrimitive()) {
            energyPerTick = json.get("energyPerTick").getAsInt();
        } else if (json.has("energy_cost") && json.get("energy_cost").isJsonPrimitive()) {
            energyPerTick = json.get("energy_cost").getAsInt();
        } else if (json.has("power") && json.get("power").isJsonPrimitive()) {
            energyPerTick = json.get("power").getAsInt();
        }

        // Extract energy generated (for dynamos / generators)
        if (json.has("energy_generated") && json.get("energy_generated").isJsonPrimitive()) {
            energyGenerated = json.get("energy_generated").getAsInt();
        } else if (json.has("energyGenerated") && json.get("energyGenerated").isJsonPrimitive()) {
            energyGenerated = json.get("energyGenerated").getAsInt();
        } else if (json.has("energy_production") && json.get("energy_production").isJsonPrimitive()) {
            energyGenerated = json.get("energy_production").getAsInt();
        } else if (json.has("power_generated") && json.get("power_generated").isJsonPrimitive()) {
            energyGenerated = json.get("power_generated").getAsInt();
        }

        // Extract inputs & catalysts
        if (json.has("ingredient")) {
            extractItemInputs(json.get("ingredient"), itemInputs);
        } else if (json.has("ingredients")) {
            extractItemInputs(json.get("ingredients"), itemInputs);
        } else if (json.has("input")) {
            extractItemInputs(json.get("input"), itemInputs);
        } else if (json.has("inputs")) {
            extractItemInputs(json.get("inputs"), itemInputs);
        } else if (json.has("item_in")) {
            extractItemInputs(json.get("item_in"), itemInputs);
        }

        // Extract multiblock catalysts / secondary tools
        if (json.has("catalyst")) {
            extractItemInputs(json.get("catalyst"), itemInputs);
        } else if (json.has("catalysts")) {
            extractItemInputs(json.get("catalysts"), itemInputs);
        } else if (json.has("tool")) {
            extractItemInputs(json.get("tool"), itemInputs);
        }

        // Extract fluid inputs
        if (json.has("fluid_input")) {
            extractFluidInputs(json.get("fluid_input"), fluidInputs);
        } else if (json.has("fluid_inputs")) {
            extractFluidInputs(json.get("fluid_inputs"), fluidInputs);
        } else if (json.has("fluid_in")) {
            extractFluidInputs(json.get("fluid_in"), fluidInputs);
        }

        // Extract outputs
        if (json.has("result")) {
            extractItemOutputs(json.get("result"), itemOutputs);
        } else if (json.has("output")) {
            extractItemOutputs(json.get("output"), itemOutputs);
        } else if (json.has("outputs")) {
            extractItemOutputs(json.get("outputs"), itemOutputs);
        } else if (json.has("results")) {
            extractItemOutputs(json.get("results"), itemOutputs);
        } else if (json.has("item_out")) {
            extractItemOutputs(json.get("item_out"), itemOutputs);
        }

        // Extract secondary byproducts / extra outputs
        if (json.has("byproduct")) {
            extractItemOutputs(json.get("byproduct"), itemOutputs);
        } else if (json.has("byproducts")) {
            extractItemOutputs(json.get("byproducts"), itemOutputs);
        } else if (json.has("extra_output")) {
            extractItemOutputs(json.get("extra_output"), itemOutputs);
        } else if (json.has("secondary_output")) {
            extractItemOutputs(json.get("secondary_output"), itemOutputs);
        }

        // Extract fluid outputs
        if (json.has("fluid_output")) {
            extractFluidOutputs(json.get("fluid_output"), fluidOutputs);
        } else if (json.has("fluid_outputs")) {
            extractFluidOutputs(json.get("fluid_outputs"), fluidOutputs);
        } else if (json.has("fluid_out")) {
            extractFluidOutputs(json.get("fluid_out"), fluidOutputs);
        }

        if (itemInputs.isEmpty() && fluidInputs.isEmpty()) {
            return null;
        }
        if (itemOutputs.isEmpty() && fluidOutputs.isEmpty()) {
            return null;
        }

        return new UniversalMachineRuntime.UniversalRecipe(
            recipeId,
            itemInputs,
            fluidInputs,
            energyPerTick,
            Math.max(1, duration),
            itemOutputs,
            fluidOutputs,
            energyGenerated
        );
    }

    private static void extractItemInputs(JsonElement element, List<TransferBridgeFactory.ItemStackView> list) {
        if (element == null) return;
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            String item = getString(obj, "item", "id", "tag");
            int count = getInt(obj, 1, "count", "amount");
            if (item != null) {
                list.add(new TransferBridgeFactory.ItemStackView(item, count));
            }
        } else if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement el : array) {
                extractItemInputs(el, list);
            }
        } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            list.add(new TransferBridgeFactory.ItemStackView(element.getAsString(), 1));
        }
    }

    private static void extractItemOutputs(JsonElement element, List<TransferBridgeFactory.ItemStackView> list) {
        if (element == null) return;
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            String item = getString(obj, "item", "id");
            int count = getInt(obj, 1, "count", "amount");
            if (item != null) {
                list.add(new TransferBridgeFactory.ItemStackView(item, count));
            }
        } else if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement el : array) {
                extractItemOutputs(el, list);
            }
        } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            list.add(new TransferBridgeFactory.ItemStackView(element.getAsString(), 1));
        }
    }

    private static void extractFluidInputs(JsonElement element, List<TransferBridgeFactory.FluidStackView> list) {
        if (element == null) return;
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            String fluid = getString(obj, "fluid", "id");
            int amount = getInt(obj, 1000, "amount", "count", "mb");
            if (fluid != null) {
                list.add(new TransferBridgeFactory.FluidStackView(fluid, amount));
            }
        } else if (element.isJsonArray()) {
            for (JsonElement el : element.getAsJsonArray()) {
                extractFluidInputs(el, list);
            }
        }
    }

    private static void extractFluidOutputs(JsonElement element, List<TransferBridgeFactory.FluidStackView> list) {
        if (element == null) return;
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            String fluid = getString(obj, "fluid", "id");
            int amount = getInt(obj, 1000, "amount", "count", "mb");
            if (fluid != null) {
                list.add(new TransferBridgeFactory.FluidStackView(fluid, amount));
            }
        } else if (element.isJsonArray()) {
            for (JsonElement el : element.getAsJsonArray()) {
                extractFluidOutputs(el, list);
            }
        }
    }

    @Nullable
    private static String getString(JsonObject obj, String... keys) {
        for (String k : keys) {
            if (obj.has(k) && obj.get(k).isJsonPrimitive() && obj.get(k).getAsJsonPrimitive().isString()) {
                return obj.get(k).getAsString();
            }
        }
        return null;
    }

    private static int getInt(JsonObject obj, int fallback, String... keys) {
        for (String k : keys) {
            if (obj.has(k) && obj.get(k).isJsonPrimitive() && obj.get(k).getAsJsonPrimitive().isNumber()) {
                return obj.get(k).getAsInt();
            }
        }
        return fallback;
    }
}
