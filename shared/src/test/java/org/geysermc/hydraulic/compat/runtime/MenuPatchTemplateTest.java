package org.geysermc.hydraulic.compat.runtime;

import net.minecraft.resources.Identifier;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.geysermc.hydraulic.compat.MappingOwnership;
import org.geysermc.hydraulic.compat.mapping.ContentPatch;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuPatchTemplateTest {
    @Test
    void resolvesFallbackContainerType() {
        MenuPatchTemplate template = MenuPatchTemplate.resolve(List.of(new ContentPatch(
            Identifier.fromNamespaceAndPath("test", "barrel_menu"),
            "menu",
            Map.of("bedrock.menu.container_type", "generic-9x3"),
            MappingOwnership.USER,
            "user/menus.json",
            MappingOwnership.USER.priority(),
            0
        )));

        assertNotNull(template);
        assertEquals(ContainerType.GENERIC_9X3, template.fallbackContainerType());
    }

    @Test
    void resolvesCrafterContainerTypeUsingProtocolEnumName() {
        MenuPatchTemplate template = MenuPatchTemplate.resolve(List.of(new ContentPatch(
            Identifier.fromNamespaceAndPath("test", "crafter_menu"),
            "menu",
            Map.of("bedrock.menu.container_type", "crafter_3x3"),
            MappingOwnership.USER,
            "user/menus.json",
            MappingOwnership.USER.priority(),
            0
        )));

        assertNotNull(template);
        assertEquals(ContainerType.CRAFTER_3x3, template.fallbackContainerType());
    }

    @Test
    void laterPatchesOverrideEarlierContainerType() {
        MenuPatchTemplate template = MenuPatchTemplate.resolve(List.of(
            new ContentPatch(
                Identifier.fromNamespaceAndPath("test", "barrel_menu"),
                "menu",
                Map.of("bedrock.menu.container_type", "generic_9x2"),
                MappingOwnership.BUILTIN,
                "builtin/menus.json",
                MappingOwnership.BUILTIN.priority(),
                0
            ),
            new ContentPatch(
                Identifier.fromNamespaceAndPath("test", "barrel_menu"),
                "menu",
                Map.of("bedrock.menu.container_type", "generic_9x3"),
                MappingOwnership.USER,
                "user/menus.json",
                MappingOwnership.USER.priority(),
                0
            )
        ));

        assertNotNull(template);
        assertEquals(ContainerType.GENERIC_9X3, template.fallbackContainerType());
    }

    @Test
    void ignoresInvalidContainerTypes() {
        assertEquals("NOT_A_REAL_CONTAINER", MenuPatchTemplate.normalizeContainerTypeName("not_a_real_container"));
        assertTrue(!MenuPatchTemplate.isSupportedContainerType(MenuPatchTemplate.normalizeContainerTypeName("not_a_real_container")));
        assertTrue(!MenuPatchTemplate.supports(List.of()));
    }

    @Test
    void compilesValidatedSlotRolesAndSynchronizedProperties() {
        MenuPatchTemplate template = MenuPatchTemplate.resolve(List.of(new ContentPatch(
            Identifier.fromNamespaceAndPath("test", "machine_menu"),
            "menu",
            Map.of(
                "bedrock.menu.container_type", "generic_9x3",
                "container.slot.input", "2, 3",
                "container.slot.output", "0",
                "container.property.0", "progress"
            ),
            MappingOwnership.USER,
            "user/menus.json",
            MappingOwnership.USER.priority(),
            0
        )));

        assertNotNull(template);
        assertEquals(List.of(2, 3), template.slotRoles().get(SlotRole.INPUT));
        assertEquals(List.of(0), template.slotRoles().get(SlotRole.OUTPUT));
        assertEquals(Map.of(0, "progress"), template.synchronizedProperties());
    }

    @Test
    void dropsMalformedSlotAndPropertyFactsFailClosed() {
        MenuPatchTemplate template = MenuPatchTemplate.resolve(List.of(new ContentPatch(
            Identifier.fromNamespaceAndPath("test", "machine_menu"),
            "menu",
            Map.of(
                "bedrock.menu.container_type", "generic_9x3",
                "container.slot.input", "2, -1",
                "container.slot.output", "0, 0",
                "container.property.-1", "progress",
                "container.property.bad", "energy"
            ),
            MappingOwnership.USER,
            "user/menus.json",
            MappingOwnership.USER.priority(),
            0
        )));

        assertNotNull(template);
        assertTrue(template.slotRoles().isEmpty());
        assertTrue(template.synchronizedProperties().isEmpty());
    }
}