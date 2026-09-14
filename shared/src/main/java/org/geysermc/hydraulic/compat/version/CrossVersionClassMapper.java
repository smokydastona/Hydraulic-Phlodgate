package org.geysermc.hydraulic.compat.version;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Maps legacy class and member paths across versions.
 * Adapted and synthesized clean-room from Retromod / Sinytra Connector / ViaBackwards migration principles.
 */
public final class CrossVersionClassMapper {
    private static final Map<String, String> CLASS_MOVES;
    private static final Map<String, String> FLATTENING_BLOCK_MAP;

    static {
        Map<String, String> moves = new HashMap<>();
        // Pre-1.17 repackaging / 1.12.2 to Modern mappings
        moves.put("net.minecraft.tileentity.TileEntity", "net.minecraft.world.level.block.entity.BlockEntity");
        moves.put("net.minecraft.block.Block", "net.minecraft.world.level.block.Block");
        moves.put("net.minecraft.item.Item", "net.minecraft.world.item.Item");
        moves.put("net.minecraft.item.ItemStack", "net.minecraft.world.item.ItemStack");
        moves.put("net.minecraft.entity.Entity", "net.minecraft.world.entity.Entity");
        moves.put("net.minecraft.entity.player.EntityPlayer", "net.minecraft.world.entity.player.Player");
        moves.put("net.minecraft.entity.player.EntityPlayerMP", "net.minecraft.server.level.ServerPlayer");
        moves.put("net.minecraft.inventory.IInventory", "net.minecraft.world.Container");
        moves.put("net.minecraft.inventory.Container", "net.minecraft.world.inventory.AbstractContainerMenu");
        moves.put("net.minecraft.util.ResourceLocation", "net.minecraft.resources.ResourceLocation");

        // 1.20.x to 26.x / Modern mappings
        moves.put("net.minecraft.world.Container", "net.minecraft.world.Container");
        moves.put("net.minecraft.world.inventory.AbstractContainerMenu", "net.minecraft.world.inventory.AbstractContainerMenu");
        moves.put("net.minecraft.world.level.block.entity.BlockEntity", "net.minecraft.world.level.block.entity.BlockEntity");

        CLASS_MOVES = Collections.unmodifiableMap(moves);

        Map<String, String> flattening = new HashMap<>();
        flattening.put("minecraft:wool", "minecraft:white_wool");
        flattening.put("minecraft:log", "minecraft:oak_log");
        flattening.put("minecraft:log2", "minecraft:acacia_log");
        flattening.put("minecraft:planks", "minecraft:oak_planks");
        flattening.put("minecraft:stained_hardened_clay", "minecraft:white_terracotta");
        flattening.put("minecraft:concrete", "minecraft:white_concrete");
        FLATTENING_BLOCK_MAP = Collections.unmodifiableMap(flattening);
    }

    @NotNull
    public static String remapClassName(@NotNull String className) {
        Objects.requireNonNull(className, "className");
        return CLASS_MOVES.getOrDefault(className, className);
    }

    @NotNull
    public static String remapLegacyBlockIdentifier(@NotNull String legacyId) {
        Objects.requireNonNull(legacyId, "legacyId");
        return FLATTENING_BLOCK_MAP.getOrDefault(legacyId, legacyId);
    }

    public static boolean isMovedClass(@Nullable String className) {
        return className != null && CLASS_MOVES.containsKey(className);
    }

    public static int registeredClassMovesCount() {
        return CLASS_MOVES.size();
    }
}
