package org.geysermc.hydraulic.compat.runtime;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.geysermc.hydraulic.compat.CompatibilityProfile;
import org.geysermc.hydraulic.compat.CompatibilityReport;
import org.geysermc.hydraulic.compat.MappingResolver;
import org.geysermc.hydraulic.compat.ir.CompiledCompatibilityPlan;
import org.geysermc.hydraulic.compat.ir.CorpusEvidenceRef;
import org.geysermc.hydraulic.compat.model.CompatibilityObject;
import org.geysermc.hydraulic.compat.model.SupportLevel;
import org.geysermc.hydraulic.compat.model.SupportResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public final class RuntimeDispatchTable {
    private final Map<String, CompiledCompatibilityPlan> plansByTypeAndIdentifier;
    private final Map<String, List<CompiledCompatibilityPlan>> plansByModAndType;
    private final Map<String, List<MappingResolver.ResolvedBlockDefinition>> blockDefinitionsByIdentifier;
    private final Map<String, MappingResolver.ResolvedBlockState> blockStatesByIdentifierAndState;
    private final Map<RuntimeBridgeKind, List<CompiledCompatibilityPlan>> plansByRuntimeBridgeKind;
    private final Map<String, LookupCounters> countersByType;

    private RuntimeDispatchTable(
        @NotNull Map<String, CompiledCompatibilityPlan> plansByTypeAndIdentifier,
        @NotNull Map<String, List<CompiledCompatibilityPlan>> plansByModAndType,
        @NotNull Map<String, List<MappingResolver.ResolvedBlockDefinition>> blockDefinitionsByIdentifier,
        @NotNull Map<String, MappingResolver.ResolvedBlockState> blockStatesByIdentifierAndState,
        @NotNull Map<RuntimeBridgeKind, List<CompiledCompatibilityPlan>> plansByRuntimeBridgeKind
    ) {
        this.plansByTypeAndIdentifier = new ConcurrentHashMap<>(plansByTypeAndIdentifier);
        Map<String, List<CompiledCompatibilityPlan>> copy = new ConcurrentHashMap<>();
        for (Map.Entry<String, List<CompiledCompatibilityPlan>> entry : plansByModAndType.entrySet()) {
            copy.put(entry.getKey(), new CopyOnWriteArrayList<>(entry.getValue()));
        }
        this.plansByModAndType = copy;
        Map<String, List<MappingResolver.ResolvedBlockDefinition>> blockDefinitionCopy = new LinkedHashMap<>();
        for (Map.Entry<String, List<MappingResolver.ResolvedBlockDefinition>> entry : blockDefinitionsByIdentifier.entrySet()) {
            blockDefinitionCopy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        this.blockDefinitionsByIdentifier = Collections.unmodifiableMap(blockDefinitionCopy);
        this.blockStatesByIdentifierAndState = Collections.unmodifiableMap(new LinkedHashMap<>(blockStatesByIdentifierAndState));
        Map<RuntimeBridgeKind, List<CompiledCompatibilityPlan>> bridgeKindCopy = new ConcurrentHashMap<>();
        for (Map.Entry<RuntimeBridgeKind, List<CompiledCompatibilityPlan>> entry : plansByRuntimeBridgeKind.entrySet()) {
            bridgeKindCopy.put(entry.getKey(), new CopyOnWriteArrayList<>(entry.getValue()));
        }
        this.plansByRuntimeBridgeKind = bridgeKindCopy;
        this.countersByType = Map.of(
            "block", new LookupCounters(),
            "item", new LookupCounters(),
            "entity", new LookupCounters(),
            "menu", new LookupCounters(),
            "block_entity", new LookupCounters(),
            "fluid", new LookupCounters()
        );
    }

    public void registerDynamicPlan(@NotNull CompiledCompatibilityPlan plan) {
        String key = key(plan.contentType(), plan.javaIdentifier());
        CompiledCompatibilityPlan previous = this.plansByTypeAndIdentifier.put(key, plan);
        if (previous != null) {
            this.plansByModAndType.values().forEach(plans -> plans.removeIf(candidate -> candidate == previous));
            this.plansByRuntimeBridgeKind.values().forEach(plans -> plans.removeIf(candidate -> candidate == previous));
        }
        this.plansByModAndType.computeIfAbsent(key(plan.modId(), plan.contentType()), ignored -> new CopyOnWriteArrayList<>()).add(plan);
        for (RuntimeBridgeKind kind : plan.runtimeBridgeKinds()) {
            this.plansByRuntimeBridgeKind.computeIfAbsent(kind, ignored -> new CopyOnWriteArrayList<>()).add(plan);
        }
    }

    @NotNull
    public static RuntimeDispatchTable empty() {
        return new RuntimeDispatchTable(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }

    @NotNull
    public static RuntimeDispatchTable compile(@NotNull CompatibilityReport report, @NotNull MappingResolver mappingResolver) {
        Map<String, CompiledCompatibilityPlan> plansByIdentifier = new LinkedHashMap<>();
        Map<String, List<CompiledCompatibilityPlan>> plansByModAndType = new LinkedHashMap<>();
        Map<String, List<MappingResolver.ResolvedBlockDefinition>> blockDefinitionsByIdentifier = new LinkedHashMap<>();
        Map<String, MappingResolver.ResolvedBlockState> blockStatesByIdentifierAndState = new LinkedHashMap<>();
        Map<RuntimeBridgeKind, List<CompiledCompatibilityPlan>> plansByRuntimeBridgeKind = new EnumMap<>(RuntimeBridgeKind.class);
        for (CompatibilityProfile profile : report.mods().values()) {
            List<CorpusEvidenceRef> corpusEvidenceForMod = corpusEvidenceRefs(report.corpusEvidence().getOrDefault(profile.modId(), List.of()));
            for (CompatibilityObject object : profile.objects()) {
                List<CompatibilityReport.CorpusMatch> objectMatches = report.objectCorpusEvidence().get(key(object.contentType(), object.javaIdentifier()));
                List<CorpusEvidenceRef> corpusEvidenceForObject = objectMatches != null && !objectMatches.isEmpty()
                    ? corpusEvidenceRefs(objectMatches)
                    : corpusEvidenceForMod;
                plansByIdentifier.put(key(object.contentType(), object.javaIdentifier()), compilePlan(object, mappingResolver, corpusEvidenceForObject));
                compileBlockStatePlans(object, mappingResolver, blockDefinitionsByIdentifier, blockStatesByIdentifierAndState);
            }
        }

        for (CompiledCompatibilityPlan basePlan : List.copyOf(plansByIdentifier.values())) {
            CompiledCompatibilityPlan plan = refineCreativeExposure(basePlan, plansByIdentifier);
            plansByIdentifier.put(key(plan.contentType(), plan.javaIdentifier()), plan);
            plansByModAndType.computeIfAbsent(key(plan.modId(), plan.contentType()), ignored -> new ArrayList<>()).add(plan);
            for (RuntimeBridgeKind kind : plan.runtimeBridgeKinds()) {
                    plansByRuntimeBridgeKind.computeIfAbsent(kind, ignored -> new ArrayList<>()).add(plan);
            }
        }
        return new RuntimeDispatchTable(
            plansByIdentifier,
            plansByModAndType,
            blockDefinitionsByIdentifier,
            blockStatesByIdentifierAndState,
            plansByRuntimeBridgeKind
        );
    }

    @NotNull
    private static CompiledCompatibilityPlan refineCreativeExposure(
        @NotNull CompiledCompatibilityPlan plan,
        @NotNull Map<String, CompiledCompatibilityPlan> plansByIdentifier
    ) {
        if (!"item".equals(plan.contentType()) || !plan.allowsCreativeExposure()) {
            return plan;
        }

        String fluidSource = plan.inventoryFacts().get("fluid_source");
        if (fluidSource == null || fluidSource.isBlank()) {
            return plan;
        }

        CompiledCompatibilityPlan fluidPlan = plansByIdentifier.get(key("fluid", fluidSource));
        if (fluidPlan == null || !fluidPlan.requiresFluidRuntime()) {
            return plan;
        }

        return withCreativeExposure(plan, false, fluidCreativeExposureReason(fluidPlan));
    }

    @NotNull
    private static CompiledCompatibilityPlan withCreativeExposure(
        @NotNull CompiledCompatibilityPlan plan,
        boolean allowsCreativeExposure,
        @Nullable String creativeExposureReason
    ) {
        return new CompiledCompatibilityPlan(
            plan.modId(),
            plan.contentType(),
            plan.javaIdentifier(),
            plan.resolvedIdentifier(),
            plan.overallLevel(),
            plan.overallStatus(),
            plan.overallScore(),
            plan.confidence(),
            plan.adapterBindings(),
            plan.runtimeRequirements(),
            plan.runtimeBridgeKinds(),
            plan.inventoryFacts(),
            allowsCreativeExposure,
            creativeExposureReason,
            plan.allowsCustomRegistration(),
            plan.customRegistrationReason(),
            plan.supportsBlockItemTextureFallback(),
            plan.supportsBlockPlacement(),
            plan.supportsWearablePresentation(),
            plan.supportsAttachablePresentation(),
            plan.requiresMenuBridge(),
            plan.menuFallbackContainerType(),
            plan.interactionPrompt(),
            plan.menuRuntimeRequirements(),
            plan.blockEntityRuntimeRequirements(),
            plan.fluidRuntimeRequirements(),
            plan.blockEntityPatchTemplate(),
            plan.requiresBlockEntityRuntime(),
            plan.requiresFluidRuntime(),
            plan.behaviorLevel(),
            plan.behaviorTag(),
            plan.corpusEvidence()
        );
    }

    @NotNull
    private static List<CorpusEvidenceRef> corpusEvidenceRefs(@NotNull List<CompatibilityReport.CorpusMatch> matches) {
        List<CorpusEvidenceRef> refs = new ArrayList<>(matches.size());
        for (CompatibilityReport.CorpusMatch match : matches) {
            refs.add(CorpusEvidenceRef.of(match.capability(), match.corpusId(), match.tier(), match.score()));
        }
        return List.copyOf(refs);
    }

    @NotNull
    private static String fluidCreativeExposureReason(@NotNull CompiledCompatibilityPlan fluidPlan) {
        StringBuilder reason = new StringBuilder("source fluid runtime bridge is required (fluid: ")
            .append(fluidPlan.javaIdentifier());
        if (fluidPlan.behaviorTag() != null && !fluidPlan.behaviorTag().isBlank()) {
            reason.append(", tag: ").append(fluidPlan.behaviorTag());
        }
        return reason.append(')').toString();
    }

    @Nullable
    public CompiledCompatibilityPlan block(@NotNull Identifier javaIdentifier) {
        return this.plan("block", javaIdentifier.toString());
    }

    @Nullable
    public CompiledCompatibilityPlan item(@NotNull Identifier javaIdentifier) {
        return this.plan("item", javaIdentifier.toString());
    }

    @Nullable
    public CompiledCompatibilityPlan entity(@NotNull Identifier javaIdentifier) {
        return this.plan("entity", javaIdentifier.toString());
    }

    @Nullable
    public CompiledCompatibilityPlan menu(@NotNull Identifier javaIdentifier) {
        return this.plan("menu", javaIdentifier.toString());
    }

    @Nullable
    public CompiledCompatibilityPlan blockEntity(@NotNull Identifier javaIdentifier) {
        return this.plan("block_entity", javaIdentifier.toString());
    }

    @Nullable
    public CompiledCompatibilityPlan fluid(@NotNull Identifier javaIdentifier) {
        return this.plan("fluid", javaIdentifier.toString());
    }

    @Nullable
    public TransferBridgeFactory.ItemTransferBridge itemTransfer(@NotNull Identifier blockIdentifier, @NotNull Object runtimeInventory) {
        CompiledCompatibilityPlan plan = this.block(blockIdentifier);
        return plan == null ? null : TransferBridgeFactory.createItemTransfer(plan, runtimeInventory);
    }

    @Nullable
    public ItemTransferTransaction itemTransaction(@NotNull Identifier blockIdentifier, @NotNull Object runtimeInventory) {
        TransferBridgeFactory.ItemTransferBridge bridge = this.itemTransfer(blockIdentifier, runtimeInventory);
        return bridge == null ? null : new ItemTransferTransaction(bridge);
    }

    @Nullable
    public TransferBridgeFactory.FluidTransferBridge fluidTransfer(@NotNull Identifier blockIdentifier, @NotNull Object runtimeTank) {
        CompiledCompatibilityPlan plan = this.block(blockIdentifier);
        return plan == null ? null : TransferBridgeFactory.createFluidTransfer(plan, runtimeTank);
    }

    @Nullable
    public FluidTransferTransaction fluidTransaction(@NotNull Identifier blockIdentifier, @NotNull Object runtimeTank) {
        TransferBridgeFactory.FluidTransferBridge bridge = this.fluidTransfer(blockIdentifier, runtimeTank);
        return bridge == null ? null : new FluidTransferTransaction(bridge);
    }

    @Nullable
    public TransferBridgeFactory.EnergyTransferBridge energyTransfer(@NotNull Identifier blockIdentifier, @NotNull Object runtimeStorage) {
        CompiledCompatibilityPlan plan = this.block(blockIdentifier);
        return plan == null ? null : TransferBridgeFactory.createEnergyTransfer(plan, runtimeStorage);
    }

    @Nullable
    public EnergyTransferTransaction energyTransaction(@NotNull Identifier blockIdentifier, @NotNull Object runtimeStorage) {
        TransferBridgeFactory.EnergyTransferBridge bridge = this.energyTransfer(blockIdentifier, runtimeStorage);
        return bridge == null ? null : new EnergyTransferTransaction(bridge);
    }

    @Nullable
    public FluidContainerBridge fluidContainer(
        @NotNull Identifier blockIdentifier,
        @NotNull Object runtimeTank,
        int tankIndex,
        int containerCapacity
    ) {
        CompiledCompatibilityPlan plan = this.block(blockIdentifier);
        TransferBridgeFactory.FluidTransferBridge transfer = plan == null
            ? null
            : TransferBridgeFactory.createFluidTransfer(plan, runtimeTank);
        return transfer == null ? null : new FluidContainerBridge(transfer, tankIndex, containerCapacity);
    }

    @Nullable
    public MachineBridgeFactory.MachineBehaviorBridge machineBehavior(@NotNull Identifier blockIdentifier) {
        CompiledCompatibilityPlan plan = this.block(blockIdentifier);
        return plan == null ? null : MachineBridgeFactory.createMachineBehavior(plan);
    }

    @Nullable
    public MachineBridgeFactory.MachineInventoryBridge machineInventory(@NotNull Identifier blockIdentifier) {
        CompiledCompatibilityPlan plan = this.block(blockIdentifier);
        return plan == null ? null : MachineBridgeFactory.createMachineInventory(plan);
    }

    @Nullable
    public MachineBridgeFactory.InventoryAccess inventoryAccess(@NotNull Identifier blockIdentifier, @NotNull Object runtimeInventory) {
        CompiledCompatibilityPlan plan = this.block(blockIdentifier);
        TransferBridgeFactory.ItemTransferBridge transfer = plan == null
            ? null
            : TransferBridgeFactory.createItemTransfer(plan, runtimeInventory);
        return MachineBridgeFactory.createInventoryAccess(plan, transfer);
    }

    @Nullable
    public MachineBridgeFactory.AutomationAccess automationAccess(@NotNull Identifier blockIdentifier, @NotNull Object runtimeInventory) {
        CompiledCompatibilityPlan plan = this.block(blockIdentifier);
        TransferBridgeFactory.ItemTransferBridge transfer = plan == null
            ? null
            : TransferBridgeFactory.createItemTransfer(plan, runtimeInventory);
        return MachineBridgeFactory.createAutomation(plan, transfer);
    }

    @Nullable
    public MachineBridgeFactory.ResourceAutomationAccess resourceAutomationAccess(
        @NotNull Identifier blockIdentifier,
        @Nullable Object runtimeInventory,
        @Nullable Object runtimeTank,
        @Nullable Object runtimeStorage
    ) {
        CompiledCompatibilityPlan plan = this.block(blockIdentifier);
        TransferBridgeFactory.ItemTransferBridge itemTransfer = plan == null || runtimeInventory == null
            ? null
            : TransferBridgeFactory.createItemTransfer(plan, runtimeInventory);
        TransferBridgeFactory.FluidTransferBridge fluidTransfer = plan == null || runtimeTank == null
            ? null
            : TransferBridgeFactory.createFluidTransfer(plan, runtimeTank);
        TransferBridgeFactory.EnergyTransferBridge energyTransfer = plan == null || runtimeStorage == null
            ? null
            : TransferBridgeFactory.createEnergyTransfer(plan, runtimeStorage);
        return MachineBridgeFactory.createResourceAutomation(plan, itemTransfer, fluidTransfer, energyTransfer);
    }

    @Nullable
    public MachineProcessingBridge machineProcessing(
        @NotNull Identifier blockIdentifier,
        @Nullable TransferBridgeFactory.ItemTransferBridge inventory,
        int inputSlot,
        int outputSlot,
        @NotNull List<MachineProcessingBridge.MachineRecipe> recipes
    ) {
        CompiledCompatibilityPlan plan = this.block(blockIdentifier);
        return MachineBridgeFactory.createProcessing(plan, inventory, inputSlot, outputSlot, recipes);
    }

    @Nullable
    public MachineProcessingBridge machineProcessing(
        @NotNull Identifier blockIdentifier,
        @Nullable TransferBridgeFactory.ItemTransferBridge inventory
    ) {
        CompiledCompatibilityPlan plan = this.block(blockIdentifier);
        return MachineBridgeFactory.createProcessing(plan, inventory);
    }

    @Nullable
    public CompiledCompatibilityPlan plan(@NotNull String contentType, @NotNull String javaIdentifier) {
        CompiledCompatibilityPlan plan = this.plansByTypeAndIdentifier.get(key(contentType, javaIdentifier));
        this.recordLookup(contentType, plan != null);
        return plan;
    }

    @NotNull
    public List<CompiledCompatibilityPlan> plans(@NotNull String modId, @NotNull String contentType) {
        List<CompiledCompatibilityPlan> plans = this.plansByModAndType.getOrDefault(key(modId, contentType), List.of());
        this.recordLookup(contentType, !plans.isEmpty());
        return plans;
    }

    @NotNull
    public List<CompiledCompatibilityPlan> entityPlans(@NotNull String modId) {
        return this.plans(modId, "entity");
    }

    @NotNull
    public List<MappingResolver.ResolvedBlockDefinition> blockDefinitions(@NotNull Identifier javaIdentifier) {
        List<MappingResolver.ResolvedBlockDefinition> definitions = this.blockDefinitionsByIdentifier.getOrDefault(javaIdentifier.toString(), List.of());
        this.recordLookup("block", !definitions.isEmpty());
        return definitions;
    }

    @Nullable
    public MappingResolver.ResolvedBlockState blockState(@NotNull Identifier javaIdentifier, @NotNull net.minecraft.world.level.block.state.BlockState state) {
        MappingResolver.ResolvedBlockState resolvedState = this.blockStatesByIdentifierAndState.get(blockStateKey(javaIdentifier.toString(), Block.getId(state)));
        this.recordLookup("block", resolvedState != null);
        return resolvedState;
    }

    @NotNull
    public List<CompiledCompatibilityPlan> menuBridgePlans() {
        return this.runtimeBridgePlans(RuntimeBridgeKind.menuRuntimeKinds());
    }

    @NotNull
    public List<CompiledCompatibilityPlan> blockEntityBridgePlans() {
        return this.runtimeBridgePlans(RuntimeBridgeKind.blockEntityRuntimeKinds());
    }

    @NotNull
    public List<CompiledCompatibilityPlan> runtimeBridgePlans(@NotNull RuntimeBridgeKind kind) {
        return this.plansByRuntimeBridgeKind.getOrDefault(kind, List.of());
    }

    @NotNull
    public List<CompiledCompatibilityPlan> runtimeBridgePlans(@NotNull List<RuntimeBridgeKind> kinds) {
        if (kinds.isEmpty()) {
            return List.of();
        }

        Map<String, CompiledCompatibilityPlan> combined = new LinkedHashMap<>();
        for (RuntimeBridgeKind kind : kinds) {
            for (CompiledCompatibilityPlan plan : this.runtimeBridgePlans(kind)) {
                combined.putIfAbsent(key(plan.contentType(), plan.javaIdentifier()), plan);
            }
        }

        List<CompiledCompatibilityPlan> plans = new ArrayList<>(combined.values());
        plans.sort(planComparator());
        return List.copyOf(plans);
    }

    @NotNull
    private static CompiledCompatibilityPlan compilePlan(
        @NotNull CompatibilityObject object,
        @NotNull MappingResolver mappingResolver,
        @NotNull List<CorpusEvidenceRef> corpusEvidenceForMod
    ) {
        Identifier javaIdentifier = Identifier.parse(object.javaIdentifier());
        Block block = "block".equals(object.contentType()) ? BuiltInRegistries.BLOCK.getValue(javaIdentifier) : null;
        Item item = "item".equals(object.contentType()) ? BuiltInRegistries.ITEM.getValue(javaIdentifier) : null;

        String resolvedIdentifier = switch (object.contentType()) {
            case "item" -> mappingResolver.resolveItemIdentifier(javaIdentifier).identifier().toString();
            case "entity" -> mappingResolver.resolveEntityIdentifier(javaIdentifier).identifier().toString();
            case "menu" -> mappingResolver.resolveMenuIdentifier(javaIdentifier).identifier().toString();
            default -> javaIdentifier.toString();
        };

        List<RuntimeBridgeKind> runtimeBridgeKinds = RuntimeBridgeKind.resolve(object.runtimeRequirements());
        boolean supportsWearablePresentation = supportsWearablePresentation(object, item);
        boolean supportsAttachablePresentation = supportsAttachablePresentation(object, item);
        boolean requiresItemBehaviorBridge = runtimeBridgeKinds.contains(RuntimeBridgeKind.ITEM_BEHAVIOR);

        boolean allowsItemCreativeExposure = allowsItemCreativeExposure(object, item, supportsWearablePresentation, supportsAttachablePresentation, requiresItemBehaviorBridge);
        String creativeExposureReason = switch (object.contentType()) {
            case "block" -> CompatibilityDecisions.creativeExposureReason(object);
            case "item" -> itemCreativeExposureReason(object, allowsItemCreativeExposure, requiresItemBehaviorBridge);
            default -> null;
        };

        boolean allowsCreativeExposure = switch (object.contentType()) {
            case "block" -> CompatibilityDecisions.allowsBlockCreativeExposure(object, block);
            case "item" -> allowsItemCreativeExposure;
            default -> false;
        };

        boolean allowsCustomRegistration = switch (object.contentType()) {
            case "item" -> CompatibilityDecisions.allowsCustomItemRegistration(object, item);
            case "entity" -> CompatibilityDecisions.allowsCustomEntityRegistration(object);
            default -> false;
        };

        String customRegistrationReason = switch (object.contentType()) {
            case "entity" -> CompatibilityDecisions.entityRegistrationReason(object);
            default -> null;
        };

        SupportLevel behaviorLevel = object.supportResults().containsKey("behavior") ? object.supportResults().get("behavior").level() : null;
        String behaviorTag = object.inventoryFacts().get("behavior_tag");
        boolean requiresMenuBridge = runtimeBridgeKinds.contains(RuntimeBridgeKind.MENU_CONTAINER);
        String interactionPrompt = object.inventoryFacts().get("interaction_prompt");
        List<String> menuRuntimeRequirements = bridgeRequirements(runtimeBridgeKinds, "menu");
        List<String> blockEntityRuntimeRequirements = bridgeRequirements(runtimeBridgeKinds, "block_entity");
        List<String> fluidRuntimeRequirements = bridgeRequirements(runtimeBridgeKinds, "fluid");
        MenuPatchTemplate menuPatchTemplate = mappingResolver.menuPatchTemplate(javaIdentifier);
        Map<String, String> inventoryFacts = new LinkedHashMap<>(object.inventoryFacts());
        if (menuPatchTemplate != null) {
            inventoryFacts.putAll(menuPatchTemplate.inventoryFacts());
        }
        List<CorpusEvidenceRef> corpusEvidence = corpusEvidenceForMod.stream()
            .filter(ref -> ref.relatedBridgeKind() != null && runtimeBridgeKinds.contains(ref.relatedBridgeKind()))
            .toList();

        return new CompiledCompatibilityPlan(
            object.modId(),
            object.contentType(),
            object.javaIdentifier(),
            resolvedIdentifier,
            object.overallLevel(),
            object.overallStatus(),
            object.overallScore(),
            object.confidence(),
            object.adapterBindings(),
            object.runtimeRequirements(),
            runtimeBridgeKinds,
            inventoryFacts,
            allowsCreativeExposure,
            creativeExposureReason,
            allowsCustomRegistration,
            customRegistrationReason,
            CompatibilityDecisions.supportsBlockItemTextureFallback(object),
            CompatibilityDecisions.shouldApplyBlockPlacementBridge(object, block),
            supportsWearablePresentation,
            supportsAttachablePresentation,
            requiresMenuBridge,
            menuFallbackContainerType(object, javaIdentifier, mappingResolver),
            interactionPrompt,
            menuRuntimeRequirements,
            blockEntityRuntimeRequirements,
            fluidRuntimeRequirements,
            blockEntityPatchTemplate(object, javaIdentifier, mappingResolver),
            !blockEntityRuntimeRequirements.isEmpty(),
            !fluidRuntimeRequirements.isEmpty(),
            behaviorLevel,
            behaviorTag,
            corpusEvidence
        );
    }

    @NotNull
    private static List<String> bridgeRequirements(@NotNull List<RuntimeBridgeKind> runtimeBridgeKinds, @NotNull String contentType) {
        List<RuntimeBridgeKind> filteredKinds = runtimeBridgeKinds.stream()
            .filter(kind -> kind.contentType().equals(contentType))
            .toList();
        return RuntimeBridgeKind.requirementIds(filteredKinds);
    }

    @NotNull
    private static java.util.Comparator<CompiledCompatibilityPlan> planComparator() {
        return java.util.Comparator
            .comparing(CompiledCompatibilityPlan::modId)
            .thenComparing(CompiledCompatibilityPlan::javaIdentifier);
    }

    private static void compileBlockStatePlans(
        @NotNull CompatibilityObject object,
        @NotNull MappingResolver mappingResolver,
        @NotNull Map<String, List<MappingResolver.ResolvedBlockDefinition>> blockDefinitionsByIdentifier,
        @NotNull Map<String, MappingResolver.ResolvedBlockState> blockStatesByIdentifierAndState
    ) {
        if (!"block".equals(object.contentType())) {
            return;
        }

        Identifier javaIdentifier = Identifier.parse(object.javaIdentifier());
        Block block = BuiltInRegistries.BLOCK.getValue(javaIdentifier);
        if (block == null) {
            return;
        }

        Map<Identifier, List<net.minecraft.world.level.block.state.BlockState>> groupedStates = new LinkedHashMap<>();
        Map<Identifier, Boolean> overridden = new LinkedHashMap<>();
        for (net.minecraft.world.level.block.state.BlockState state : block.getStateDefinition().getPossibleStates()) {
            MappingResolver.ResolvedBlockState resolvedState = mappingResolver.resolveBlockState(javaIdentifier, state);
            blockStatesByIdentifierAndState.put(blockStateKey(object.javaIdentifier(), Block.getId(state)), resolvedState);
            groupedStates.computeIfAbsent(resolvedState.identifier(), ignored -> new ArrayList<>()).add(state);
            overridden.merge(resolvedState.identifier(), resolvedState.overridden(), Boolean::logicalOr);
        }

        List<MappingResolver.ResolvedBlockDefinition> definitions = new ArrayList<>();
        for (Map.Entry<Identifier, List<net.minecraft.world.level.block.state.BlockState>> entry : groupedStates.entrySet()) {
            definitions.add(new MappingResolver.ResolvedBlockDefinition(entry.getKey(), List.copyOf(entry.getValue()), overridden.getOrDefault(entry.getKey(), false)));
        }
        blockDefinitionsByIdentifier.put(object.javaIdentifier(), List.copyOf(definitions));
    }

    @NotNull
    private static String blockStateKey(@NotNull String javaIdentifier, int stateId) {
        return javaIdentifier + '|' + stateId;
    }

    @Nullable
    private static ContainerType menuFallbackContainerType(
        @NotNull CompatibilityObject object,
        @NotNull Identifier javaIdentifier,
        @NotNull MappingResolver mappingResolver
    ) {
        if (!"menu".equals(object.contentType())) {
            return null;
        }

        MenuPatchTemplate template = mappingResolver.menuPatchTemplate(javaIdentifier);
        return template != null ? template.fallbackContainerType() : null;
    }

    @Nullable
    private static BlockEntityPatchTemplate blockEntityPatchTemplate(
        @NotNull CompatibilityObject object,
        @NotNull Identifier javaIdentifier,
        @NotNull MappingResolver mappingResolver
    ) {
        if (!"block_entity".equals(object.contentType())) {
            return null;
        }
        return mappingResolver.blockEntityPatchTemplate(javaIdentifier);
    }

    @NotNull
    private static String key(@NotNull String left, @NotNull String right) {
        return left + "|" + right;
    }

    private static boolean supportsWearablePresentation(@NotNull CompatibilityObject object, @Nullable Item item) {
        if (isUnsupported(object, "content") || isUnsupported(object, "presentation")) {
            return false;
        }
        return hasBehaviorTag(object, "wearable", "wearable_attachable") || hasEquippableAsset(item);
    }

    private static boolean supportsAttachablePresentation(@NotNull CompatibilityObject object, @Nullable Item item) {
        if (isUnsupported(object, "content") || isUnsupported(object, "presentation")) {
            return false;
        }
        return hasBehaviorTag(object, "chargeable_bow", "bow_attachable") || item instanceof BowItem || !isUnsupported(object, "behavior");
    }

    private static boolean allowsItemCreativeExposure(
        @NotNull CompatibilityObject object,
        @Nullable Item item,
        boolean supportsWearablePresentation,
        boolean supportsAttachablePresentation,
        boolean requiresItemBehaviorBridge
    ) {
        if (!CompatibilityDecisions.allowsCustomItemRegistration(object, item)) {
            return false;
        }

        if (requiresItemBehaviorBridge) {
            return false;
        }

        SupportResult behavior = object.supportResults().get("behavior");
        if (behavior == null || (behavior.level() != SupportLevel.UNSUPPORTED && behavior.level() != SupportLevel.APPROXIMATED)) {
            return true;
        }

        return supportsWearablePresentation || supportsAttachablePresentation;
    }

    @Nullable
    private static String itemCreativeExposureReason(@NotNull CompatibilityObject object, boolean allowsItemCreativeExposure, boolean requiresItemBehaviorBridge) {
        if (allowsItemCreativeExposure) {
            return null;
        }

        if (!CompatibilityDecisions.allowsCustomItemRegistration(object)) {
            return "content or presentation support is insufficient";
        }

        if (requiresItemBehaviorBridge) {
            return behaviorReason("item behavior runtime bridge is required", object);
        }

        SupportResult behavior = object.supportResults().get("behavior");
        if (behavior != null && behavior.level() == SupportLevel.UNSUPPORTED) {
            return behaviorReason("behavior domain is unsupported", object);
        }
        if (behavior != null && behavior.level() == SupportLevel.APPROXIMATED) {
            return behaviorReason("behavior domain is approximated", object);
        }
        return "compatibility evidence is insufficient";
    }

    @Nullable
    private static String behaviorReason(@NotNull String defaultReason, @NotNull CompatibilityObject object) {
        String behaviorTag = object.inventoryFacts().get("behavior_tag");
        if (behaviorTag == null || behaviorTag.isBlank()) {
            return defaultReason;
        }
        return defaultReason + " (tag: " + behaviorTag + ")";
    }

    private static boolean isUnsupported(@NotNull CompatibilityObject object, @NotNull String domain) {
        SupportResult supportResult = object.supportResults().get(domain);
        return supportResult != null && supportResult.level() == SupportLevel.UNSUPPORTED;
    }

    private static boolean hasBehaviorTag(@NotNull CompatibilityObject object, @NotNull String... tags) {
        String behaviorTag = object.inventoryFacts().get("behavior_tag");
        if (behaviorTag == null || behaviorTag.isBlank()) {
            return false;
        }

        for (String tag : tags) {
            if (behaviorTag.equals(tag)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasEquippableAsset(@Nullable Item item) {
        if (item == null) {
            return false;
        }

        try {
            return item.components().has(DataComponents.EQUIPPABLE)
                && item.components().get(DataComponents.EQUIPPABLE).assetId().isPresent();
        } catch (NullPointerException ignored) {
            return false;
        }
    }

    @NotNull
    public org.geysermc.hydraulic.pack.PerformanceReport.RuntimeDispatchMetrics metrics() {
        return new org.geysermc.hydraulic.pack.PerformanceReport.RuntimeDispatchMetrics(
            this.cacheMetrics("block"),
            this.cacheMetrics("item"),
            this.cacheMetrics("entity"),
            this.cacheMetrics("menu"),
            this.cacheMetrics("block_entity"),
            this.cacheMetrics("fluid")
        );
    }

    private void recordLookup(@NotNull String contentType, boolean hit) {
        LookupCounters counters = this.countersByType.get(contentType);
        if (counters == null) {
            return;
        }
        counters.record(hit);
    }

    @NotNull
    private org.geysermc.hydraulic.pack.PerformanceReport.CacheMetrics cacheMetrics(@NotNull String contentType) {
        LookupCounters counters = this.countersByType.get(contentType);
        return counters == null
            ? new org.geysermc.hydraulic.pack.PerformanceReport.CacheMetrics(0, 0)
            : new org.geysermc.hydraulic.pack.PerformanceReport.CacheMetrics(counters.hits(), counters.misses());
    }

    private static final class LookupCounters {
        private final AtomicLong hits = new AtomicLong();
        private final AtomicLong misses = new AtomicLong();

        private void record(boolean hit) {
            if (hit) {
                this.hits.incrementAndGet();
            } else {
                this.misses.incrementAndGet();
            }
        }

        private long hits() {
            return this.hits.get();
        }

        private long misses() {
            return this.misses.get();
        }
    }
}