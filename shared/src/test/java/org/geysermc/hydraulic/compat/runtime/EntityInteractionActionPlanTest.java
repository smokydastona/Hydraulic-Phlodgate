package org.geysermc.hydraulic.compat.runtime;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EntityInteractionActionPlanTest {
    @Test
    void compilesBoundedAttackAction() {
        EntityInteractionActionPlan plan = EntityInteractionActionPlan.fromFacts(Map.of(
            "interaction.entity.action", "attack",
            "interaction.entity.hand", "main_hand"
        ));

        assertEquals(EntityInteractionActionPlan.Action.ATTACK, plan.action());
    }

    @Test
    void rejectsUnknownActionAndInvalidRange() {
        assertNull(EntityInteractionActionPlan.fromFacts(Map.of("interaction.entity.action", "teleport")));
    }

    @Test
    void defaultsHandAndPreservesRequiredItem() {
        EntityInteractionActionPlan plan = EntityInteractionActionPlan.fromFacts(Map.of(
            "interaction.entity.action", "use",
            "interaction.entity.item", "minecraft:emerald"
        ));

        assertEquals("main_hand", plan.hand());
        assertEquals("minecraft:emerald", plan.requiredItem());
    }
}