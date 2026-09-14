package org.geysermc.hydraulic.compat.machine;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.geysermc.hydraulic.compat.model.Confidence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Normalizes live RecipeManager entries through Minecraft's registry-aware recipe codec. */
public final class RuntimeRecipeNormalizer {
    private RuntimeRecipeNormalizer() {
    }

    @NotNull
    public static Result normalize(@NotNull RecipeHolder<?> holder, @NotNull HolderLookup.Provider registries) {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(registries, "registries");
        String recipeId = holder.id().identifier().toString();
        Recipe<?> recipe = holder.value();
        String serializer = identifierOrUnknown(BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer()));
        String recipeType = identifierOrUnknown(BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()));
        if ("unknown".equals(serializer) || "unknown".equals(recipeType)) {
            return Result.unknown(recipeId, serializer, recipeType, "Recipe serializer or type is not registered");
        }

        try {
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
            JsonElement encoded = Recipe.CODEC.encodeStart(ops, recipe).result().orElse(null);
            if (encoded == null || !encoded.isJsonObject()) {
                return Result.unknown(recipeId, serializer, recipeType, "Recipe codec produced no portable JSON object");
            }
            return normalizeEncoded(recipeId, serializer, recipeType, encoded.getAsJsonObject());
        } catch (Throwable throwable) {
            return Result.unknown(recipeId, serializer, recipeType,
                "Recipe codec failed: " + throwable.getClass().getSimpleName());
        }
    }

    @NotNull
    static Result normalizeEncoded(
        @NotNull String recipeId,
        @NotNull String serializer,
        @NotNull String recipeType,
        @NotNull JsonObject json
    ) {
        if (!serializer.startsWith("minecraft:") && SpecializedRecipeSerializerRegistry.get(serializer) == null) {
            return Result.unknown(recipeId, serializer, recipeType,
                "No registered runtime recipe adapter for serializer");
        }
        RecipeIR recipeIR = DatapackRecipeCompiler.compileRecipeIr(
            recipeId, json, serializer, RecipeIR.Source.RUNTIME_CODEC,
            new Confidence(1.0D, "minecraft_recipe_codec")
        );
        if (recipeIR == null) {
            return Result.unknown(recipeId, serializer, recipeType,
                "Serialized recipe semantics are not executable by the generic runtime");
        }
        return Result.normalized(recipeIR);
    }

    private static String identifierOrUnknown(@Nullable net.minecraft.resources.Identifier identifier) {
        return identifier == null ? "unknown" : identifier.toString();
    }

    public record Result(
        @NotNull Status status,
        @NotNull String recipeId,
        @NotNull String serializer,
        @NotNull String recipeType,
        @Nullable RecipeIR recipe,
        @Nullable String reason
    ) {
        @NotNull
        private static Result normalized(@NotNull RecipeIR recipe) {
            return new Result(Status.NORMALIZED, recipe.recipeId(), recipe.serializer(), recipe.recipeType(), recipe, null);
        }

        @NotNull
        private static Result unknown(String recipeId, String serializer, String recipeType, String reason) {
            return new Result(Status.RECIPE_RUNTIME_UNKNOWN, recipeId, serializer, recipeType, null, reason);
        }
    }

    public enum Status {
        NORMALIZED,
        RECIPE_RUNTIME_UNKNOWN
    }
}
