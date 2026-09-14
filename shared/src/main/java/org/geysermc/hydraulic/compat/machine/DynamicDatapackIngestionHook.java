package org.geysermc.hydraulic.compat.machine;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.geysermc.hydraulic.compat.runtime.TransferBridgeFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dynamic Datapack Ingestion Hook (Phase 3 Extension).
 * Automatically invoked on ServerLifecycleEvents.SERVER_STARTED to scan active World
 * datapack recipe registries and compile them into executable UniversalRecipe instances.
 */
public final class DynamicDatapackIngestionHook {
    private static final Logger LOGGER = LoggerFactory.getLogger("HydraulicRecipeIngestion");
    private static volatile Map<String, UniversalMachineRuntime.UniversalRecipe> ingestedRecipes = Map.of();
    private static volatile Map<String, RecipeIR> ingestedRecipeIr = Map.of();
    private static volatile Map<String, RuntimeRecipeNormalizer.Result> runtimeRecipeEvidence = Map.of();

    private DynamicDatapackIngestionHook() {
    }

    public static synchronized int ingest(@Nullable MinecraftServer server) {
        if (server == null) {
            return 0;
        }
        int count = 0;
        int recipeManagerEntries = 0;
        int runtimeNormalized = 0;
        int runtimeUnknown = 0;
        Map<String, UniversalMachineRuntime.UniversalRecipe> stagedRecipes = new LinkedHashMap<>();
        Map<String, RecipeIR> stagedRecipeIr = new LinkedHashMap<>();
        Map<String, RuntimeRecipeNormalizer.Result> stagedEvidence = new LinkedHashMap<>();
        try {
            // Attempt 1: Scan server.getResourceManager()
            Object resourceManager = invokeMethod(server, "getResourceManager", "resourceManager");
            if (resourceManager != null) {
                Method listMethod = findMethod(resourceManager.getClass(), "listResources", "findResources");
                if (listMethod != null) {
                    Map<?, ?> resources = (Map<?, ?>) listMethod.invoke(resourceManager, "recipes", (java.util.function.Predicate<Object>) o -> {
                        String str = o.toString();
                        return str.endsWith(".json");
                    });
                    if (resources != null) {
                        for (Map.Entry<?, ?> entry : resources.entrySet()) {
                            String id = entry.getKey().toString();
                            Object res = entry.getValue();
                            String json = readResourceContent(res);
                            if (json != null) {
                                RecipeIR compiled = DatapackRecipeCompiler.compileRecipeIrJson(id, json);
                                if (compiled != null) {
                                    stagedRecipeIr.put(compiled.recipeId(), compiled);
                                    stagedRecipes.put(compiled.recipeId(), compiled.executableRecipe());
                                    count++;
                                }
                            }
                        }
                    }
                }
            }

            // Attempt 2: normalize live recipe-manager entries through Minecraft's
            // registry-aware Recipe codec. Entries that cannot be represented safely remain
            // explicit RECIPE_RUNTIME_UNKNOWN evidence and never become executable plans.
            for (RecipeHolder<?> recipeHolder : server.getRecipeManager().getRecipes()) {
                recipeManagerEntries++;
                String recipeId = recipeHolder.id().identifier().toString();
                if (stagedRecipes.containsKey(recipeId)) {
                    continue;
                }
                RuntimeRecipeNormalizer.Result result = RuntimeRecipeNormalizer.normalize(recipeHolder, server.registryAccess());
                stagedEvidence.put(recipeId, result);
                if (result.recipe() != null) {
                    stagedRecipeIr.put(recipeId, result.recipe());
                    stagedRecipes.put(recipeId, result.recipe().executableRecipe());
                    count++;
                    runtimeNormalized++;
                } else {
                    runtimeUnknown++;
                    LOGGER.debug("Recipe manager entry {} remains {}: {}", recipeId, result.status(), result.reason());
                }
            }
            ingestedRecipes = Map.copyOf(stagedRecipes);
            ingestedRecipeIr = Map.copyOf(stagedRecipeIr);
            runtimeRecipeEvidence = Map.copyOf(stagedEvidence);
            LOGGER.info(
                "Dynamic Datapack Ingestion Hook compiled {} recipes, inspected {} recipe-manager entries, normalized {} runtime entries, and classified {} as RECIPE_RUNTIME_UNKNOWN.",
                count, recipeManagerEntries, runtimeNormalized, runtimeUnknown
            );
        } catch (Throwable t) {
            LOGGER.warn("Dynamic Datapack Ingestion encountered a non-fatal issue during recipe scan: {}", t.getMessage());
        }
        return count;
    }

    public static synchronized void registerRecipe(@NotNull UniversalMachineRuntime.UniversalRecipe recipe) {
        Map<String, UniversalMachineRuntime.UniversalRecipe> recipes = new LinkedHashMap<>(ingestedRecipes);
        recipes.put(recipe.recipeId(), recipe);
        ingestedRecipes = Map.copyOf(recipes);
    }

    public static synchronized void registerRecipe(@NotNull RecipeIR recipe) {
        Map<String, RecipeIR> recipeIr = new LinkedHashMap<>(ingestedRecipeIr);
        recipeIr.put(recipe.recipeId(), recipe);
        ingestedRecipeIr = Map.copyOf(recipeIr);
        Map<String, UniversalMachineRuntime.UniversalRecipe> recipes = new LinkedHashMap<>(ingestedRecipes);
        recipes.put(recipe.recipeId(), recipe.executableRecipe());
        ingestedRecipes = Map.copyOf(recipes);
    }

    @NotNull
    public static Map<String, UniversalMachineRuntime.UniversalRecipe> getIngestedRecipes() {
        return ingestedRecipes;
    }

    @NotNull
    public static Map<String, RecipeIR> getIngestedRecipeIr() {
        return ingestedRecipeIr;
    }

    @Nullable
    public static UniversalMachineRuntime.UniversalRecipe getRecipe(@NotNull String recipeId) {
        return ingestedRecipes.get(recipeId);
    }

    @NotNull
    public static Map<String, RuntimeRecipeNormalizer.Result> getRuntimeRecipeEvidence() {
        return runtimeRecipeEvidence;
    }

    @NotNull
    public static List<UniversalMachineRuntime.UniversalRecipe> findRecipesForInput(@NotNull String itemId) {
        List<UniversalMachineRuntime.UniversalRecipe> matches = new ArrayList<>();
        for (UniversalMachineRuntime.UniversalRecipe recipe : ingestedRecipes.values()) {
            for (TransferBridgeFactory.ItemStackView in : recipe.itemInputs()) {
                if (in.itemId().equalsIgnoreCase(itemId)) {
                    matches.add(recipe);
                    break;
                }
            }
        }
        return matches;
    }

    private static String readResourceContent(Object resource) {
        try {
            Method openMethod = findMethod(resource.getClass(), "open", "getInputStream", "openAsReader");
            if (openMethod != null) {
                Object result = openMethod.invoke(resource);
                if (result instanceof InputStream is) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line).append('\n');
                        }
                        return sb.toString();
                    }
                } else if (result instanceof java.io.Reader r) {
                    try (BufferedReader reader = new BufferedReader(r)) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line).append('\n');
                        }
                        return sb.toString();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object invokeMethod(Object target, String... names) {
        for (String name : names) {
            try {
                Method m = target.getClass().getMethod(name);
                return m.invoke(target);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> clazz, String... names) {
        for (String name : names) {
            for (Method m : clazz.getMethods()) {
                if (m.getName().equalsIgnoreCase(name)) {
                    return m;
                }
            }
        }
        return null;
    }
}
