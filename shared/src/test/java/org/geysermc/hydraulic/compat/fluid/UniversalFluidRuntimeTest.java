package org.geysermc.hydraulic.compat.fluid;

import org.geysermc.hydraulic.compat.runtime.TransferBridgeFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UniversalFluidRuntimeTest {

    @Test
    @DisplayName("Universal Fluid Tank fill and drain operations enforce volume and whitelist")
    void tankFillAndDrainEnforceConstraints() {
        UniversalFluidRuntime.FluidTankManager manager = new UniversalFluidRuntime.FluidTankManager();
        manager.addTank(4000, true, true, List.of("minecraft:water", "minecraft:lava"));

        assertEquals(1, manager.tankCount());
        UniversalFluidRuntime.UniversalFluidTank tank = manager.getTank(0);
        assertNotNull(tank);
        assertTrue(tank.isEmpty());
        assertEquals(4000, tank.remainingCapacity());

        // Fill 1000 water
        UniversalFluidRuntime.FluidTransferTransactionResult fillRes = manager.fill(
            0,
            new TransferBridgeFactory.FluidStackView("minecraft:water", 1000),
            false
        );
        assertTrue(fillRes.success());
        assertEquals(1000, fillRes.transferredAmount());

        // Drain 500 water
        UniversalFluidRuntime.FluidTransferTransactionResult drainRes = manager.drain(0, 500, false);
        assertTrue(drainRes.success());
        assertEquals(500, drainRes.transferredAmount());
        assertEquals("minecraft:water", drainRes.fluidId());

        UniversalFluidRuntime.UniversalFluidTank updated = manager.getTank(0);
        assertNotNull(updated);
        assertEquals(500, updated.fluid().amount());
        assertEquals(3500, updated.remainingCapacity());
    }

    @Test
    @DisplayName("6-Sided Multi-Tank fill and drain operations respect side constraints")
    void sixSidedMultiTankRespectsDirections() {
        UniversalFluidRuntime.FluidTankManager manager = new UniversalFluidRuntime.FluidTankManager();
        manager.addTank(
            4000,
            true,
            true,
            List.of("minecraft:water"),
            java.util.Set.of(UniversalFluidRuntime.FluidSide.UP, UniversalFluidRuntime.FluidSide.NORTH)
        );

        // Try filling from SOUTH (disallowed)
        UniversalFluidRuntime.FluidTransferTransactionResult deniedFill = manager.fill(
            0,
            new TransferBridgeFactory.FluidStackView("minecraft:water", 1000),
            UniversalFluidRuntime.FluidSide.SOUTH,
            false
        );
        assertFalse(deniedFill.success());
        assertEquals(0, deniedFill.transferredAmount());

        // Try filling from UP (allowed)
        UniversalFluidRuntime.FluidTransferTransactionResult allowedFill = manager.fill(
            0,
            new TransferBridgeFactory.FluidStackView("minecraft:water", 1000),
            UniversalFluidRuntime.FluidSide.UP,
            false
        );
        assertTrue(allowedFill.success());
        assertEquals(1000, allowedFill.transferredAmount());

        // Drain simulation from DOWN (disallowed)
        UniversalFluidRuntime.FluidTransferTransactionResult deniedDrain = manager.drain(
            0,
            500,
            UniversalFluidRuntime.FluidSide.DOWN,
            true
        );
        assertFalse(deniedDrain.success());

        // Drain commit from NORTH (allowed)
        UniversalFluidRuntime.FluidTransferTransactionResult allowedDrain = manager.drain(
            0,
            500,
            UniversalFluidRuntime.FluidSide.NORTH,
            false
        );
        assertTrue(allowedDrain.success());
        assertEquals(500, allowedDrain.transferredAmount());
        assertEquals(500, manager.getTank(0).fluid().amount());
    }

    @Test
    @DisplayName("World Fluid Approximator returns valid Bedrock visuals")
    void worldFluidApproximatorReturnsVisuals() {
        UniversalFluidRuntime.WorldFluidApproximator.BedrockFluidVisual customVisual =
            new UniversalFluidRuntime.WorldFluidApproximator.BedrockFluidVisual(
                "minecraft:water",
                "textures/blocks/oil_still",
                "textures/blocks/oil_flow",
                0x111111,
                0
            );

        UniversalFluidRuntime.WorldFluidApproximator.registerVisual("tech:oil", customVisual);

        UniversalFluidRuntime.WorldFluidApproximator.BedrockFluidVisual result =
            UniversalFluidRuntime.WorldFluidApproximator.approximate("tech:oil");

        assertEquals("textures/blocks/oil_still", result.sourceTexture());
        assertEquals(0x111111, result.tintColor());
    }
}
