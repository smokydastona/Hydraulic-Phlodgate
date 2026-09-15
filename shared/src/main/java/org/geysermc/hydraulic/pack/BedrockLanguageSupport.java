package org.geysermc.hydraulic.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import org.geysermc.pack.bedrock.resource.BedrockResourcePack;
import org.geysermc.pack.bedrock.resource.Languages;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class BedrockLanguageSupport {
    public static final String DEFAULT_LANGUAGE = "en_US";

    private BedrockLanguageSupport() {
    }

    public static void includeIndexedLanguages(
        @NotNull BedrockResourcePack bedrockPack,
        @NotNull ModResourceIndex resourceIndex,
        @NotNull Logger logger
    ) {
        for (Map.Entry<Identifier, Path> entry : resourceIndex.languagePaths().entrySet()) {
            String languageCode = bedrockLanguageCode(entry.getKey().getPath());
            Map<String, String> translations = readTranslations(entry.getValue(), logger);
            if (!translations.isEmpty()) {
                mergeLanguage(bedrockPack, languageCode, translations);
            }
        }
    }

    public static void includeDefaultTranslation(
        @NotNull BedrockResourcePack bedrockPack,
        @NotNull String translationKey,
        @NotNull String fallbackName
    ) {
        Map<String, String> translations = new LinkedHashMap<>();
        translations.put(translationKey, fallbackName);
        mergeLanguage(bedrockPack, DEFAULT_LANGUAGE, translations);
    }

    @NotNull
    public static String fallbackName(@NotNull Identifier identifier) {
        String path = identifier.getPath();
        int slash = path.lastIndexOf('/');
        if (slash >= 0 && slash < path.length() - 1) {
            path = path.substring(slash + 1);
        }

        StringBuilder builder = new StringBuilder();
        boolean capitalizeNext = true;
        for (int i = 0; i < path.length(); i++) {
            char character = path.charAt(i);
            if (character == '_' || character == '-' || character == '.') {
                if (!builder.isEmpty() && builder.charAt(builder.length() - 1) != ' ') {
                    builder.append(' ');
                }
                capitalizeNext = true;
                continue;
            }

            if (capitalizeNext) {
                builder.append(Character.toUpperCase(character));
                capitalizeNext = false;
            } else {
                builder.append(character);
            }
        }

        String value = builder.toString().trim();
        return value.isEmpty() ? identifier.toString() : value;
    }

    @NotNull
    private static Map<String, String> readTranslations(@NotNull Path path, @NotNull Logger logger) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                logger.warn("Skipping language file {} because the root is not a JSON object", path);
                return Map.of();
            }

            Map<String, String> translations = new LinkedHashMap<>();
            JsonObject object = root.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                JsonElement value = entry.getValue();
                if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                    translations.put(entry.getKey(), value.getAsString());
                }
            }
            return translations;
        } catch (IOException | RuntimeException e) {
            logger.warn("Skipping unreadable language file {}", path, e);
            return Map.of();
        }
    }

    private static void mergeLanguage(
        @NotNull BedrockResourcePack bedrockPack,
        @NotNull String languageCode,
        @NotNull Map<String, String> translations
    ) {
        if (bedrockPack.languages() == null) {
            bedrockPack.languages(new Languages());
        }
        Map<String, String> merged = new LinkedHashMap<>(bedrockPack.languages().language(languageCode));
        translations.forEach(merged::putIfAbsent);
        bedrockPack.addLanguage(languageCode, merged);
        bedrockPack.addExtraFile(languageFileBytes(merged), "texts/" + languageCode + ".lang");
    }

    private static byte[] languageFileBytes(@NotNull Map<String, String> translations) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : translations.entrySet()) {
            builder
                .append(sanitizeLanguageLine(entry.getKey()))
                .append('=')
                .append(sanitizeLanguageLine(entry.getValue()))
                .append('\n');
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    @NotNull
    private static String sanitizeLanguageLine(@NotNull String value) {
        return value.replace('\r', ' ').replace('\n', ' ');
    }

    @NotNull
    private static String bedrockLanguageCode(@NotNull String javaLanguagePath) {
        String code = javaLanguagePath;
        if (code.endsWith(".json")) {
            code = code.substring(0, code.length() - ".json".length());
        }

        int separator = code.indexOf('_');
        if (separator <= 0 || separator == code.length() - 1) {
            return code;
        }

        String language = code.substring(0, separator).toLowerCase(Locale.ROOT);
        String region = code.substring(separator + 1).toUpperCase(Locale.ROOT);
        return language + '_' + region;
    }
}
