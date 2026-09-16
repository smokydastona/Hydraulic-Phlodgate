package org.geysermc.hydraulic.compat.runtime;

import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.geysermc.hydraulic.compat.mapping.ContentPatch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class MenuPatchTemplate {
    private static final String FALLBACK_CONTAINER_TYPE = "bedrock.menu.container_type";
    private static final String SLOT_PREFIX = "container.slot.";
    private static final String PROPERTY_PREFIX = "container.property.";
    private static final Map<String, ContainerType> CONTAINER_TYPES_BY_NORMALIZED_NAME =
        java.util.Arrays.stream(ContainerType.values())
            .collect(Collectors.toUnmodifiableMap(
                type -> normalizeContainerTypeName(type.name()),
                type -> type,
                (left, right) -> left
            ));

    private final @NotNull ContainerType fallbackContainerType;
    private final @NotNull Map<SlotRole, List<Integer>> slotRoles;
    private final @NotNull Map<Integer, String> synchronizedProperties;

    private MenuPatchTemplate(
        @NotNull ContainerType fallbackContainerType,
        @NotNull Map<SlotRole, List<Integer>> slotRoles,
        @NotNull Map<Integer, String> synchronizedProperties
    ) {
        this.fallbackContainerType = fallbackContainerType;
        this.slotRoles = Collections.unmodifiableMap(new LinkedHashMap<>(slotRoles));
        this.synchronizedProperties = Collections.unmodifiableMap(new LinkedHashMap<>(synchronizedProperties));
    }

    @Nullable
    public static MenuPatchTemplate resolve(@NotNull List<ContentPatch> patches) {
        ContainerType fallbackContainerType = null;
        Map<SlotRole, List<Integer>> slotRoles = new LinkedHashMap<>();
        Map<Integer, String> synchronizedProperties = new LinkedHashMap<>();
        for (ContentPatch patch : patches) {
            ContainerType resolved = parseContainerType(patch.operation(FALLBACK_CONTAINER_TYPE));
            if (resolved == null) {
                if (patch.operation(FALLBACK_CONTAINER_TYPE) != null) {
                    fallbackContainerType = null;
                }
            } else {
                fallbackContainerType = resolved;
            }

            for (Map.Entry<String, String> operation : patch.operations().entrySet()) {
                if (operation.getKey().startsWith(SLOT_PREFIX)) {
                    SlotRole role = SlotRole.parse(operation.getKey().substring(SLOT_PREFIX.length()));
                    List<Integer> slots = parseSlots(operation.getValue());
                    if (role == SlotRole.UNKNOWN || slots == null) {
                        if (role != SlotRole.UNKNOWN) {
                            slotRoles.remove(role);
                        }
                    } else {
                        slotRoles.put(role, slots);
                    }
                } else if (operation.getKey().startsWith(PROPERTY_PREFIX)) {
                    Integer propertyId = parseNonNegativeInteger(operation.getKey().substring(PROPERTY_PREFIX.length()));
                    if (propertyId == null || operation.getValue() == null || operation.getValue().isBlank()) {
                        if (propertyId != null) {
                            synchronizedProperties.remove(propertyId);
                        }
                    } else {
                        synchronizedProperties.put(propertyId, operation.getValue().trim());
                    }
                }
            }
        }

        return fallbackContainerType != null ? new MenuPatchTemplate(fallbackContainerType, slotRoles, synchronizedProperties) : null;
    }

    public static boolean supports(@NotNull List<ContentPatch> patches) {
        return resolve(patches) != null;
    }

    @Nullable
    public static ContainerType parseContainerType(@Nullable String rawValue) {
        String normalized = normalizeContainerTypeName(rawValue);
        return normalized != null ? CONTAINER_TYPES_BY_NORMALIZED_NAME.get(normalized) : null;
    }

    @Nullable
    public static String normalizeContainerTypeName(@Nullable String rawValue) {
        if (rawValue == null) {
            return null;
        }

        String normalized = rawValue.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        normalized = normalized
            .replace('-', '_')
            .replace('.', '_')
            .toUpperCase(Locale.ROOT);
        return normalized;
    }

    @NotNull
    public ContainerType fallbackContainerType() {
        return this.fallbackContainerType;
    }

    @NotNull
    public Map<SlotRole, List<Integer>> slotRoles() {
        return this.slotRoles;
    }

    @NotNull
    public Map<Integer, String> synchronizedProperties() {
        return this.synchronizedProperties;
    }

    @NotNull
    public Map<String, String> inventoryFacts() {
        Map<String, String> facts = new LinkedHashMap<>();
        for (Map.Entry<SlotRole, List<Integer>> entry : this.slotRoles.entrySet()) {
            facts.put(SLOT_PREFIX + entry.getKey().name().toLowerCase(Locale.ROOT), entry.getValue().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
        }
        for (Map.Entry<Integer, String> entry : this.synchronizedProperties.entrySet()) {
            facts.put(PROPERTY_PREFIX + entry.getKey(), entry.getValue());
        }
        return Map.copyOf(facts);
    }

    public static boolean isSupportedContainerType(@Nullable String normalizedContainerType) {
        return normalizedContainerType != null && CONTAINER_TYPES_BY_NORMALIZED_NAME.containsKey(normalizedContainerType);
    }

    @Nullable
    private static List<Integer> parseSlots(@Nullable String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        List<Integer> slots = new ArrayList<>();
        for (String value : rawValue.split(",")) {
            Integer slot = parseNonNegativeInteger(value);
            if (slot == null || slots.contains(slot)) {
                return null;
            }
            slots.add(slot);
        }
        return List.copyOf(slots);
    }

    @Nullable
    private static Integer parseNonNegativeInteger(@Nullable String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            int value = Integer.parseInt(rawValue.trim());
            return value >= 0 ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}