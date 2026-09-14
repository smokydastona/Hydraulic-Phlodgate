package org.geysermc.hydraulic.compat.machine;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.geysermc.hydraulic.compat.runtime.TransferBridgeFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Discovers and normalizes recipe JSON from mod/data roots without requiring mod-specific metadata.
 * It delegates schema interpretation to the shared datapack and specialized serializers, while
 * retaining diagnostics for missing, malformed, unsupported, and successfully compiled recipes.
 */
public final class AutomaticRecipeDiscovery {
    public enum Status {
        COMPILED,
        MALFORMED,
        UNSUPPORTED_SCHEMA,
        IO_FAILURE
    }

    public record DiscoveredRecipe(
        @NotNull String recipeId,
        @NotNull Path sourcePath,
        @NotNull Status status,
        @Nullable UniversalMachineRuntime.UniversalRecipe compiledRecipe,
        @NotNull List<TransferBridgeFactory.ItemStackView> catalysts,
        @NotNull List<TransferBridgeFactory.ItemStackView> byproducts,
        @NotNull Map<String, String> conditions,
        @Nullable String error
    ) {
        public DiscoveredRecipe {
            Objects.requireNonNull(recipeId, "recipeId");
            Objects.requireNonNull(sourcePath, "sourcePath");
            Objects.requireNonNull(status, "status");
            catalysts = List.copyOf(catalysts);
            byproducts = List.copyOf(byproducts);
            conditions = Map.copyOf(new LinkedHashMap<>(conditions));
        }
    }

    public record DiscoveryReport(
        int scanned,
        int compiled,
        int malformed,
        int unsupported,
        int ioFailures,
        @NotNull List<DiscoveredRecipe> recipes
    ) {
        public DiscoveryReport {
            recipes = List.copyOf(recipes);
        }
    }

    private AutomaticRecipeDiscovery() {
    }

    /**
     * Scans one or more mod roots. Only paths below data/&lt;namespace&gt;/recipes are accepted.
     */
    @NotNull
    public static DiscoveryReport discover(@NotNull List<Path> roots) {
        List<DiscoveredRecipe> recipes = new ArrayList<>();
        for (Path root : roots) {
            if (root == null || !Files.isDirectory(root)) {
                continue;
            }
            Path normalizedRoot = root.toAbsolutePath().normalize();
            Path dataRoot = normalizedRoot.resolve("data").normalize();
            if (!dataRoot.startsWith(normalizedRoot) || !Files.isDirectory(dataRoot)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(dataRoot)) {
                files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .filter(path -> path.toString().contains("\\recipes\\") || path.toString().contains("/recipes/"))
                    .sorted()
                    .forEach(path -> recipes.add(readRecipe(normalizedRoot, path)));
            } catch (IOException e) {
                recipes.add(new DiscoveredRecipe(
                    normalizedRoot.toString(),
                    normalizedRoot,
                    Status.IO_FAILURE,
                    null,
                    List.of(),
                    List.of(),
                    Map.of(),
                    e.getMessage()
                ));
            }
        }

        int compiled = count(recipes, Status.COMPILED);
        int malformed = count(recipes, Status.MALFORMED);
        int unsupported = count(recipes, Status.UNSUPPORTED_SCHEMA);
        int ioFailures = count(recipes, Status.IO_FAILURE);
        return new DiscoveryReport(recipes.size(), compiled, malformed, unsupported, ioFailures, recipes);
    }

    @NotNull
    public static Map<String, UniversalMachineRuntime.UniversalRecipe> compileAll(@NotNull DiscoveryReport report) {
        Map<String, UniversalMachineRuntime.UniversalRecipe> compiled = new LinkedHashMap<>();
        for (DiscoveredRecipe recipe : report.recipes()) {
            if (recipe.compiledRecipe() != null) {
                compiled.put(recipe.recipeId(), recipe.compiledRecipe());
            }
        }
        return Collections.unmodifiableMap(compiled);
    }

    @NotNull
    private static DiscoveredRecipe readRecipe(@NotNull Path root, @NotNull Path path) {
        String recipeId = recipeId(root, path);
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                return failed(recipeId, path, Status.MALFORMED, "Recipe root is not an object.");
            }
            JsonObject json = parsed.getAsJsonObject();
            UniversalMachineRuntime.UniversalRecipe compiled = DatapackRecipeCompiler.compile(recipeId, json);
            if (compiled == null) {
                return failed(recipeId, path, Status.UNSUPPORTED_SCHEMA, "No supported item/fluid input and output schema was found.");
            }
            return new DiscoveredRecipe(
                recipeId,
                path,
                Status.COMPILED,
                compiled,
                parseStacks(json, "catalyst", "catalysts", false),
                parseStacks(json, "byproduct", "byproducts", true),
                parseConditions(json),
                null
            );
        } catch (Exception e) {
            return failed(recipeId, path, Status.MALFORMED, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @NotNull
    private static DiscoveredRecipe failed(String id, Path path, Status status, String error) {
        return new DiscoveredRecipe(id, path, status, null, List.of(), List.of(), Map.of(), error);
    }

    @NotNull
    private static String recipeId(@NotNull Path root, @NotNull Path path) {
        Path relative = root.relativize(path);
        List<String> parts = new ArrayList<>();
        for (Path part : relative) {
            parts.add(part.toString());
        }
        int dataIndex = parts.indexOf("data");
        if (dataIndex < 0 || parts.size() < dataIndex + 4) {
            return path.toString();
        }
        String namespace = parts.get(dataIndex + 1);
        int recipesIndex = parts.indexOf("recipes");
        if (recipesIndex < 0 || recipesIndex + 1 >= parts.size()) {
            return path.toString();
        }
        StringBuilder pathPart = new StringBuilder();
        for (int i = recipesIndex + 1; i < parts.size(); i++) {
            if (pathPart.length() > 0) {
                pathPart.append('/');
            }
            pathPart.append(parts.get(i));
        }
        String value = pathPart.toString();
        if (value.endsWith(".json")) {
            value = value.substring(0, value.length() - 5);
        }
        return namespace + ":" + value;
    }

    @NotNull
    private static List<TransferBridgeFactory.ItemStackView> parseStacks(JsonObject json, String singular, String plural, boolean output) {
        List<TransferBridgeFactory.ItemStackView> stacks = new ArrayList<>();
        JsonElement element = json.has(plural) ? json.get(plural) : json.get(singular);
        if (element == null) {
            return stacks;
        }
        collectStacks(element, stacks, output);
        return stacks;
    }

    private static void collectStacks(JsonElement element, List<TransferBridgeFactory.ItemStackView> stacks, boolean output) {
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectStacks(child, stacks, output);
            }
            return;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            stacks.add(new TransferBridgeFactory.ItemStackView(element.getAsString(), 1));
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        JsonObject object = element.getAsJsonObject();
        String id = firstString(object, "item", "id");
        if (id == null) {
            return;
        }
        int count = firstPositiveInt(object, 1, "count", "amount");
        stacks.add(new TransferBridgeFactory.ItemStackView(id, count));
    }

    @NotNull
    private static Map<String, String> parseConditions(JsonObject json) {
        Map<String, String> conditions = new LinkedHashMap<>();
        for (String key : List.of("heat", "temperature", "kinetic", "stress", "power", "energy", "duration", "processingTime")) {
            if (json.has(key) && json.get(key).isJsonPrimitive()) {
                conditions.put(key, json.get(key).getAsString());
            }
        }
        JsonElement conditionArray = json.get("conditions");
        if (conditionArray != null && conditionArray.isJsonArray()) {
            conditions.put("conditions.count", Integer.toString(conditionArray.getAsJsonArray().size()));
        }
        return conditions;
    }

    @Nullable
    private static String firstString(JsonObject object, String... keys) {
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                String text = value.getAsString().trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return null;
    }

    private static int firstPositiveInt(JsonObject object, int fallback, String... keys) {
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                int number = value.getAsInt();
                if (number > 0) {
                    return number;
                }
            }
        }
        return fallback;
    }

    private static int count(List<DiscoveredRecipe> recipes, Status status) {
        return (int) recipes.stream().filter(recipe -> recipe.status() == status).count();
    }
}
