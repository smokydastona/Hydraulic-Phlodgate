package org.geysermc.hydraulic.compat.validation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ClientObservationAttestationTest {

    @Test
    public void testAttestationPayloadStructure() {
        JsonObject report = new JsonObject();
        report.addProperty("schemaVersion", "1.0.0");
        report.addProperty("totalAttestations", 20);

        JsonArray platforms = new JsonArray();
        platforms.add("Windows 11");
        platforms.add("iOS");
        platforms.add("Android");
        platforms.add("Nintendo Switch");
        report.add("platformsCovered", platforms);

        JsonArray attestations = new JsonArray();
        JsonObject record = new JsonObject();
        record.addProperty("attestationId", "test-guid");
        record.addProperty("clientPlatform", "Windows 11");
        record.addProperty("clientVersion", "1.21.60");
        record.addProperty("targetObject", "hydraulic_test_mod:processing_machine");
        record.addProperty("verdict", "PASSED");
        record.addProperty("attestedBy", "Release-QA-Lead");
        record.addProperty("notes", "Verified block placement, held-item insertion, and slot sync.");
        attestations.add(record);
        report.add("attestations", attestations);

        assertEquals("1.0.0", report.get("schemaVersion").getAsString());
        assertEquals(4, report.getAsJsonArray("platformsCovered").size());
        assertEquals("PASSED", report.getAsJsonArray("attestations").get(0).getAsJsonObject().get("verdict").getAsString());
    }
}
