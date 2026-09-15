package org.geysermc.hydraulic.compat.runtime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical Runtime State snapshot.
 *
 * Implements P0.7 State-Equivalence Testing (S_after == S_pre-restart) and P0.2 Block-Entity
 * lifecycle state preservation verification.
 */
public record CanonicalRuntimeState(
    @NotNull String blockEntityId,
    @NotNull String positionKey,
    @NotNull String dimensionKey,
    @NotNull List<ItemSlotState> inventory,
    @NotNull List<FluidTankState> fluids,
    int energyStored,
    int energyCapacity,
    @Nullable String activeRecipeId,
    int progressTicks,
    @NotNull Map<String, String> customProperties
) {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public CanonicalRuntimeState {
        Objects.requireNonNull(blockEntityId, "blockEntityId");
        Objects.requireNonNull(positionKey, "positionKey");
        Objects.requireNonNull(dimensionKey, "dimensionKey");
        inventory = List.copyOf(inventory);
        fluids = List.copyOf(fluids);
        customProperties = Collections.unmodifiableMap(new LinkedHashMap<>(customProperties));
    }

    public record ItemSlotState(
        int slot,
        @NotNull String itemId,
        int count,
        @NotNull Map<String, String> components
    ) {
        public ItemSlotState {
            Objects.requireNonNull(itemId, "itemId");
            components = Collections.unmodifiableMap(new LinkedHashMap<>(components));
        }
    }

    public record FluidTankState(
        int tank,
        @NotNull String fluidId,
        int amount,
        int capacity
    ) {
        public FluidTankState {
            Objects.requireNonNull(fluidId, "fluidId");
        }
    }

    /**
     * Verifies exact state equivalence between this snapshot and another snapshot.
     * Guaranteed invariant: S_after == S_pre-restart across volatile fields.
     */
    public boolean isEquivalentTo(@NotNull CanonicalRuntimeState other) {
        if (!this.blockEntityId.equals(other.blockEntityId)) return false;
        if (!this.positionKey.equals(other.positionKey)) return false;
        if (!this.dimensionKey.equals(other.dimensionKey)) return false;
        if (this.energyStored != other.energyStored) return false;
        if (this.energyCapacity != other.energyCapacity) return false;
        if (!Objects.equals(this.activeRecipeId, other.activeRecipeId)) return false;
        if (this.progressTicks != other.progressTicks) return false;
        if (!this.inventory.equals(other.inventory)) return false;
        if (!this.fluids.equals(other.fluids)) return false;
        return this.customProperties.equals(other.customProperties);
    }

    @NotNull
    public List<String> diff(@NotNull CanonicalRuntimeState other) {
        List<String> differences = new ArrayList<>();
        if (!this.blockEntityId.equals(other.blockEntityId)) {
            differences.add("blockEntityId: " + this.blockEntityId + " != " + other.blockEntityId);
        }
        if (!this.positionKey.equals(other.positionKey)) {
            differences.add("positionKey: " + this.positionKey + " != " + other.positionKey);
        }
        if (!this.dimensionKey.equals(other.dimensionKey)) {
            differences.add("dimensionKey: " + this.dimensionKey + " != " + other.dimensionKey);
        }
        if (this.energyStored != other.energyStored) {
            differences.add("energyStored: " + this.energyStored + " != " + other.energyStored);
        }
        if (this.energyCapacity != other.energyCapacity) {
            differences.add("energyCapacity: " + this.energyCapacity + " != " + other.energyCapacity);
        }
        if (!Objects.equals(this.activeRecipeId, other.activeRecipeId)) {
            differences.add("activeRecipeId: " + this.activeRecipeId + " != " + other.activeRecipeId);
        }
        if (this.progressTicks != other.progressTicks) {
            differences.add("progressTicks: " + this.progressTicks + " != " + other.progressTicks);
        }
        if (!this.inventory.equals(other.inventory)) {
            differences.add("inventory: " + this.inventory + " != " + other.inventory);
        }
        if (!this.fluids.equals(other.fluids)) {
            differences.add("fluids: " + this.fluids + " != " + other.fluids);
        }
        if (!this.customProperties.equals(other.customProperties)) {
            differences.add("customProperties: " + this.customProperties + " != " + other.customProperties);
        }
        return differences;
    }

    @NotNull
    public CompoundTag toCompoundTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("BlockEntityId", this.blockEntityId);
        tag.putString("PositionKey", this.positionKey);
        tag.putString("DimensionKey", this.dimensionKey);
        tag.putInt("EnergyStored", this.energyStored);
        tag.putInt("EnergyCapacity", this.energyCapacity);
        if (this.activeRecipeId != null) {
            tag.putString("ActiveRecipeId", this.activeRecipeId);
        }
        tag.putInt("ProgressTicks", this.progressTicks);

        ListTag invList = new ListTag();
        for (ItemSlotState slot : this.inventory) {
            CompoundTag slotTag = new CompoundTag();
            slotTag.putInt("Slot", slot.slot());
            slotTag.putString("ItemId", slot.itemId());
            slotTag.putInt("Count", slot.count());
            invList.add(slotTag);
        }
        tag.put("Inventory", invList);

        ListTag fluidList = new ListTag();
        for (FluidTankState tank : this.fluids) {
            CompoundTag tankTag = new CompoundTag();
            tankTag.putInt("Tank", tank.tank());
            tankTag.putString("FluidId", tank.fluidId());
            tankTag.putInt("Amount", tank.amount());
            tankTag.putInt("Capacity", tank.capacity());
            fluidList.add(tankTag);
        }
        tag.put("Fluids", fluidList);

        CompoundTag propsTag = new CompoundTag();
        for (Map.Entry<String, String> prop : this.customProperties.entrySet()) {
            propsTag.putString(prop.getKey(), prop.getValue());
        }
        tag.put("CustomProperties", propsTag);

        return tag;
    }

    @NotNull
    public static CanonicalRuntimeState fromCompoundTag(@NotNull CompoundTag tag) {
        String id = tag.getStringOr("BlockEntityId", "minecraft:unknown");
        String pos = tag.getStringOr("PositionKey", "0,0,0");
        String dim = tag.getStringOr("DimensionKey", "minecraft:overworld");
        int energy = tag.getIntOr("EnergyStored", 0);
        int energyCap = tag.getIntOr("EnergyCapacity", 0);
        String recipe = tag.getStringOr("ActiveRecipeId", "");
        if (recipe.isEmpty()) {
            recipe = null;
        }
        int progress = tag.getIntOr("ProgressTicks", 0);

        List<ItemSlotState> inv = new ArrayList<>();
        if (tag.contains("Inventory")) {
            ListTag list = tag.getListOrEmpty("Inventory");
            for (int i = 0; i < list.size(); i++) {
                CompoundTag itemTag = list.getCompoundOrEmpty(i);
                inv.add(new ItemSlotState(
                    itemTag.getIntOr("Slot", i),
                    itemTag.getStringOr("ItemId", "minecraft:air"),
                    itemTag.getIntOr("Count", 0),
                    Map.of()
                ));
            }
        }

        List<FluidTankState> fluids = new ArrayList<>();
        if (tag.contains("Fluids")) {
            ListTag list = tag.getListOrEmpty("Fluids");
            for (int i = 0; i < list.size(); i++) {
                CompoundTag tankTag = list.getCompoundOrEmpty(i);
                fluids.add(new FluidTankState(
                    tankTag.getIntOr("Tank", i),
                    tankTag.getStringOr("FluidId", "minecraft:empty"),
                    tankTag.getIntOr("Amount", 0),
                    tankTag.getIntOr("Capacity", 0)
                ));
            }
        }

        Map<String, String> props = new LinkedHashMap<>();
        if (tag.contains("CustomProperties")) {
            CompoundTag propsTag = tag.getCompoundOrEmpty("CustomProperties");
            for (String key : propsTag.keySet()) {
                props.put(key, propsTag.getStringOr(key, ""));
            }
        }

        return new CanonicalRuntimeState(id, pos, dim, inv, fluids, energy, energyCap, recipe, progress, props);
    }

    @NotNull
    public String toJson() {
        return GSON.toJson(this);
    }

    @NotNull
    public static CanonicalRuntimeState fromJson(@NotNull String json) {
        return GSON.fromJson(json, CanonicalRuntimeState.class);
    }
}
