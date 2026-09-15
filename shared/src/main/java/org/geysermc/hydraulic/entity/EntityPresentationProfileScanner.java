package org.geysermc.hydraulic.entity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.geysermc.hydraulic.pack.ModResourceIndex;
import org.geysermc.hydraulic.platform.mod.ModInfo;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads the portable presentation part of Easy Model Entities profiles.
 *
 * <p>The upstream project owns a Java renderer and its artistic assets. Hydraulic only consumes
 * profile facts as conversion evidence; runtime behavior and assets remain owned by the loaded
 * mod and the existing Hydraulic pack pipeline.</p>
 */
public final class EntityPresentationProfileScanner {
    private static final int MAX_PROFILE_BYTES = 256 * 1024;
    private static final String PROFILE_ROOT = "easy_model_entities/profiles/";
    private static final String RENDER_PROFILE_ROOT = "easy_model_entities/render_profiles/";

    private EntityPresentationProfileScanner() {
    }

    @NotNull
    public static Report scan(@NotNull ModInfo mod, @NotNull ModResourceIndex index, @NotNull Logger logger) {
        List<Profile> profiles = new ArrayList<>();
        Set<String> indexedFiles = indexedFiles(index);
        for (ModResourceIndex.FileStamp stamp : index.fileStamps()) {
            String stablePath = stamp.stablePath();
            String category = category(stablePath);
            if (!"assets".equals(category) && !"data".equals(category)) {
                continue;
            }
            int categorySeparator = stablePath.indexOf(category + ":");
            String relative = categorySeparator < 0 ? "" : stablePath.substring(categorySeparator + category.length() + 1);
            String prefix = category.equals("data") ? PROFILE_ROOT : RENDER_PROFILE_ROOT;
            String marker = "/" + prefix;
            int markerStart = relative.indexOf(marker);
            if (markerStart < 0 || !relative.endsWith(".json")) {
                continue;
            }
            String namespace = relative.substring(0, markerStart);
            String profilePath = relative.substring(markerStart + marker.length());
            String type = profilePath.startsWith("entity/") ? "entity" : profilePath.startsWith("block_entity/") ? "block_entity" : null;
            if (type == null) {
                continue;
            }
            String profileId = profilePath.substring(type.length() + 1, profilePath.length() - ".json".length());
            profiles.add(readProfile(mod.id(), namespace, type, profileId, category, stamp.path(), indexedFiles, logger));
        }
        profiles.sort(Comparator.comparing(Profile::path));
        return new Report(mod.id(), profiles);
    }

    @NotNull
    private static Profile readProfile(
        @NotNull String modId,
        @NotNull String namespace,
        @NotNull String type,
        @NotNull String profileId,
        @NotNull String category,
        @NotNull String file,
        @NotNull Set<String> indexedFiles,
        @NotNull Logger logger
    ) {
        String path = namespace + ":" + type + "/" + profileId;
        List<Issue> issues = new ArrayList<>();
        JsonObject root;
        try {
            Path profileFile = Path.of(file);
            if (Files.size(profileFile) > MAX_PROFILE_BYTES) {
                return new Profile(path, category, file, false, List.of(new Issue("PROFILE_TOO_LARGE", "Profile exceeds the 256 KiB safety limit.")));
            }
            JsonElement parsed = JsonParser.parseString(Files.readString(profileFile, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                return new Profile(path, category, file, false, List.of(new Issue("PROFILE_NOT_OBJECT", "Profile root must be a JSON object.")));
            }
            root = parsed.getAsJsonObject();
        } catch (IOException | RuntimeException exception) {
            logger.warn("Unable to inspect entity presentation profile {} from {}", path, file, exception);
            return new Profile(path, category, file, false, List.of(new Issue("PROFILE_INVALID_JSON", "Profile could not be parsed.")));
        }

        requiredString(root, "schema_version", issues);
        requiredString(root, "version", issues);
        if (category.equals("data")) {
            requiredString(root, "model_type", issues);
            String modelType = string(root, "model_type");
            if (modelType != null && !type.equals(modelType)) {
                issues.add(new Issue("MODEL_TYPE_MISMATCH", "model_type does not match the profile directory."));
            }
        } else {
            String model = requiredString(root, "model", issues);
            String texture = requiredString(root, "texture", issues);
            if (model != null && !hasAsset(indexedFiles, model, ".bbmodel", ".json")) {
                issues.add(new Issue("MODEL_ASSET_MISSING", "Referenced model asset is not present in the indexed resources."));
            }
            if (texture != null && !hasAsset(indexedFiles, texture, ".png", ".tga")) {
                issues.add(new Issue("TEXTURE_ASSET_MISSING", "Referenced texture asset is not present in the indexed resources."));
            }
            JsonElement animation = root.get("animation");
            if (animation != null && !animation.isJsonObject()) {
                issues.add(new Issue("ANIMATION_NOT_OBJECT", "animation must be a JSON object when present."));
            }
        }
        return new Profile(path, category, file, issues.isEmpty(), issues);
    }

    @NotNull
    private static Set<String> indexedFiles(@NotNull ModResourceIndex index) {
        Set<String> files = new LinkedHashSet<>();
        for (ModResourceIndex.FileStamp stamp : index.fileStamps()) {
            String stablePath = stamp.stablePath();
            int separator = stablePath.indexOf(':', stablePath.indexOf(':') + 1);
            if (separator >= 0) {
                files.add(stablePath.substring(separator + 1).replace('\\', '/'));
            }
        }
        return files;
    }

    private static boolean hasAsset(@NotNull Set<String> indexedFiles, @NotNull String raw, @NotNull String... extensions) {
        String normalized = raw.replace('\\', '/');
        int separator = normalized.indexOf(':');
        if (separator <= 0 || separator == normalized.length() - 1) {
            return false;
        }
        String namespace = normalized.substring(0, separator);
        String asset = normalized.substring(separator + 1);
        for (String extension : extensions) {
            String indexedPath = namespace + "/" + asset;
            if (asset.endsWith(extension) && indexedFiles.contains(indexedPath)) {
                return true;
            }
            if (indexedFiles.contains(indexedPath + extension)) {
                return true;
            }
        }
        return false;
    }

    private static String category(@NotNull String stablePath) {
        int first = stablePath.indexOf(':');
        int second = stablePath.indexOf(':', first + 1);
        return first >= 0 && second > first ? stablePath.substring(first + 1, second) : "";
    }

    private static String requiredString(@NotNull JsonObject root, @NotNull String name, @NotNull List<Issue> issues) {
        String value = string(root, name);
        if (value == null || value.isBlank()) {
            issues.add(new Issue("REQUIRED_FIELD_MISSING", "Required string field is missing: " + name));
            return null;
        }
        return value;
    }

    private static String string(@NotNull JsonObject root, @NotNull String name) {
        JsonElement element = root.get(name);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString() ? element.getAsString() : null;
    }

    public record Report(@NotNull String modId, @NotNull List<Profile> profiles) {
        public Report {
            profiles = List.copyOf(profiles);
        }

        public long validCount() {
            return profiles.stream().filter(Profile::valid).count();
        }
    }

    public record Profile(@NotNull String path, @NotNull String category, @NotNull String source, boolean valid, @NotNull List<Issue> issues) {
        public Profile {
            issues = List.copyOf(issues);
        }
    }

    public record Issue(@NotNull String code, @NotNull String message) {
    }
}