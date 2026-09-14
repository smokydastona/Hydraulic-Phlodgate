package org.geysermc.hydraulic.compat.model;

import org.geysermc.hydraulic.compat.CompatibilityStatus;
import org.geysermc.hydraulic.compat.capability.CapabilityProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImplementationMaturityTest {
    @Test
    void legacyConstructorDefaultsToUnknown() {
        CompatibilityObject object = new CompatibilityObject(
            "example:block", "block", "example", Map.of(),
            new CapabilityProfile("example:block", List.of(), List.of()), List.of(), List.of(), Map.of(),
            SupportLevel.VISUAL_ONLY, CompatibilityStatus.PARTIAL, 0,
            new Confidence(0.5D, "unknown"), List.of(), List.of()
        );

        assertEquals(ImplementationMaturity.UNKNOWN, object.implementationMaturity());
    }

    @Test
    void maturityUpdatesWithoutChangingCompatibilityFields() {
        CompatibilityObject object = new CompatibilityObject(
            "example:block", "block", "example", Map.of(),
            new CapabilityProfile("example:block", List.of(), List.of()), List.of(), List.of(), Map.of(),
            SupportLevel.AUTOMATIC, CompatibilityStatus.COMPLETE, 100,
            new Confidence(1D, "high"), List.of(), List.of()
        );

        CompatibilityObject verified = object.withImplementationMaturity(ImplementationMaturity.VERIFIED);

        assertEquals(ImplementationMaturity.VERIFIED, verified.implementationMaturity());
        assertEquals(object.overallLevel(), verified.overallLevel());
        assertEquals(object.overallScore(), verified.overallScore());
    }
}