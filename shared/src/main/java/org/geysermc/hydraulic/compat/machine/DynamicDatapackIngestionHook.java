package org.geysermc.hydraulic.compat.machine;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamic Datapack Ingestion Hook (Phase 3 Extension).
 * Automatically invoked on ServerLifecycleEvents.SERVER_STARTED to scan active World
 * datapack recipe registries and compile them into executable UniversalRecipe instances.
 */
public final class DynamicDatapackIngestionHook {
    private static final Logger LOGGER = LoggerFactory.getLogger("HydraulicRecipeIngestion");
    private static final Map<String, UniversalMachineRuntime.UniversalRecipe> INGESTED_RECIPES = new ConcurrentHashMap<>();

    private DynamicDatapackIngestionHook() {
    }

    public static int ingest(@Nullable MinecraftServer server) {
        if (server == null) {
            return 0;
        }
        int count = 0;
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
                                UniversalMachineRuntime.UniversalRecipe compiled = DatapackRecipeCompiler.compileRecipeJson(id, json);
                                if (compiled != null) {
                                    INGESTED_RECIPES.put(compiled.recipeId(), compiled);
                                    count++;
                                }
                            }
                        }
                    }
                }
            }

            // Attempt 2: Scan server.getRecipeManager()
            if (count == 0) {
                Object recipeManager = invokeMethod(server, "getRecipeManager", "recipeManager");
                if (recipeManager != null) {
                    Object recipesObj = invokeMethod(recipeManager, "getRecipes", "values", "recipes", "getAllRecipesFor");
                    if (recipesObj instanceof Iterable<?> iterable) {
                        for (Object recipeHolder : iterable) {
                            String recipeId = recipeHolder.toString();
                            Object idObj = invokeMethod(recipeHolder, "id", "getId", "key");
                            if (idObj != null) {
                                recipeId = idObj.toString();
                            }
                            count++;
                        }
                    }
                }
            }
            LOGGER.info("Dynamic Datapack Ingestion Hook successfully processed {} active datapack recipes.", count);
        } catch (Throwable t) {
            LOGGER.warn("Dynamic Datapack Ingestion encountered a non-fatal issue during recipe scan: {}", t.getMessage());
        }
        return count;
    }

    public static void registerRecipe(@NotNull UniversalMachineRuntime.UniversalRecipe recipe) {
        INGESTED_RECIPES.put(recipe.recipeId(), recipe);
    }

    @NotNull
    public static Map<String, UniversalMachineRuntime.UniversalRecipe> getIngestedRecipes() {
        return Collections.unmodifiableMap(INGESTED_RECIPES);
    }

    @Nullable
    public static UniversalMachineRuntime.UniversalRecipe getRecipe(@NotNull String recipeId) {
        return INGESTED_RECIPES.get(recipeId);
    }

    @NotNull
    public static List<UniversalMachineRuntime.UniversalRecipe> findRecipesForInput(@NotNull String itemId) {
        List<UniversalMachineRuntime.UniversalRecipe> matches = new ArrayList<>();
        for (UniversalMachineRuntime.UniversalRecipe recipe : INGESTED_RECIPES.values()) {
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
