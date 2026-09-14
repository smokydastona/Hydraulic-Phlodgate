package org.geysermc.hydraulic.mixin.ext;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftResourcePackReaderImplMixinTest {
    @Test
    void flagsUnsupportedCustomItemModelSchemas() throws Exception {
        assertTrue(invokeIsUnsupportedItemModelSchema(
            new IllegalArgumentException("Unknown item model type: citadel:custom_item_model")
        ));
        assertTrue(invokeIsUnsupportedItemModelSchema(
            new IllegalArgumentException("Unknown select property type: travelersbackpack:hose_modes")
        ));
        assertTrue(invokeIsUnsupportedItemModelSchema(
            new IllegalArgumentException("Unknown condition property: farmersdelight:skillet/is_cooking")
        ));
        assertTrue(invokeIsUnsupportedItemModelSchema(
            new IllegalArgumentException("Unknown special render type: lootr:chest")
        ));
    }

    @Test
    void ignoresOtherDeserializationFailures() throws Exception {
        assertFalse(invokeIsUnsupportedItemModelSchema(
            new IllegalArgumentException("Some other schema problem")
        ));
        assertFalse(invokeIsUnsupportedItemModelSchema(
            new RuntimeException("Unknown item model type: citadel:custom_item_model")
        ));
    }

    @Test
    void sanitizesMalformedPackMetadataMinFormat() throws Exception {
        var json = JsonParser.parseString("""
            {
              "pack": {
                "description": "furniture resources",
                "max_format": 107,
                "min_format": [107, 1]
              }
            }
            """);

        var sanitized = invokeSanitizePackMetadata(json);
        assertTrue(sanitized.isJsonObject());
        assertEquals(107, sanitized.getAsJsonObject().getAsJsonObject("pack").get("min_format").getAsInt());
    }

    private static boolean invokeIsUnsupportedItemModelSchema(Exception exception) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method method = MinecraftResourcePackReaderImplMixin.class.getDeclaredMethod("isUnsupportedItemModelSchema", Exception.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, exception);
    }

    private static com.google.gson.JsonElement invokeSanitizePackMetadata(com.google.gson.JsonElement json)
        throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method method = MinecraftResourcePackReaderImplMixin.class.getDeclaredMethod("sanitizePackMetadata", com.google.gson.JsonElement.class);
        method.setAccessible(true);
        return (com.google.gson.JsonElement) method.invoke(null, json);
    }
}