package org.geysermc.hydraulic.compat.entity;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UniversalEntityRuntimeTest {

    @Test
    @DisplayName("Entity State IR encapsulates spatial and health state")
    void entityStateIREncapsulatesState() {
        UniversalEntityRuntime.EntityStateIR state = new UniversalEntityRuntime.EntityStateIR(
            Identifier.parse("test:golem"),
            100.5,
            64.0,
            -200.5,
            90.0f,
            0.0f,
            50.0f,
            100.0f,
            false,
            null,
            Map.of("tier", "iron")
        );

        assertTrue(state.isAlive());
        assertEquals(50.0f, state.health());
        assertEquals("iron", state.customAttributes().get("tier"));
    }

    @Test
    @DisplayName("Entity Interaction Mapper dispatches Bedrock interaction to Java")
    void entityInteractionMapperDispatchesInteraction() {
        UniversalEntityRuntime.EntityInteractionMapper mapper = new UniversalEntityRuntime.EntityInteractionMapper();
        Identifier entityId = Identifier.parse("test:npc");
        mapper.registerPrompt(entityId, "Talk to Merchant");

        assertEquals("Talk to Merchant", mapper.getPrompt(entityId));

        UniversalEntityRuntime.EntityInteractionAction action = new UniversalEntityRuntime.EntityInteractionAction(
            entityId,
            "RIGHT_CLICK",
            "minecraft:emerald",
            false
        );

        UniversalEntityRuntime.EntityInteractionResult result = mapper.handleInteraction(action);
        assertTrue(result.success());
        assertEquals("EXECUTE_JAVA_INTERACTION", result.resultAction());
        assertEquals("Talk to Merchant", result.updatedStatePrompt());
    }
}
