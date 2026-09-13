package org.geysermc.hydraulic.compat.menu;

import net.minecraft.resources.Identifier;
import org.geysermc.hydraulic.compat.runtime.SlotRole;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UniversalMenuIRTest {

    @Test
    @DisplayName("Menu IR Compiler compiles valid Furnace archetype layout")
    void compileFurnaceArchetypeLayout() {
        Identifier menuId = Identifier.parse("test:smelter");
        UniversalMenuIR.MenuLayoutIR layout = UniversalMenuIR.MenuIRCompiler.compileFurnaceArchetype(menuId);

        assertNotNull(layout);
        assertEquals(menuId, layout.menuIdentifier());
        assertEquals(ContainerType.FURNACE, layout.fallbackContainerType());
        assertEquals(3, layout.totalSlots());

        UniversalMenuIR.MenuSlotIR inputSlot = layout.getSlot(0);
        assertNotNull(inputSlot);
        assertEquals(SlotRole.INPUT, inputSlot.role());

        UniversalMenuIR.MenuSlotIR fuelSlot = layout.getSlot(1);
        assertNotNull(fuelSlot);
        assertEquals(SlotRole.FUEL, fuelSlot.role());

        UniversalMenuIR.MenuSlotIR outputSlot = layout.getSlot(2);
        assertNotNull(outputSlot);
        assertEquals(SlotRole.OUTPUT, outputSlot.role());

        assertNotNull(layout.getWidget("cook_progress"));
        assertEquals(UniversalMenuIR.WidgetType.PROGRESS_BAR, layout.getWidget("cook_progress").type());
    }

    @Test
    @DisplayName("Menu IR Compiler compiles generic storage chest layouts")
    void compileGenericStorageLayout() {
        Identifier menuId = Identifier.parse("test:chest");
        UniversalMenuIR.MenuLayoutIR layout = UniversalMenuIR.MenuIRCompiler.compileGenericStorage(menuId, 3);

        assertNotNull(layout);
        assertEquals(27, layout.totalSlots());
        assertEquals(ContainerType.GENERIC_9X3, layout.fallbackContainerType());
        assertEquals(SlotRole.STORAGE, layout.getSlot(0).role());
        assertEquals(SlotRole.STORAGE, layout.getSlot(26).role());
    }
}
