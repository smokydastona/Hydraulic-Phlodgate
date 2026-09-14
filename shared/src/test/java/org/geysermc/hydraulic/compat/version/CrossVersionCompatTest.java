package org.geysermc.hydraulic.compat.version;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CrossVersionCompatTest {

    @Test
    public void testVersionEraParsing() {
        MinecraftVersionEra era112 = MinecraftVersionEra.parse("1.12.2");
        assertEquals(1, era112.major());
        assertEquals(12, era112.minor());
        assertEquals(2, era112.patch());
        assertTrue(era112.isLegacy());
        assertFalse(era112.usesComponents());

        MinecraftVersionEra era1201 = MinecraftVersionEra.parse("1.20.1");
        assertEquals(MinecraftVersionEra.EraType.MODERN_PRE_COMPONENTS, era1201.eraType());
        assertFalse(era1201.usesComponents());

        MinecraftVersionEra era1214 = MinecraftVersionEra.parse("1.21.4");
        assertEquals(MinecraftVersionEra.EraType.MODERN_COMPONENTS, era1214.eraType());
        assertTrue(era1214.usesComponents());

        MinecraftVersionEra era262 = MinecraftVersionEra.parse("26.2");
        assertEquals(26, era262.major());
        assertEquals(2, era262.minor());
        assertTrue(era262.usesComponents());
    }

    @Test
    public void testClassMapping() {
        assertEquals("net.minecraft.world.level.block.entity.BlockEntity",
                CrossVersionClassMapper.remapClassName("net.minecraft.tileentity.TileEntity"));
        assertEquals("net.minecraft.world.Container",
                CrossVersionClassMapper.remapClassName("net.minecraft.inventory.IInventory"));
        assertEquals("minecraft:white_wool",
                CrossVersionClassMapper.remapLegacyBlockIdentifier("minecraft:wool"));
        assertTrue(CrossVersionClassMapper.isMovedClass("net.minecraft.tileentity.TileEntity"));
        assertFalse(CrossVersionClassMapper.isMovedClass("java.lang.String"));
    }
}
