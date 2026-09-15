package org.geysermc.hydraulic.pack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record PackValidationReport(
    @NotNull Map<String, ModValidation> perMod
) {
    public PackValidationReport {
        perMod = Map.copyOf(new LinkedHashMap<>(perMod));
    }

    @NotNull
    public static PackValidationReport empty() {
        return new PackValidationReport(Map.of());
    }

    @NotNull
    public PackValidationReport withValidation(@NotNull String modId, @NotNull ModValidation validation) {
        Map<String, ModValidation> updated = new LinkedHashMap<>(this.perMod);
        updated.put(modId, validation);
        return new PackValidationReport(updated);
    }

    public record ModValidation(
        @NotNull String packPath,
        boolean created,
        boolean valid,
        long durationMillis,
        @NotNull List<ValidationMessage> errors,
        @NotNull List<ValidationMessage> warnings,
        @NotNull List<String> manualActions
    ) {
        public ModValidation {
            errors = List.copyOf(errors);
            warnings = List.copyOf(warnings);
            manualActions = List.copyOf(manualActions);
        }

        public int errorCount() {
            return this.errors.size();
        }

        public int warningCount() {
            return this.warnings.size();
        }

        public int manualActionCount() {
            return this.manualActions.size();
        }
    }

    public record ValidationMessage(
        @NotNull String code,
        @NotNull String message,
        @Nullable String entry,
        @NotNull FailureClassification classification
    ) {
        public ValidationMessage(@NotNull String code, @NotNull String message, @Nullable String entry) {
            this(code, message, entry, FailureClassification.classify(code, message, entry));
        }
    }

    public enum FailureClassification {
        GENERIC_GENERATOR_DEFECT,
        INVALID_MANIFEST,
        INVALID_PATH,
        MISSING_ASSET,
        UNSUPPORTED_CONTENT,
        UNKNOWN;

        @NotNull
        public static FailureClassification classify(@NotNull String code, @NotNull String message, @Nullable String entry) {
            String normalizedCode = code.toLowerCase(Locale.ROOT);
            String normalizedMessage = message == null ? "" : message.toLowerCase(Locale.ROOT);
            if (normalizedCode.contains("manifest") || normalizedMessage.contains("manifest")) {
                return INVALID_MANIFEST;
            }
            if (normalizedCode.contains("json.invalid") || normalizedCode.contains("archive.unreadable")
                || normalizedCode.contains("pack.output.missing") || normalizedCode.contains("pack.json.invalid")) {
                return GENERIC_GENERATOR_DEFECT;
            }
            if (normalizedCode.contains("path.long") || normalizedCode.contains("path") || normalizedMessage.contains("path")) {
                return INVALID_PATH;
            }
            if (normalizedCode.contains("icon.missing") || normalizedCode.contains("content.empty")
                || normalizedCode.contains("missing") || normalizedMessage.contains("missing")) {
                return MISSING_ASSET;
            }
            if (normalizedCode.contains("unsupported") || normalizedMessage.contains("unsupported")) {
                return UNSUPPORTED_CONTENT;
            }
            return UNKNOWN;
        }
    }
}