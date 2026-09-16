package org.geysermc.hydraulic.compat.menu;

import net.minecraft.resources.Identifier;
import org.geysermc.hydraulic.compat.runtime.SlotRole;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Universal Menu Intermediate Representation (Menu IR) and UI Compiler (Phase 6).
 * Compiles Java ScreenHandler / AbstractContainerMenu structures into normalized
 * Bedrock container archetypes, managing progress properties, energy gauges, and action buttons.
 */
public final class UniversalMenuIR {

    public enum WidgetType {
        PROGRESS_BAR,
        ENERGY_GAUGE,
        FLUID_GAUGE,
        TOGGLE_BUTTON,
        ACTION_BUTTON,
        TEXT_LABEL
    }

    public record MenuSlotIR(
        int slotIndex,
        int x,
        int y,
        @NotNull SlotRole role,
        boolean interactable,
        @NotNull List<String> itemWhitelist
    ) {
        public MenuSlotIR {
            itemWhitelist = List.copyOf(itemWhitelist);
        }
    }

    public record MenuWidgetIR(
        @NotNull String widgetId,
        @NotNull WidgetType type,
        int x,
        int y,
        int width,
        int height,
        @NotNull String propertyKey,
        int maxValue,
        @NotNull String label
    ) {}

    public record MenuLayoutIR(
        @NotNull Identifier menuIdentifier,
        @NotNull ContainerType fallbackContainerType,
        int totalSlots,
        @NotNull List<MenuSlotIR> slots,
        @NotNull List<MenuWidgetIR> widgets,
        @NotNull Map<String, Integer> syncedProperties
    ) {
        public MenuLayoutIR {
            slots = List.copyOf(slots);
            widgets = List.copyOf(widgets);
            syncedProperties = Collections.unmodifiableMap(new LinkedHashMap<>(syncedProperties));
        }

        @Nullable
        public MenuSlotIR getSlot(int index) {
            return slots.stream().filter(s -> s.slotIndex() == index).findFirst().orElse(null);
        }

        @Nullable
        public MenuWidgetIR getWidget(@NotNull String widgetId) {
            return widgets.stream().filter(w -> w.widgetId().equalsIgnoreCase(widgetId)).findFirst().orElse(null);
        }
    }

    public static final class MenuIRCompiler {
        private MenuIRCompiler() {
        }

        @NotNull
        public static MenuLayoutIR compileFurnaceArchetype(@NotNull Identifier menuId) {
            List<MenuSlotIR> slots = List.of(
                new MenuSlotIR(0, 56, 17, SlotRole.INPUT, true, List.of()),
                new MenuSlotIR(1, 56, 53, SlotRole.FUEL, true, List.of()),
                new MenuSlotIR(2, 116, 35, SlotRole.OUTPUT, true, List.of())
            );

            List<MenuWidgetIR> widgets = List.of(
                new MenuWidgetIR("cook_progress", WidgetType.PROGRESS_BAR, 79, 34, 24, 17, "container.property.0", 200, "Progress"),
                new MenuWidgetIR("fuel_gauge", WidgetType.PROGRESS_BAR, 56, 36, 14, 14, "container.property.1", 200, "Fuel")
            );

            return new MenuLayoutIR(
                menuId,
                ContainerType.FURNACE,
                3,
                slots,
                widgets,
                Map.of("container.property.0", 0, "container.property.1", 0)
            );
        }

        @NotNull
        public static MenuLayoutIR compileGenericStorage(@NotNull Identifier menuId, int rows) {
            int total = rows * 9;
            List<MenuSlotIR> slots = new ArrayList<>(total);
            for (int i = 0; i < total; i++) {
                int col = i % 9;
                int row = i / 9;
                slots.add(new MenuSlotIR(i, 8 + col * 18, 18 + row * 18, SlotRole.STORAGE, true, List.of()));
            }

            ContainerType type = switch (rows) {
                case 1 -> ContainerType.GENERIC_9X1;
                case 2 -> ContainerType.GENERIC_9X2;
                case 3 -> ContainerType.GENERIC_9X3;
                case 4 -> ContainerType.GENERIC_9X4;
                case 5 -> ContainerType.GENERIC_9X5;
                case 6 -> ContainerType.GENERIC_9X6;
                default -> ContainerType.GENERIC_9X3;
            };

            return new MenuLayoutIR(menuId, type, total, slots, List.of(), Map.of());
        }
    }
}
