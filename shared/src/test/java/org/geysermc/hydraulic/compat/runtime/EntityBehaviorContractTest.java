package org.geysermc.hydraulic.compat.runtime;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EntityBehaviorContractTest {
    @Test
    void classifiesSupportedVocabularyWithoutAdvertisingExecution() {
        EntityBehaviorContract contract = EntityBehaviorContract.fromFacts(Map.of(
            "entity.ai.behavior", "follow",
            "entity.ai.target", "player",
            "entity.ai.range", "16"
        ));

        assertEquals(EntityBehaviorContract.Behavior.FOLLOW, contract.behavior());
        assertEquals("player", contract.target());
        assertEquals(16, contract.range());
        org.junit.jupiter.api.Assertions.assertFalse(contract.executable());
    }

    @Test
    void rejectsUnknownOrUnsafeBehaviorFacts() {
        assertNull(EntityBehaviorContract.fromFacts(Map.of("entity.ai.behavior", "teleport")));
        assertNull(EntityBehaviorContract.fromFacts(Map.of("entity.ai.behavior", "wander", "entity.ai.range", "129")));
    }

    @Test
    void validatesCustomNetworkEvidenceWithoutAdvertisingExecution() {
        EntityNetworkContract contract = EntityNetworkContract.fromFacts(Map.of(
            "entity.network.required", "true",
            "entity.network.direction", "serverbound",
            "entity.network.channel", "example:entity_action"
        ));

        assertEquals(EntityNetworkContract.Direction.SERVERBOUND, contract.direction());
        assertEquals("example:entity_action", contract.channel());
        org.junit.jupiter.api.Assertions.assertFalse(contract.executable());
    }
}