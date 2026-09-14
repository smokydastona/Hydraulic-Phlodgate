package org.geysermc.hydraulic.fabric.test.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.geysermc.hydraulic.fabric.test.ModBlockEntities;
import org.jetbrains.annotations.NotNull;

public final class MenuMachineBlockEntity extends BlockEntity implements Container {
    private static final int SLOT_COUNT = 2;
    private static final int MAX_PROGRESS = 200;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private int progress;
    private boolean enabled = true;

    public MenuMachineBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        super(ModBlockEntities.MENU_MACHINE, pos, state);
    }

    public static void tick(@NotNull MenuMachineBlockEntity machine) {
        if (!machine.enabled) {
            return;
        }
        machine.progress = (machine.progress + 1) % (MAX_PROGRESS + 1);
        machine.setChanged();
    }

    public int progress() {
        return this.progress;
    }

    public boolean enabled() {
        return this.enabled;
    }

    public void toggleEnabled() {
        this.enabled = !this.enabled;
        this.setChanged();
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        return this.items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = this.items.get(slot).split(amount);
        if (!removed.isEmpty()) {
            this.setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack removed = this.items.set(slot, ItemStack.EMPTY);
        return removed;
    }

    @Override
    public void setItem(int slot, @NotNull ItemStack stack) {
        this.items.set(slot, stack.copyWithCount(Math.min(stack.getCount(), this.getMaxStackSize(stack))));
        this.setChanged();
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return !this.isRemoved() && player.distanceToSqr(
            this.worldPosition.getX() + 0.5D,
            this.worldPosition.getY() + 0.5D,
            this.worldPosition.getZ() + 0.5D
        ) <= 64.0D;
    }

    @Override
    public void clearContent() {
        this.items.clear();
        this.setChanged();
    }

    @Override
    protected void saveAdditional(@NotNull ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("progress", this.progress);
        output.putBoolean("enabled", this.enabled);
        for (int slot = 0; slot < this.items.size(); slot++) {
            output.storeNullable("slot_" + slot, ItemStack.CODEC, this.items.get(slot));
        }
    }

    @Override
    protected void loadAdditional(@NotNull ValueInput input) {
        super.loadAdditional(input);
        this.progress = Math.clamp(input.getIntOr("progress", 0), 0, MAX_PROGRESS);
        this.enabled = input.getBooleanOr("enabled", true);
        for (int slot = 0; slot < this.items.size(); slot++) {
            this.items.set(slot, input.read("slot_" + slot, ItemStack.CODEC).orElse(ItemStack.EMPTY));
        }
    }
}