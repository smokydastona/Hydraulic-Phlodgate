package org.geysermc.hydraulic.compat.capability;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Universal runtime capability discovery and reflection engine.
 * Safely inspects live Java runtime objects (BlockEntities, Containers, Entities, Menus, Custom Mod APIs)
 * to detect concrete capability implementations with zero crash risk.
 */
public final class RuntimeCapabilityDiscoveryEngine {

    private RuntimeCapabilityDiscoveryEngine() {
    }

    public record DiscoveredCapabilities(
        @NotNull Object targetObject,
        @NotNull Class<?> targetClass,
        boolean hasItemTransfer,
        boolean hasFluidTransfer,
        boolean hasEnergyTransfer,
        boolean hasMachineProcessing,
        boolean hasMenu,
        boolean hasBlockEntityData,
        boolean hasEntityInteraction,
        int detectedSlotCount,
        int detectedTankCount,
        int detectedEnergyCapacity,
        @NotNull List<String> detectedMethodSignatures,
        @NotNull Map<String, String> capabilityAttributes
    ) {
        public DiscoveredCapabilities {
            detectedMethodSignatures = List.copyOf(detectedMethodSignatures);
            capabilityAttributes = Collections.unmodifiableMap(new LinkedHashMap<>(capabilityAttributes));
        }

        public boolean hasAnyTransfer() {
            return hasItemTransfer || hasFluidTransfer || hasEnergyTransfer;
        }

        public boolean isComplexMachine() {
            return hasMachineProcessing || (hasItemTransfer && (hasFluidTransfer || hasEnergyTransfer));
        }
    }

    @NotNull
    public static DiscoveredCapabilities discover(@Nullable Object target) {
        if (target == null) {
            return new DiscoveredCapabilities(
                new Object(),
                Object.class,
                false, false, false, false, false, false, false,
                0, 0, 0,
                List.of(),
                Map.of()
            );
        }

        Class<?> clazz = target.getClass();
        List<String> signatures = new ArrayList<>();
        Map<String, String> attributes = new LinkedHashMap<>();

        boolean itemTransfer = discoverItemTransfer(target, clazz, signatures, attributes);
        boolean fluidTransfer = discoverFluidTransfer(target, clazz, signatures, attributes);
        boolean energyTransfer = discoverEnergyTransfer(target, clazz, signatures, attributes);
        boolean machineProcessing = discoverMachineProcessing(target, clazz, signatures, attributes);
        boolean menu = discoverMenu(target, clazz, signatures, attributes);
        boolean blockEntityData = discoverBlockEntityData(target, clazz, signatures, attributes);
        boolean entityInteraction = discoverEntityInteraction(target, clazz, signatures, attributes);

        int slotCount = parseInteger(attributes.get("slot_count"), 0);
        int tankCount = parseInteger(attributes.get("tank_count"), 0);
        int energyCap = parseInteger(attributes.get("energy_capacity"), 0);

        return new DiscoveredCapabilities(
            target,
            clazz,
            itemTransfer,
            fluidTransfer,
            energyTransfer,
            machineProcessing,
            menu,
            blockEntityData,
            entityInteraction,
            slotCount,
            tankCount,
            energyCap,
            signatures,
            attributes
        );
    }

    private static boolean discoverItemTransfer(Object target, Class<?> clazz, List<String> signatures, Map<String, String> attributes) {
        boolean detected = false;
        // Check for vanilla Container / WorldlyContainer
        try {
            Class<?> containerClass = Class.forName("net.minecraft.world.Container");
            if (containerClass.isAssignableFrom(clazz)) {
                detected = true;
                signatures.add("implements net.minecraft.world.Container");
                attributes.put("item_api", "vanilla_container");
                Method sizeMethod = clazz.getMethod("getContainerSize");
                Object size = sizeMethod.invoke(target);
                if (size instanceof Number num) {
                    attributes.put("slot_count", String.valueOf(num.intValue()));
                }
            }
        } catch (Throwable ignored) {
        }

        // Check reflection methods: insertItem, extractItem, getContainerSize, getSlots, getItem
        Method insertMethod = findMethod(clazz, "insertItem", "insert", "addItem");
        Method extractMethod = findMethod(clazz, "extractItem", "extract", "takeItem");
        Method slotMethod = findMethod(clazz, "getContainerSize", "getSlots", "getSlotCount");
        Method getItemMethod = findMethod(clazz, "getItem", "getStackInSlot");

        if (insertMethod != null || extractMethod != null || slotMethod != null || getItemMethod != null) {
            detected = true;
            if (insertMethod != null) {
                signatures.add(insertMethod.getName() + "(" + formatParams(insertMethod) + ")");
                attributes.put("can_insert_item", "true");
            }
            if (extractMethod != null) {
                signatures.add(extractMethod.getName() + "(" + formatParams(extractMethod) + ")");
                attributes.put("can_extract_item", "true");
            }
            if (slotMethod != null && !attributes.containsKey("slot_count")) {
                signatures.add(slotMethod.getName() + "()");
                try {
                    Object size = slotMethod.invoke(target);
                    if (size instanceof Number num) {
                        attributes.put("slot_count", String.valueOf(num.intValue()));
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return detected;
    }

    private static boolean discoverFluidTransfer(Object target, Class<?> clazz, List<String> signatures, Map<String, String> attributes) {
        boolean detected = false;
        Method fillMethod = findMethod(clazz, "fill", "insertFluid", "fillTank");
        Method drainMethod = findMethod(clazz, "drain", "extractFluid", "drainTank");
        Method tankCountMethod = findMethod(clazz, "getTanks", "getTankCount", "tankCount");
        Method getFluidMethod = findMethod(clazz, "getFluid", "getFluidInTank", "getFluidStack");
        Method capacityMethod = findMethod(clazz, "getTankCapacity", "tankCapacity", "getCapacity");

        if (fillMethod != null || drainMethod != null || getFluidMethod != null || capacityMethod != null) {
            detected = true;
            if (fillMethod != null) {
                signatures.add(fillMethod.getName() + "(" + formatParams(fillMethod) + ")");
                attributes.put("can_fill_fluid", "true");
            }
            if (drainMethod != null) {
                signatures.add(drainMethod.getName() + "(" + formatParams(drainMethod) + ")");
                attributes.put("can_drain_fluid", "true");
            }
            if (tankCountMethod != null) {
                try {
                    Object tanks = tankCountMethod.invoke(target);
                    if (tanks instanceof Number num) {
                        attributes.put("tank_count", String.valueOf(num.intValue()));
                    }
                } catch (Throwable ignored) {
                }
            } else if (detected && !attributes.containsKey("tank_count")) {
                attributes.put("tank_count", "1");
            }
            if (capacityMethod != null) {
                try {
                    Object cap = capacityMethod.getParameterCount() == 0 ? capacityMethod.invoke(target) : capacityMethod.invoke(target, 0);
                    if (cap instanceof Number num) {
                        attributes.put("fluid_capacity", String.valueOf(num.intValue()));
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return detected;
    }

    private static boolean discoverEnergyTransfer(Object target, Class<?> clazz, List<String> signatures, Map<String, String> attributes) {
        boolean detected = false;
        Method receiveMethod = findMethod(clazz, "receiveEnergy", "insertEnergy", "addEnergy");
        Method extractMethod = findMethod(clazz, "extractEnergy", "extractPower", "takeEnergy");
        Method storedMethod = findMethod(clazz, "getEnergyStored", "getStoredEnergy", "getEnergy", "energyStored");
        Method maxMethod = findMethod(clazz, "getMaxEnergyStored", "getMaxEnergy", "maxEnergy", "getCapacity");

        if (receiveMethod != null || extractMethod != null || storedMethod != null || maxMethod != null) {
            detected = true;
            if (receiveMethod != null) {
                signatures.add(receiveMethod.getName() + "(" + formatParams(receiveMethod) + ")");
                attributes.put("can_receive_energy", "true");
            }
            if (extractMethod != null) {
                signatures.add(extractMethod.getName() + "(" + formatParams(extractMethod) + ")");
                attributes.put("can_extract_energy", "true");
            }
            if (storedMethod != null) {
                try {
                    Object stored = storedMethod.invoke(target);
                    if (stored instanceof Number num) {
                        attributes.put("energy_stored", String.valueOf(num.intValue()));
                    }
                } catch (Throwable ignored) {
                }
            }
            if (maxMethod != null) {
                try {
                    Object max = maxMethod.invoke(target);
                    if (max instanceof Number num) {
                        attributes.put("energy_capacity", String.valueOf(num.intValue()));
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return detected;
    }

    private static boolean discoverMachineProcessing(Object target, Class<?> clazz, List<String> signatures, Map<String, String> attributes) {
        boolean detected = false;
        Method tickMethod = findMethod(clazz, "tick", "serverTick", "tickProcess");
        Method progressMethod = findMethod(clazz, "getProgress", "getProcessingTime", "getCookTime");
        Method maxProgressMethod = findMethod(clazz, "getMaxProgress", "getTotalProcessingTime", "getCookTimeTotal");
        Method isProcessingMethod = findMethod(clazz, "isProcessing", "isBurning", "isActive", "isWorking");

        if (progressMethod != null || maxProgressMethod != null || isProcessingMethod != null) {
            detected = true;
            attributes.put("has_processing_contract", "true");
            if (progressMethod != null) {
                signatures.add(progressMethod.getName() + "()");
            }
            if (isProcessingMethod != null) {
                signatures.add(isProcessingMethod.getName() + "()");
            }
        }
        if (tickMethod != null) {
            signatures.add(tickMethod.getName() + "(" + formatParams(tickMethod) + ")");
            attributes.put("has_tick_method", "true");
        }
        return detected;
    }

    private static boolean discoverMenu(Object target, Class<?> clazz, List<String> signatures, Map<String, String> attributes) {
        boolean detected = false;
        try {
            Class<?> menuClass = Class.forName("net.minecraft.world.inventory.AbstractContainerMenu");
            if (menuClass.isAssignableFrom(clazz)) {
                detected = true;
                signatures.add("extends AbstractContainerMenu");
                attributes.put("menu_class", clazz.getSimpleName());
            }
        } catch (Throwable ignored) {
        }
        try {
            Class<?> menuProviderClass = Class.forName("net.minecraft.world.MenuProvider");
            if (menuProviderClass.isAssignableFrom(clazz)) {
                detected = true;
                signatures.add("implements MenuProvider");
                attributes.put("menu_provider", "true");
            }
        } catch (Throwable ignored) {
        }
        return detected;
    }

    private static boolean discoverBlockEntityData(Object target, Class<?> clazz, List<String> signatures, Map<String, String> attributes) {
        boolean detected = false;
        try {
            Class<?> beClass = Class.forName("net.minecraft.world.level.block.entity.BlockEntity");
            if (beClass.isAssignableFrom(clazz)) {
                detected = true;
                signatures.add("extends BlockEntity");
                attributes.put("is_block_entity", "true");
            }
        } catch (Throwable ignored) {
        }
        return detected;
    }

    private static boolean discoverEntityInteraction(Object target, Class<?> clazz, List<String> signatures, Map<String, String> attributes) {
        boolean detected = false;
        try {
            Class<?> entityClass = Class.forName("net.minecraft.world.entity.Entity");
            if (entityClass.isAssignableFrom(clazz)) {
                detected = true;
                signatures.add("extends Entity");
                attributes.put("is_entity", "true");
            }
        } catch (Throwable ignored) {
        }
        Method interactMethod = findMethod(clazz, "interact", "mobInteract", "interactAt");
        if (interactMethod != null) {
            detected = true;
            signatures.add(interactMethod.getName() + "(" + formatParams(interactMethod) + ")");
            attributes.put("has_interaction_handler", "true");
        }
        return detected;
    }

    @Nullable
    private static Method findMethod(Class<?> clazz, String... candidateNames) {
        for (String name : candidateNames) {
            for (Method method : clazz.getMethods()) {
                if (method.getName().equalsIgnoreCase(name) && !Modifier.isStatic(method.getModifiers())) {
                    return method;
                }
            }
        }
        return null;
    }

    private static String formatParams(Method method) {
        Class<?>[] params = method.getParameterTypes();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(params[i].getSimpleName());
        }
        return sb.toString();
    }

    private static int parseInteger(@Nullable String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
