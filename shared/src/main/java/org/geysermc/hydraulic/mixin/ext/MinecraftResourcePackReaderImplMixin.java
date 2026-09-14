package org.geysermc.hydraulic.mixin.ext;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import net.kyori.adventure.key.Key;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import team.unnamed.creative.metadata.pack.PackFormat;
import team.unnamed.creative.overlay.ResourceContainer;
import team.unnamed.creative.part.ResourcePackPart;
import team.unnamed.creative.serialize.minecraft.GsonUtil;
import team.unnamed.creative.serialize.minecraft.io.JsonResourceDeserializer;

import java.io.IOException;

@Mixin(targets = "team.unnamed.creative.serialize.minecraft.MinecraftResourcePackReaderImpl", remap = false)
public abstract class MinecraftResourcePackReaderImplMixin {
    private static Logger LOGGER = LoggerFactory.getLogger("MinecraftResourcePackReaderImplMixin");
    private static final String UNKNOWN_ITEM_MODEL_TYPE_PREFIX = "Unknown item model type:";

    /**
     * Redirect the parseJson method to catch any exceptions that may occur
     * This means a single bad json file won't cause the entire resource pack to fail loading
     */
    @Redirect(
        method = "parseJson",
        at = @At(
            value = "INVOKE",
            target = "Lteam/unnamed/creative/serialize/minecraft/GsonUtil;parseReader(Lcom/google/gson/stream/JsonReader;)Lcom/google/gson/JsonElement;"
        )
    )
    private JsonElement parseJson(JsonReader reader) {
        try {
            return GsonUtil.parseReader(reader);
        } catch (Exception e) {
            LOGGER.error("Failed to parse JSON: " + e.getMessage());
        }

        return null;
    }

    /**
     * Redirect the deserializeFromJson to ignore any null JsonElements
     * Also catch any exceptions that may occur and log them
     */
    @Redirect(
        method = "read(Lteam/unnamed/creative/serialize/minecraft/fs/FileTreeReader;)Lteam/unnamed/creative/ResourcePack;",
        at = @At(
            value = "INVOKE",
            target = "Lteam/unnamed/creative/serialize/minecraft/io/JsonResourceDeserializer;deserializeFromJson(Lcom/google/gson/JsonElement;Lnet/kyori/adventure/key/Key;Lteam/unnamed/creative/metadata/pack/PackFormat;)Ljava/lang/Object;"
        )
    )
    private Object deserializeFromJson(JsonResourceDeserializer<?> instance, JsonElement jsonElement, Key key, PackFormat packFormat) throws IOException {
        if (jsonElement == null) {
            return null;
        }

        try {
            jsonElement = sanitizePackMetadata(jsonElement);
            return instance.deserializeFromJson(jsonElement, key, packFormat);
        } catch (Exception e) {
            if (isUnsupportedItemModelSchema(e)) {
                LOGGER.debug("Skipping unsupported item model schema for {}: {}", key, e.getMessage());
            } else {
                LOGGER.error("Failed to deserialize JSON (" + key + "): " + e.getMessage());
            }
        }

        return null;
    }

    private static JsonElement sanitizePackMetadata(JsonElement jsonElement) {
        if (jsonElement == null || !jsonElement.isJsonObject()) {
            return jsonElement;
        }

        JsonObject root = jsonElement.getAsJsonObject();
        if (!root.has("pack") || !root.get("pack").isJsonObject()) {
            return jsonElement;
        }

        JsonObject pack = root.getAsJsonObject("pack");
        if (!pack.has("min_format")) {
            return jsonElement;
        }

        JsonElement minFormat = pack.get("min_format");
        if (minFormat == null || !minFormat.isJsonArray()) {
            return jsonElement;
        }

        JsonElement first = minFormat.getAsJsonArray().size() > 0 ? minFormat.getAsJsonArray().get(0) : null;
        if (first == null || !first.isJsonPrimitive() || !((JsonPrimitive) first).isNumber()) {
            return jsonElement;
        }

        int normalized = first.getAsInt();
        if (normalized <= 0) {
            return jsonElement;
        }

        pack.addProperty("min_format", normalized);
        return jsonElement;
    }

    private static boolean isUnsupportedItemModelSchema(Exception exception) {
        if (!(exception instanceof IllegalArgumentException) || exception.getMessage() == null) {
            return false;
        }
        String msg = exception.getMessage();
        return msg.startsWith(UNKNOWN_ITEM_MODEL_TYPE_PREFIX)
            || msg.startsWith("Unknown select property type:")
            || msg.startsWith("Unknown condition property:")
            || msg.startsWith("Unknown special render type:");
    }

    @Redirect(
            method = "read(Lteam/unnamed/creative/serialize/minecraft/fs/FileTreeReader;)Lteam/unnamed/creative/ResourcePack;",
            at = @At(
                    value = "INVOKE",
                    target = "Lteam/unnamed/creative/part/ResourcePackPart;addTo(Lteam/unnamed/creative/overlay/ResourceContainer;)V"
            )
    )
    private void addTo(ResourcePackPart instance, ResourceContainer resourceContainer) {
        if (instance != null) {
            instance.addTo(resourceContainer);
        }
    }

    //Key key = Key.key(namespace, keyValue);
    @ModifyArgs(
            method = "read(Lteam/unnamed/creative/serialize/minecraft/fs/FileTreeReader;)Lteam/unnamed/creative/ResourcePack;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/kyori/adventure/key/Key;key(Ljava/lang/String;Ljava/lang/String;)Lnet/kyori/adventure/key/Key;",
                    ordinal = 2
            )
    )
    private void injectKeyCreation(Args args) {
        args.set(1, ((String) args.get(1)).toLowerCase());
    }
}
