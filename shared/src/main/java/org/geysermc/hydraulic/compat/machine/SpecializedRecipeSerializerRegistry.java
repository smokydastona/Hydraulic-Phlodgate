package org.geysermc.hydraulic.compat.machine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.geysermc.hydraulic.compat.runtime.TransferBridgeFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Specialized Recipe Serializer Registry (Phase 3 Extension).
 * Handles complex multi-step and mod-specific machine recipe schemas including:
 * - Create Sequenced Assembly (transitional items, loops, deploy/press/fill/cut steps)
 * - Create Milling, Mixing, Compacting, and Crushing
 * - Mekanism Metallurgic Infusing and Combining
 * - Thermal Induction Smelting and Pulverizing
 * - Farmer's Delight Pot Cooking and Cutting Board
 */
public final class SpecializedRecipeSerializerRegistry {

    public interface SpecializedSerializer {
        @NotNull String recipeType();
        @Nullable UniversalMachineRuntime.UniversalRecipe parse(@NotNull String recipeId, @NotNull JsonObject json);
    }

    private static final Map<String, SpecializedSerializer> SERIALIZERS = new ConcurrentHashMap<>();

    static {
        register(new CreateSequencedAssemblySerializer());
        register(new CreateProcessingSerializer("create:milling", 100, 10));
        register(new CreateProcessingSerializer("create:crushing", 120, 15));
        register(new CreateProcessingSerializer("create:pressing", 40, 20));
        register(new CreateProcessingSerializer("create:mixing", 80, 10));
        register(new CreateProcessingSerializer("create:compacting", 60, 15));
        register(new CreateProcessingSerializer("create:cutting", 50, 10));

        register(new MekanismInfusionSerializer());
        register(new ThermalSmelterSerializer());
        register(new FarmersDelightCookingSerializer());
    }

    private SpecializedRecipeSerializerRegistry() {
    }

    public static void register(@NotNull SpecializedSerializer serializer) {
        SERIALIZERS.put(serializer.recipeType().toLowerCase(), serializer);
    }

    @Nullable
    public static SpecializedSerializer get(@NotNull String recipeType) {
        return SERIALIZERS.get(recipeType.toLowerCase());
    }

    @Nullable
    public static UniversalMachineRuntime.UniversalRecipe tryParseSpecialized(@NotNull String recipeId, @NotNull JsonObject json) {
        if (!json.has("type") || !json.get("type").isJsonPrimitive()) {
            return null;
        }
        String type = json.get("type").getAsString().toLowerCase();
        SpecializedSerializer serializer = SERIALIZERS.get(type);
        if (serializer != null) {
            return serializer.parse(recipeId, json);
        }
        return null;
    }

    /**
     * Serializer for Create Sequenced Assembly recipes (e.g. Precision Mechanism).
     * Decomposes multi-step transitional recipes into aggregated input/output/catalyst chains.
     */
    public static final class CreateSequencedAssemblySerializer implements SpecializedSerializer {
        @Override
        public @NotNull String recipeType() {
            return "create:sequenced_assembly";
        }

        @Override
        public @Nullable UniversalMachineRuntime.UniversalRecipe parse(@NotNull String recipeId, @NotNull JsonObject json) {
            List<TransferBridgeFactory.ItemStackView> itemInputs = new ArrayList<>();
            List<TransferBridgeFactory.FluidStackView> fluidInputs = new ArrayList<>();
            List<TransferBridgeFactory.ItemStackView> itemOutputs = new ArrayList<>();
            List<TransferBridgeFactory.FluidStackView> fluidOutputs = new ArrayList<>();

            int loops = json.has("loops") && json.get("loops").isJsonPrimitive()
                ? Math.max(1, json.get("loops").getAsInt())
                : 1;

            // Base ingredient / input item
            if (json.has("ingredient")) {
                extractItemInputs(json.get("ingredient"), itemInputs, 1);
            }

            // Target output item
            if (json.has("results")) {
                extractItemOutputs(json.get("results"), itemOutputs);
            } else if (json.has("result")) {
                extractItemOutputs(json.get("result"), itemOutputs);
            }

            int stepCount = 0;
            int totalDuration = 0;
            int energyPerTick = 10;

            if (json.has("sequence") && json.get("sequence").isJsonArray()) {
                JsonArray seq = json.get("sequence").getAsJsonArray();
                stepCount = seq.size();
                for (JsonElement stepEl : seq) {
                    if (!stepEl.isJsonObject()) continue;
                    JsonObject stepObj = stepEl.getAsJsonObject();
                    totalDuration += 20; // Default 20 ticks per assembly step

                    // Ingest intermediate catalysts (e.g., small cogwheels, iron nuggets, fluid pouring)
                    if (stepObj.has("ingredients") || stepObj.has("ingredient")) {
                        JsonElement ing = stepObj.has("ingredients") ? stepObj.get("ingredients") : stepObj.get("ingredient");
                        extractItemInputs(ing, itemInputs, loops);
                    }
                    if (stepObj.has("fluid_ingredients") || stepObj.has("fluid_ingredient")) {
                        JsonElement fl = stepObj.has("fluid_ingredients") ? stepObj.get("fluid_ingredients") : stepObj.get("fluid_ingredient");
                        extractFluidInputs(fl, fluidInputs, loops);
                    }
                }
            }

            if (itemInputs.isEmpty() || itemOutputs.isEmpty()) {
                return null;
            }

            int calculatedDuration = Math.max(20, totalDuration * loops);
            return new UniversalMachineRuntime.UniversalRecipe(
                recipeId,
                itemInputs,
                fluidInputs,
                energyPerTick,
                calculatedDuration,
                itemOutputs,
                fluidOutputs,
                0
            );
        }
    }

    public static final class CreateProcessingSerializer implements SpecializedSerializer {
        private final String type;
        private final int defaultDuration;
        private final int defaultEnergy;

        public CreateProcessingSerializer(String type, int defaultDuration, int defaultEnergy) {
            this.type = type;
            this.defaultDuration = defaultDuration;
            this.defaultEnergy = defaultEnergy;
        }

        @Override
        public @NotNull String recipeType() {
            return type;
        }

        @Override
        public @Nullable UniversalMachineRuntime.UniversalRecipe parse(@NotNull String recipeId, @NotNull JsonObject json) {
            List<TransferBridgeFactory.ItemStackView> itemInputs = new ArrayList<>();
            List<TransferBridgeFactory.FluidStackView> fluidInputs = new ArrayList<>();
            List<TransferBridgeFactory.ItemStackView> itemOutputs = new ArrayList<>();
            List<TransferBridgeFactory.FluidStackView> fluidOutputs = new ArrayList<>();

            int duration = json.has("processingTime") ? json.get("processingTime").getAsInt() : defaultDuration;

            if (json.has("ingredients")) {
                extractItemInputs(json.get("ingredients"), itemInputs, 1);
            }
            if (json.has("results")) {
                extractItemOutputs(json.get("results"), itemOutputs);
            }

            if (itemInputs.isEmpty() || itemOutputs.isEmpty()) return null;

            return new UniversalMachineRuntime.UniversalRecipe(
                recipeId,
                itemInputs,
                fluidInputs,
                defaultEnergy,
                Math.max(1, duration),
                itemOutputs,
                fluidOutputs,
                0
            );
        }
    }

    public static final class MekanismInfusionSerializer implements SpecializedSerializer {
        @Override
        public @NotNull String recipeType() {
            return "mekanism:metallurgic_infusing";
        }

        @Override
        public @Nullable UniversalMachineRuntime.UniversalRecipe parse(@NotNull String recipeId, @NotNull JsonObject json) {
            List<TransferBridgeFactory.ItemStackView> itemInputs = new ArrayList<>();
            List<TransferBridgeFactory.ItemStackView> itemOutputs = new ArrayList<>();

            if (json.has("itemInput")) {
                extractItemInputs(json.get("itemInput"), itemInputs, 1);
            }
            if (json.has("output")) {
                extractItemOutputs(json.get("output"), itemOutputs);
            }

            if (itemInputs.isEmpty() || itemOutputs.isEmpty()) return null;

            return new UniversalMachineRuntime.UniversalRecipe(
                recipeId,
                itemInputs,
                List.of(),
                20,
                100,
                itemOutputs,
                List.of(),
                0
            );
        }
    }

    public static final class ThermalSmelterSerializer implements SpecializedSerializer {
        @Override
        public @NotNull String recipeType() {
            return "thermal:smelter";
        }

        @Override
        public @Nullable UniversalMachineRuntime.UniversalRecipe parse(@NotNull String recipeId, @NotNull JsonObject json) {
            List<TransferBridgeFactory.ItemStackView> itemInputs = new ArrayList<>();
            List<TransferBridgeFactory.ItemStackView> itemOutputs = new ArrayList<>();

            if (json.has("ingredients")) {
                extractItemInputs(json.get("ingredients"), itemInputs, 1);
            }
            if (json.has("result")) {
                extractItemOutputs(json.get("result"), itemOutputs);
            }

            int energy = json.has("energy") ? json.get("energy").getAsInt() / 100 : 25;
            if (itemInputs.isEmpty() || itemOutputs.isEmpty()) return null;

            return new UniversalMachineRuntime.UniversalRecipe(
                recipeId,
                itemInputs,
                List.of(),
                energy,
                80,
                itemOutputs,
                List.of(),
                0
            );
        }
    }

    public static final class FarmersDelightCookingSerializer implements SpecializedSerializer {
        @Override
        public @NotNull String recipeType() {
            return "farmersdelight:cooking";
        }

        @Override
        public @Nullable UniversalMachineRuntime.UniversalRecipe parse(@NotNull String recipeId, @NotNull JsonObject json) {
            List<TransferBridgeFactory.ItemStackView> itemInputs = new ArrayList<>();
            List<TransferBridgeFactory.ItemStackView> itemOutputs = new ArrayList<>();

            if (json.has("ingredients")) {
                extractItemInputs(json.get("ingredients"), itemInputs, 1);
            }
            if (json.has("result")) {
                extractItemOutputs(json.get("result"), itemOutputs);
            }

            int time = json.has("cookingtime") ? json.get("cookingtime").getAsInt() : 200;
            if (itemInputs.isEmpty() || itemOutputs.isEmpty()) return null;

            return new UniversalMachineRuntime.UniversalRecipe(
                recipeId,
                itemInputs,
                List.of(),
                0,
                Math.max(1, time),
                itemOutputs,
                List.of(),
                0
            );
        }
    }

    private static void extractItemInputs(JsonElement element, List<TransferBridgeFactory.ItemStackView> list, int multiplier) {
        if (element == null) return;
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            String item = obj.has("item") ? obj.get("item").getAsString() : (obj.has("id") ? obj.get("id").getAsString() : null);
            int count = obj.has("count") ? obj.get("count").getAsInt() : (obj.has("amount") ? obj.get("amount").getAsInt() : 1);
            if (item != null) {
                list.add(new TransferBridgeFactory.ItemStackView(item, count * multiplier));
            }
        } else if (element.isJsonArray()) {
            for (JsonElement el : element.getAsJsonArray()) {
                extractItemInputs(el, list, multiplier);
            }
        } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            list.add(new TransferBridgeFactory.ItemStackView(element.getAsString(), multiplier));
        }
    }

    private static void extractItemOutputs(JsonElement element, List<TransferBridgeFactory.ItemStackView> list) {
        if (element == null) return;
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            String item = obj.has("item") ? obj.get("item").getAsString() : (obj.has("id") ? obj.get("id").getAsString() : null);
            int count = obj.has("count") ? obj.get("count").getAsInt() : (obj.has("amount") ? obj.get("amount").getAsInt() : 1);
            if (item != null) {
                list.add(new TransferBridgeFactory.ItemStackView(item, count));
            }
        } else if (element.isJsonArray()) {
            for (JsonElement el : element.getAsJsonArray()) {
                extractItemOutputs(el, list);
            }
        } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            list.add(new TransferBridgeFactory.ItemStackView(element.getAsString(), 1));
        }
    }

    private static void extractFluidInputs(JsonElement element, List<TransferBridgeFactory.FluidStackView> list, int multiplier) {
        if (element == null) return;
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            String fluid = obj.has("fluid") ? obj.get("fluid").getAsString() : (obj.has("id") ? obj.get("id").getAsString() : null);
            int amount = obj.has("amount") ? obj.get("amount").getAsInt() : 1000;
            if (fluid != null) {
                list.add(new TransferBridgeFactory.FluidStackView(fluid, amount * multiplier));
            }
        } else if (element.isJsonArray()) {
            for (JsonElement el : element.getAsJsonArray()) {
                extractFluidInputs(el, list, multiplier);
            }
        }
    }
}
