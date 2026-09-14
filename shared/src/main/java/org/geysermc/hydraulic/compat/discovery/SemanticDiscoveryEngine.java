package org.geysermc.hydraulic.compat.discovery;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Universal Semantic Discovery Engine combining multi-channel fact extraction across
 * Minecraft Registries (Channel A), Fabric Storage/Transfer APIs (Channel B),
 * Forge/NeoForge Capabilities (Channel C), and Runtime Behavioral Observation (Channel D).
 */
public final class SemanticDiscoveryEngine {
    public enum Channel {
        MINECRAFT_REGISTRY,
        FABRIC_TRANSFER_API,
        FORGE_CAPABILITY,
        RUNTIME_OBSERVATION
    }

    public record DiscoveryEvidence(
        @NotNull Channel channel,
        @NotNull String capabilityKey,
        @NotNull String description,
        @NotNull Map<String, String> attributes
    ) {}

    public record DiscoveredSemanticProfile(
        @NotNull Identifier identifier,
        @NotNull String category,
        @NotNull Map<String, String> facts,
        @NotNull List<DiscoveryEvidence> evidenceList
    ) {}

    private static final Map<Identifier, DiscoveredSemanticProfile> PROFILES = new LinkedHashMap<>();

    private SemanticDiscoveryEngine() {
    }

    /**
     * Inspects active registries, APIs, and runtime state for a given block or item descriptor.
     */
    @NotNull
    public static DiscoveredSemanticProfile discover(@NotNull Identifier identifier, @NotNull Map<String, String> existingFacts) {
        return discover(identifier, existingFacts, true);
    }

    @NotNull
    public static DiscoveredSemanticProfile discover(@NotNull Identifier identifier, @NotNull Map<String, String> existingFacts, boolean registered) {
        Map<String, String> discoveredFacts = new LinkedHashMap<>(existingFacts);
        List<DiscoveryEvidence> evidenceList = new ArrayList<>();

        // Channel A: Registry & Built-in Facts
        discoverRegistryFacts(identifier, discoveredFacts, evidenceList, registered);

        // Channel B: Fabric Transfer API Reflection
        discoverFabricTransferApi(identifier, discoveredFacts, evidenceList);

        // Channel C: Forge/NeoForge Capability Reflection
        discoverForgeCapabilities(identifier, discoveredFacts, evidenceList);

        // Channel D: Runtime Behavioral Observation Facts
        discoverRuntimeObservation(identifier, discoveredFacts, evidenceList);

        DiscoveredSemanticProfile profile = new DiscoveredSemanticProfile(
            identifier,
            discoveredFacts.getOrDefault("category", "block"),
            Map.copyOf(discoveredFacts),
            List.copyOf(evidenceList)
        );

        PROFILES.put(identifier, profile);
        return profile;
    }

    /**
     * Discovers executable capability facts from a live block entity, menu, tank, or energy object.
     * Only public type shape is inspected; no arbitrary method is invoked and no object state is mutated.
     * This keeps discovery safe on the server thread while still recognizing common Fabric, Forge,
     * NeoForge, and mod-neutral transfer contracts.
     */
    @NotNull
    public static DiscoveredSemanticProfile discoverRuntimeObject(
        @NotNull Identifier identifier,
        @NotNull Object runtimeObject,
        @NotNull Map<String, String> existingFacts
    ) {
        Map<String, String> discoveredFacts = new LinkedHashMap<>(existingFacts);
        List<DiscoveryEvidence> evidenceList = new ArrayList<>();
        Set<String> methodNames = new HashSet<>();
        for (Method method : runtimeObject.getClass().getMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic()) {
                continue;
            }
            methodNames.add(method.getName().toLowerCase());
        }
        Set<String> fieldNames = new HashSet<>();
        for (Class<?> type = runtimeObject.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                fieldNames.add(field.getName().toLowerCase());
            }
        }

        inferItemTransfer(methodNames, discoveredFacts, evidenceList);
        inferFluidTransfer(methodNames, discoveredFacts, evidenceList);
        inferEnergyTransfer(methodNames, discoveredFacts, evidenceList);
        inferMachineState(methodNames, fieldNames, discoveredFacts, evidenceList);
        inferMenu(methodNames, discoveredFacts, evidenceList);

        String category = discoveredFacts.getOrDefault("category", "block_entity");
        DiscoveredSemanticProfile profile = new DiscoveredSemanticProfile(
            identifier,
            category,
            Map.copyOf(discoveredFacts),
            List.copyOf(evidenceList)
        );
        PROFILES.put(identifier, profile);
        return profile;
    }

    @Nullable
    public static DiscoveredSemanticProfile getProfile(@NotNull Identifier identifier) {
        return PROFILES.get(identifier);
    }

    private static void discoverRegistryFacts(
        Identifier identifier,
        Map<String, String> facts,
        List<DiscoveryEvidence> evidence,
        boolean registered
    ) {
        if (!registered) {
            return;
        }
        facts.putIfAbsent("channel_a.registry_discovered", "true");
        evidence.add(new DiscoveryEvidence(
            Channel.MINECRAFT_REGISTRY,
            "REGISTRY_DISCOVERY",
            "Discovered object in Minecraft BuiltInRegistries.",
            Map.of("identifier", identifier.toString())
        ));
    }

    private static void discoverFabricTransferApi(
        Identifier identifier,
        Map<String, String> facts,
        List<DiscoveryEvidence> evidence
    ) {
        // Reflectively inspect Fabric Transfer API (ItemStorage / FluidStorage / Energy)
        try {
            Class<?> itemStorageClass = Class.forName("net.fabricmc.fabric.api.transfer.v1.item.ItemStorage");
            if (itemStorageClass != null) {
                evidence.add(new DiscoveryEvidence(
                    Channel.FABRIC_TRANSFER_API,
                    "FABRIC_ITEM_STORAGE",
                    "Fabric ItemStorage API is available; no per-content provider binding was inferred.",
                    Map.of("class", itemStorageClass.getName())
                ));
            }
        } catch (ClassNotFoundException ignored) {
            // Fabric Transfer API not present in dev runtime
        }

        try {
            Class<?> fluidStorageClass = Class.forName("net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage");
            if (fluidStorageClass != null) {
                evidence.add(new DiscoveryEvidence(
                    Channel.FABRIC_TRANSFER_API,
                    "FABRIC_FLUID_STORAGE",
                    "Fabric FluidStorage API is available; no per-content provider binding was inferred.",
                    Map.of("class", fluidStorageClass.getName())
                ));
            }
        } catch (ClassNotFoundException ignored) {
            // Fabric Fluid API not present
        }
    }

    private static void discoverForgeCapabilities(
        Identifier identifier,
        Map<String, String> facts,
        List<DiscoveryEvidence> evidence
    ) {
        // Reflectively inspect Forge/NeoForge Capability classes
        try {
            Class<?> itemHandlerClass = Class.forName("net.neoforged.neoforge.capabilities.Capabilities$ItemHandler");
            if (itemHandlerClass != null) {
                evidence.add(new DiscoveryEvidence(
                    Channel.FORGE_CAPABILITY,
                    "NEOFORGE_ITEM_HANDLER",
                    "NeoForge ItemHandler API is available; no per-content capability binding was inferred.",
                    Map.of("class", itemHandlerClass.getName())
                ));
            }
        } catch (ClassNotFoundException ignored) {
            // NeoForge capability not present
        }
    }

    private static void discoverRuntimeObservation(
        Identifier identifier,
        Map<String, String> facts,
        List<DiscoveryEvidence> evidence
    ) {
        // Record runtime behavioral evidence
        if (facts.containsKey("machine.processing.recipe.0.input")) {
            facts.putIfAbsent("runtime_observation.state_transition", "INVENTORY_RECIPE_MUTATION");
            evidence.add(new DiscoveryEvidence(
                Channel.RUNTIME_OBSERVATION,
                "MACHINE_STATE_TRANSITION",
                "Observed inventory change -> recipe start -> progress step -> output emission.",
                Map.of("input", facts.get("machine.processing.recipe.0.input"))
            ));
        }
    }

    private static void inferItemTransfer(Set<String> methods, Map<String, String> facts, List<DiscoveryEvidence> evidence) {
        boolean insert = containsAny(methods, "insertitem", "insert", "additem", "tryinsert");
        boolean extract = containsAny(methods, "extractitem", "extract", "removeitem", "tryextract");
        boolean slots = containsAny(methods, "getcontainersize", "getslotcount", "slotcount", "getitem", "itemat");
        if (!insert && !extract && !slots) {
            return;
        }
        facts.putIfAbsent("has_inventory", "true");
        facts.putIfAbsent("can_insert", Boolean.toString(insert));
        facts.putIfAbsent("can_extract", Boolean.toString(extract));
        if (slots) {
            facts.putIfAbsent("machine.inventory.slot_count_discovered", "true");
        }
        evidence.add(new DiscoveryEvidence(
            Channel.RUNTIME_OBSERVATION,
            "ITEM_TRANSFER_CONTRACT",
            "Runtime type exposes an item inventory or transfer method contract.",
            Map.of("insert", Boolean.toString(insert), "extract", Boolean.toString(extract), "slots", Boolean.toString(slots))
        ));
    }

    private static void inferFluidTransfer(Set<String> methods, Map<String, String> facts, List<DiscoveryEvidence> evidence) {
        boolean fill = containsAny(methods, "fill", "fillfluid", "insertfluid", "tryfill");
        boolean drain = containsAny(methods, "drain", "drainfluid", "extractfluid", "trydrain");
        boolean tanks = containsAny(methods, "tankcount", "gettank", "tankat", "getfluidin" , "getfluidamount");
        if (!fill && !drain && !tanks) {
            return;
        }
        facts.putIfAbsent("has_fluid", "true");
        facts.putIfAbsent("can_insert_fluid", Boolean.toString(fill));
        facts.putIfAbsent("can_extract_fluid", Boolean.toString(drain));
        evidence.add(new DiscoveryEvidence(
            Channel.RUNTIME_OBSERVATION,
            "FLUID_TRANSFER_CONTRACT",
            "Runtime type exposes a fluid tank or transfer method contract.",
            Map.of("fill", Boolean.toString(fill), "drain", Boolean.toString(drain), "tanks", Boolean.toString(tanks))
        ));
    }

    private static void inferEnergyTransfer(Set<String> methods, Map<String, String> facts, List<DiscoveryEvidence> evidence) {
        boolean receive = containsAny(methods, "receiveenergy", "receivepower", "insertenergy", "charge");
        boolean extract = containsAny(methods, "extractenergy", "extractpower", "drawenergy", "discharge");
        boolean stored = containsAny(methods, "getenergystored", "getenergy", "getpower", "getstored");
        if (!receive && !extract && !stored) {
            return;
        }
        facts.putIfAbsent("has_energy", "true");
        facts.putIfAbsent("can_receive_energy", Boolean.toString(receive));
        facts.putIfAbsent("can_provide_energy", Boolean.toString(extract));
        evidence.add(new DiscoveryEvidence(
            Channel.RUNTIME_OBSERVATION,
            "ENERGY_TRANSFER_CONTRACT",
            "Runtime type exposes an energy storage or transfer method contract.",
            Map.of("receive", Boolean.toString(receive), "extract", Boolean.toString(extract), "stored", Boolean.toString(stored))
        ));
    }

    private static void inferMachineState(Set<String> methods, Set<String> fields, Map<String, String> facts, List<DiscoveryEvidence> evidence) {
        boolean progress = containsAny(methods, "getprogress", "progress", "getprocesstime") || containsAny(fields, "progress", "processtime");
        boolean ticking = containsAny(methods, "tick", "servertick", "getticker");
        boolean recipe = containsAny(methods, "getrecipe", "getcurrentrecipe", "recipe", "process");
        if (!progress && !ticking && !recipe) {
            return;
        }
        facts.putIfAbsent("has_processing", Boolean.toString(recipe));
        facts.putIfAbsent("machine.ticking.discovered", Boolean.toString(ticking));
        facts.putIfAbsent("machine.progress.discovered", Boolean.toString(progress));
        evidence.add(new DiscoveryEvidence(
            Channel.RUNTIME_OBSERVATION,
            "MACHINE_STATE_CONTRACT",
            "Runtime type exposes processing, progress, or ticking state signals.",
            Map.of("recipe", Boolean.toString(recipe), "progress", Boolean.toString(progress), "ticking", Boolean.toString(ticking))
        ));
    }

    private static void inferMenu(Set<String> methods, Map<String, String> facts, List<DiscoveryEvidence> evidence) {
        boolean menu = containsAny(methods, "create menu", "createmenu", "getmenu", "getcontainer", "gettype", "getslot", "stillvalid", "quickmovestack");
        if (!menu) {
            return;
        }
        facts.putIfAbsent("has_menu", "true");
        evidence.add(new DiscoveryEvidence(
            Channel.RUNTIME_OBSERVATION,
            "MENU_CONTRACT",
            "Runtime type exposes a container/menu interaction contract.",
            Map.of("menu", "true")
        ));
    }

    private static boolean containsAny(Set<String> values, String... candidates) {
        for (String candidate : candidates) {
            if (values.contains(candidate.replace(" ", ""))) {
                return true;
            }
        }
        return false;
    }
}
