package org.geysermc.hydraulic.fabric.test.machine;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.geysermc.hydraulic.fabric.test.ModMenus;
import org.jetbrains.annotations.NotNull;

public final class MenuMachineMenu extends AbstractContainerMenu {
    private final Container machine;
    private final DataSlot progress;

    public MenuMachineMenu(int containerId, @NotNull Inventory inventory, String ignoredPayload) {
        this(containerId, inventory, new SimpleContainer(2), DataSlot.standalone());
    }

    public MenuMachineMenu(int containerId, @NotNull Inventory inventory, @NotNull MenuMachineBlockEntity machine) {
        this(containerId, inventory, machine, new DataSlot() {
            @Override
            public int get() {
                return machine.progress();
            }

            @Override
            public void set(int value) {
            }
        });
    }

    private MenuMachineMenu(int containerId, @NotNull Inventory inventory, @NotNull Container machine, @NotNull DataSlot progress) {
        super(ModMenus.MENU_MACHINE, containerId);
        this.machine = machine;
        this.progress = this.addDataSlot(progress);
        this.addSlot(new Slot(machine, 0, 56, 35));
        this.addSlot(new Slot(machine, 1, 116, 35) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                return false;
            }
        });
        addPlayerInventory(inventory);
    }

    public int progress() {
        return this.progress.get();
    }

    @Override
    public boolean clickMenuButton(@NotNull Player player, int buttonId) {
        if (buttonId != 0 || !(this.machine instanceof MenuMachineBlockEntity machine)) {
            return false;
        }
        machine.toggleEnabled();
        this.broadcastFullState();
        return true;
    }

    @Override
    public ItemStack quickMoveStack(@NotNull Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack source = slot.getItem();
        ItemStack copy = source.copy();
        if (slotIndex < 2) {
            if (!this.moveItemStackTo(source, 2, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(source, 0, 1, false)) {
            return ItemStack.EMPTY;
        }
        if (source.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return this.machine.stillValid(player);
    }

    private void addPlayerInventory(@NotNull Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        }
    }
}