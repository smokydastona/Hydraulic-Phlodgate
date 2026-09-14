package org.geysermc.hydraulic.compat.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BedrockSchemaValidatorTest {

    @Test
    public void testValidateManifest() {
        JsonObject manifest = new JsonObject();
        manifest.addProperty("format_version", 2);

        JsonObject header = new JsonObject();
        header.addProperty("uuid", "12345678-1234-1234-1234-123456789abc");
        header.addProperty("name", "Test Pack");
        JsonArray version = new JsonArray();
        version.add(1);
        version.add(0);
        version.add(0);
        header.add("version", version);
        manifest.add("header", header);

        JsonArray modules = new JsonArray();
        JsonObject mod = new JsonObject();
        mod.addProperty("type", "resources");
        mod.addProperty("uuid", "87654321-4321-4321-4321-cba987654321");
        mod.add("version", version);
        modules.add(mod);
        manifest.add("modules", modules);

        BedrockSchemaValidator.ValidationResult result = BedrockSchemaValidator.validateManifest(manifest);
        assertTrue(result.isValid(), result.getMessage());
    }

    @Test
    public void testNormalizeGeometryAndAttachable() {
        JsonObject geom = new JsonObject();
        JsonObject normGeom = BedrockSchemaValidator.normalizeGeometrySchema(geom);
        assertTrue(normGeom.has("format_version"));
        assertEquals("1.21.0", normGeom.get("format_version").getAsString());

        JsonObject attachable = new JsonObject();
        JsonObject normAttachable = BedrockSchemaValidator.normalizeAttachableSchema(attachable);
        assertTrue(normAttachable.has("format_version"));
        assertEquals("1.10.0", normAttachable.get("format_version").getAsString());
    }
}
