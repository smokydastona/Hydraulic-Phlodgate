package org.geysermc.hydraulic.compat.runtime;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EnergyBlockUseActionPlanTest {
    @Test
    void compilesBoundedReceiveContract() {
        EnergyBlockUseActionPlan plan = EnergyBlockUseActionPlan.from(Map.of(
            "interaction.energy.action", "receive",
            "interaction.energy.amount", "250",
            "interaction.energy.side", "up",
            "interaction.energy.property", "4"
        ));

        assertEquals(EnergyBlockUseActionPlan.Action.RECEIVE, plan.action());
        assertEquals(250, plan.amount());
        assertEquals("up", plan.side());
        assertEquals(4, plan.propertyId());
    }

    @Test
    void rejectsMalformedOrUnboundedContract() {
        assertNull(EnergyBlockUseActionPlan.from(Map.of(
            "interaction.energy.action", "receive",
            "interaction.energy.amount", "0"
        )));
        assertNull(EnergyBlockUseActionPlan.from(Map.of(
            "interaction.energy.action", "extract",
            "interaction.energy.amount", "10000001"
        )));
        assertNull(EnergyBlockUseActionPlan.from(Map.of(
            "interaction.energy.action", "receive",
            "interaction.energy.amount", "20",
            "interaction.energy.property", "-1"
        )));
    }
}