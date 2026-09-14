package org.geysermc.hydraulic.compat.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Validates and normalizes generated Bedrock pack schemas against current Bedrock client format specifications.
 * Ensures strict compliance with mandatory Bedrock store updates (inspired by BedrockConnect and itzg/bds rules).
 */
public final class BedrockSchemaValidator {
    public static final String MINIMUM_RESOURCE_FORMAT_VERSION = "1.21.0";
    public static final String MINIMUM_GEOMETRY_FORMAT_VERSION = "1.21.0";
    public static final String MINIMUM_ATTACHABLE_FORMAT_VERSION = "1.10.0";

    public static final class ValidationResult {
        private final boolean valid;
        private final String message;

        public ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }
    }

    @NotNull
    public static ValidationResult validateManifest(@NotNull JsonObject manifest) {
        Objects.requireNonNull(manifest, "manifest");
        if (!manifest.has("format_version")) {
            return new ValidationResult(false, "Missing format_version in manifest");
        }
        if (!manifest.has("header") || !manifest.get("header").isJsonObject()) {
            return new ValidationResult(false, "Missing or invalid header in manifest");
        }
        JsonObject header = manifest.getAsJsonObject("header");
        if (!header.has("uuid") || !header.has("version") || !header.has("name")) {
            return new ValidationResult(false, "Header missing uuid, version, or name");
        }
        if (!manifest.has("modules") || !manifest.get("modules").isJsonArray()) {
            return new ValidationResult(false, "Missing modules array in manifest");
        }
        return new ValidationResult(true, "Manifest is valid");
    }

    @NotNull
    public static JsonObject normalizeGeometrySchema(@NotNull JsonObject geometryJson) {
        Objects.requireNonNull(geometryJson, "geometryJson");
        JsonObject normalized = geometryJson.deepCopy();
        if (!normalized.has("format_version")) {
            normalized.addProperty("format_version", MINIMUM_GEOMETRY_FORMAT_VERSION);
        }
        return normalized;
    }

    @NotNull
    public static JsonObject normalizeAttachableSchema(@NotNull JsonObject attachableJson) {
        Objects.requireNonNull(attachableJson, "attachableJson");
        JsonObject normalized = attachableJson.deepCopy();
        if (!normalized.has("format_version")) {
            normalized.addProperty("format_version", MINIMUM_ATTACHABLE_FORMAT_VERSION);
        }
        return normalized;
    }

    @NotNull
    public static JsonObject normalizeBlocksJson(@NotNull JsonObject blocksJson) {
        Objects.requireNonNull(blocksJson, "blocksJson");
        JsonObject normalized = blocksJson.deepCopy();
        if (!normalized.has("format_version")) {
            normalized.addProperty("format_version", "1.21.0");
        }
        return normalized;
    }
}
