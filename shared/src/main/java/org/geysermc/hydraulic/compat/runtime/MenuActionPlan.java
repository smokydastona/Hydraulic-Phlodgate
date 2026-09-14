package org.geysermc.hydraulic.compat.runtime;

import org.geysermc.hydraulic.compat.ir.CompiledCompatibilityPlan;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

/** Explicit compiled semantics for Java menu button IDs. */
public record MenuActionPlan(@NotNull Map<Integer, BedrockMenuActionRouter.MenuActionType> buttons) {
    public MenuActionPlan {
        buttons = Map.copyOf(buttons);
    }

    @NotNull
    public static MenuActionPlan from(@NotNull CompiledCompatibilityPlan plan) {
        Map<Integer, BedrockMenuActionRouter.MenuActionType> buttons = new LinkedHashMap<>();
        for (Map.Entry<String, String> fact : plan.inventoryFacts().entrySet()) {
            String prefix = "menu.button.";
            if (!fact.getKey().startsWith(prefix)) {
                continue;
            }
            try {
                int buttonId = Integer.parseInt(fact.getKey().substring(prefix.length()));
                if (buttonId < 0 || buttonId > 65_535) {
                    continue;
                }
                BedrockMenuActionRouter.MenuActionType action = switch (fact.getValue().trim().toLowerCase()) {
                    case "button" -> BedrockMenuActionRouter.MenuActionType.BUTTON;
                    case "toggle" -> BedrockMenuActionRouter.MenuActionType.TOGGLE;
                    default -> null;
                };
                if (action != null) {
                    buttons.put(buttonId, action);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return new MenuActionPlan(buttons);
    }

    @NotNull
    public BedrockMenuActionRouter.MenuActionType action(int buttonId) {
        return buttons.getOrDefault(buttonId, BedrockMenuActionRouter.MenuActionType.BUTTON);
    }
}
