package org.geysermc.hydraulic.pack;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.Equippable;
import org.geysermc.event.Event;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.hydraulic.cache.ArtifactCache;
import org.geysermc.hydraulic.companion.CompanionManager;
import org.geysermc.hydraulic.cache.ConversionKey;
import org.geysermc.hydraulic.Constants;
import org.geysermc.hydraulic.HydraulicImpl;
import org.geysermc.hydraulic.block.StateDefinition;
import org.geysermc.hydraulic.compat.CompatibilityManager;
import org.geysermc.hydraulic.compat.CompatibilityRegistry;
import org.geysermc.hydraulic.compat.CompatibilityReport;
import org.geysermc.hydraulic.compat.MappingResolver;
import org.geysermc.hydraulic.compat.runtime.RuntimeLifecycleCoordinator;
import org.geysermc.hydraulic.compat.adapter.AdapterCatalog;
import org.geysermc.hydraulic.compat.adapter.AdapterCatalogCache;
import org.geysermc.hydraulic.compat.corpus.AddonCorpusLoader;
import org.geysermc.hydraulic.compat.corpus.CorpusBuiltinBootstrap;
import org.geysermc.hydraulic.compat.corpus.CorpusSnapshotImporter;
import org.geysermc.hydraulic.compat.corpus.CorpusReportWriter;
import org.geysermc.hydraulic.compat.corpus.java.JavaModCorpusBuiltinBootstrap;
import org.geysermc.hydraulic.compat.corpus.java.JavaModCorpusLoader;
import org.geysermc.hydraulic.compat.corpus.java.JavaModCorpusReportWriter;
import org.geysermc.hydraulic.compat.handoff.CompatibilityHandoffExporter;
import org.geysermc.hydraulic.compat.handoff.CompatibilityHandoffQueue;
import org.geysermc.hydraulic.compat.handoff.HandoffEnvelope;
import org.geysermc.hydraulic.metadata.MetadataIndex;
import org.geysermc.hydraulic.metadata.MetadataLoader;
import org.geysermc.hydraulic.item.EquipmentAssetLoader;
import org.geysermc.hydraulic.pack.index.UniversalResourceIndex;
import org.geysermc.hydraulic.pack.index.LazyBlockstateProvider;
import org.geysermc.hydraulic.pack.index.LazyItemDefinitionProvider;
import org.geysermc.hydraulic.pack.context.PackEventContext;
import org.geysermc.hydraulic.pack.context.PackPostProcessContext;
import org.geysermc.hydraulic.pack.context.PackPreProcessContext;
import org.geysermc.hydraulic.pack.converter.CustomModelConverter;
import org.geysermc.hydraulic.pack.modules.MetadataPackModule;
import org.geysermc.hydraulic.platform.mod.ModInfo;
import org.geysermc.hydraulic.util.PackUtil;
import org.geysermc.pack.converter.PackConverter;
import org.geysermc.pack.converter.pipeline.AssetConverters;
import org.geysermc.pack.converter.pipeline.ConverterPipeline;
import org.geysermc.pack.converter.type.model.ModelStitcher;
import org.geysermc.pack.converter.util.NioDirectoryFileTreeReader;
import org.geysermc.pack.converter.util.VanillaPackProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import team.unnamed.creative.ResourcePack;
import team.unnamed.creative.serialize.minecraft.MinecraftResourcePackReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeMap;

/**
 * Manages packs within Hydraulic. Most of the pack conversion
 * management is done within this class, and it is also responsible
 * for loading the packs onto the server.
 */
public class PackManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    static final Set<String> IGNORED_MODS = Set.of(
            // Fabric
            "geyser-fabric",
            "fabric-permissions-api-v0",

            // NeoForge
            "geyser-neoforge",
            "neoforge",
            "minecraft",

            // Common
            "floodgate",
            "mixinextras",
            "cloud"
    );

    private final HydraulicImpl hydraulic;
    private final Path vanillaPath;
    private final PerformanceReportTracker performanceTracker;
    private final PackValidationTracker packValidationTracker;
    private final ArtifactCache artifactCache;
    private final AdapterCatalogCache adapterCatalogCache;
    private final CompatibilityHandoffQueue handoffQueue;
    private final CompatibilityHandoffExporter handoffExporter;
    private final CompanionManager companionManager;
    private final AddonCorpusLoader corpusLoader;
    private final CorpusSnapshotImporter corpusImporter;
    private final CorpusReportWriter corpusReportWriter;
    private final JavaModCorpusLoader javaModCorpusLoader;
    private final JavaModCorpusReportWriter javaModCorpusReportWriter;
    private final TextureResolutionCache textureResolutionCache = new TextureResolutionCache();
    private final PackValidator packValidator = new PackValidator();
    private final List<PackModule<?>> modules = new ArrayList<>();

    private final ListMultimap<String, ModInfo> namespacesToMods = MultimapBuilder.hashKeys().arrayListValues(1).build();
    private final ListMultimap<String, Identifier> modsToBlocks = MultimapBuilder.hashKeys().arrayListValues().build();
    private final ListMultimap<String, Identifier> modsToItems = MultimapBuilder.hashKeys().arrayListValues().build();
    private final Map<String, ModResourceIndex> modResourceIndexes = new LinkedHashMap<>();

    private MetadataIndex metadataIndex = MetadataIndex.empty();
    private CompatibilityRegistry compatibilityRegistry = CompatibilityRegistry.empty();
    private CompatibilityManager compatibilityManager;
    private UniversalResourceIndex universalResourceIndex;
    private LazyBlockstateProvider lazyBlockstateProvider;
    private LazyItemDefinitionProvider lazyItemDefinitionProvider;
    private long indexCacheHits;
    private long indexCacheMisses;
    private long compatibilityCacheHits;
    private long compatibilityCacheMisses;
    private long conversionCacheHits;
    private long conversionCacheMisses;
    private long validationCacheHits;
    private long validationCacheMisses;
    private final ConcurrentMap<String, TextureDependencyGraph> activeTextureDependencies = new ConcurrentHashMap<>();

    private List<ConverterPipeline<?, ?>> packConverters;
    private ModelStitcher.Provider modelProvider;

    public PackManager(HydraulicImpl hydraulic) {
        this.hydraulic = hydraulic;
        this.vanillaPath = hydraulic.dataFolder(Constants.MOD_ID).resolve("cache/vanilla-assets.zip");
        this.performanceTracker = new PerformanceReportTracker(LOGGER, hydraulic.dataFolder(Constants.MOD_ID).resolve("reports/performance-report.json"));
        this.packValidationTracker = new PackValidationTracker(LOGGER, hydraulic.dataFolder(Constants.MOD_ID).resolve("reports/pack-validation-report.json"));
        this.artifactCache = new ArtifactCache(LOGGER, hydraulic.dataFolder(Constants.MOD_ID).resolve("cache"));
        this.adapterCatalogCache = new AdapterCatalogCache(LOGGER, hydraulic.dataFolder(Constants.MOD_ID).resolve("cache"));
        Path cachePath = hydraulic.dataFolder(Constants.MOD_ID).resolve("cache");
        this.handoffQueue = new CompatibilityHandoffQueue(LOGGER, cachePath);
        this.handoffExporter = new CompatibilityHandoffExporter(LOGGER, this.handoffQueue, cachePath);
        Path dataPath = hydraulic.dataFolder(Constants.MOD_ID);
        this.companionManager = new CompanionManager(LOGGER, dataPath);
        this.corpusLoader = new AddonCorpusLoader(LOGGER, dataPath);
        this.corpusImporter = new CorpusSnapshotImporter(LOGGER, this.corpusLoader);
        this.corpusReportWriter = new CorpusReportWriter(LOGGER, dataPath);
        this.javaModCorpusLoader = new JavaModCorpusLoader(LOGGER, dataPath);
        this.javaModCorpusReportWriter = new JavaModCorpusReportWriter(LOGGER, dataPath);
    }

    /**
     * Initializes the pack manager.
     */
    public void initialize() {
        this.artifactCache.ensureLayout();
        this.adapterCatalogCache.ensureLayout();
        this.handoffQueue.ensureLayout();
        this.handoffQueue.loadQueueState();
        this.handoffExporter.scheduleRetryProcessing();
        this.companionManager.initialize();
        this.corpusLoader.ensureLayout();
        CorpusBuiltinBootstrap.installBuiltinEntries(LOGGER, this.hydraulic.dataFolder(Constants.MOD_ID).resolve("corpus"));
        this.corpusLoader.loadIndex();
        this.corpusLoader.refreshIndexFromSnapshots();
        this.corpusReportWriter.writeReports(this.corpusLoader.index(), this.corpusLoader.loadAdmissibleEntries(), this.corpusLoader.loadAllEntries());
        this.javaModCorpusLoader.ensureLayout();
        JavaModCorpusBuiltinBootstrap.installBuiltinEntries(LOGGER, this.hydraulic.dataFolder(Constants.MOD_ID).resolve("corpus").resolve("java"));
        this.javaModCorpusLoader.loadIndex();
        this.javaModCorpusLoader.refreshIndexFromSnapshots();
        this.javaModCorpusReportWriter.writeReport(this.javaModCorpusLoader.index(), this.javaModCorpusLoader.loadAdmissibleEntries());

        long resourceIndexStarted = System.nanoTime();
        LookupSummary lookupSummary = initializeModLookups();
        long indexedResourcesMillis = nanosToMillis(System.nanoTime() - resourceIndexStarted);
        this.artifactCache.storeIndexSnapshot(ArtifactCache.IndexSnapshot.from(this.hydraulic.mods(), this.modResourceIndexes));

        // Build universal resource index from mod indexes
        this.universalResourceIndex = UniversalResourceIndex.fromLookups(
            this.modResourceIndexes,
            this.namespacesToMods,
            this.modsToBlocks,
            this.modsToItems
        );

        // Initialize lazy resource providers
        this.lazyBlockstateProvider = new LazyBlockstateProvider(this.modResourceIndexes, LOGGER, 200);
        this.lazyItemDefinitionProvider = new LazyItemDefinitionProvider(this.modResourceIndexes, LOGGER, 200);

        long metadataLoadStarted = System.nanoTime();
        Path metadataPath = this.hydraulic.dataFolder(Constants.MOD_ID).resolve("metadata");
        org.geysermc.hydraulic.metadata.MetadataBuiltinBootstrap.installBuiltinMetadata(LOGGER, metadataPath);
        this.metadataIndex = new MetadataLoader(LOGGER).load(metadataPath);
        long metadataLoadMillis = nanosToMillis(System.nanoTime() - metadataLoadStarted);

        long compatibilityStarted = System.nanoTime();
        ArtifactCache.StartupCompatibilityKey startupCompatibilityKey = this.startupCompatibilityKey();
        initializeCompatibilityRegistry(startupCompatibilityKey);
        long compatibilityInitializationMillis = nanosToMillis(System.nanoTime() - compatibilityStarted);

        try {
            Files.createDirectories(this.getVanillaPath().getParent());
        } catch (IOException e) {
            LOGGER.error("Failed to create cache dir");
        }

        VanillaPackProvider.create(
                this.getVanillaPath(),
                SharedConstants.getCurrentVersion().id(),
                new PackLogListener(LOGGER)
        );

        final Collection<ModInfo> mods = this.hydraulic.mods();
        long modelProviderStarted = System.nanoTime();
        modelProvider = createModelProvider(this.modResourceIndexes, this.getVanillaPath());
        long modelIndexBuildMillis = nanosToMillis(System.nanoTime() - modelProviderStarted);

        this.loadModules();
        long resourcePackReadStarted = System.nanoTime();
        final Map<String, List<ResourcePack>> modPacks = Maps.newHashMapWithExpectedSize(mods.size());
        if (this.modules.stream().anyMatch(PackModule::requiresParsedPacks)) {
            for (final ModInfo mod : mods) {
                List<ResourcePack> packs = new ArrayList<>();
                for (Path root : mod.roots()) {
                    try {
                        ResourcePack pack = MinecraftResourcePackReader.minecraft().read(NioDirectoryFileTreeReader.read(root));
                        packs.add(pack);
                    } catch (Exception e) {
                        LOGGER.warn("Failed to read resource pack from mod '{}' at path '{}': {}",
                            mod.id(), root, e.getMessage());
                    }
                }
                if (!packs.isEmpty()) {
                    modPacks.put(mod.id(), packs);
                }
            }
        }
        long resourcePackReadMillis = nanosToMillis(System.nanoTime() - resourcePackReadStarted);

        this.performanceTracker.recordStartup(new PerformanceReport.StartupMetrics(
            indexedResourcesMillis,
            metadataLoadMillis,
            compatibilityInitializationMillis,
            resourcePackReadMillis,
            modelIndexBuildMillis,
            mods.size(),
            lookupSummary.modsWithAssetFiles(),
            lookupSummary.modsWithoutAssetFiles(),
            lookupSummary.namespaces(),
            lookupSummary.indexedBlockStates(),
            lookupSummary.indexedItemAssets(),
            lookupSummary.blockMatches(),
            lookupSummary.skippedBlocks(),
            lookupSummary.itemMatches(),
            lookupSummary.skippedItems(),
            lookupSummary.missingItemModelComponents(),
            this.metadataIndex.summary().fileCount(),
            this.metadataIndex.summary().ruleCount(),
            this.metadataIndex.summary().patchCount(),
            this.metadataIndex.summary().validationIssueCount()
        ));
        this.recordModelProviderMetrics();
        this.recordTextureResolutionMetrics();
        this.recordArtifactCacheMetrics();

        this.packConverters = new ArrayList<>(AssetConverters.converters(hydraulic.isDev()));
        this.packConverters.remove(AssetConverters.MANIFEST);

        for (PackModule<?> module : this.modules) {
            for (ModInfo mod : mods) {
                if (shouldIgnoreMod(mod)) {
                    continue;
                }

                if (!this.shouldConvertModAssets(mod)) {
                    continue;
                }

                if (module.hasPreProcessors()) {
                    try {
                        this.preProcessModule(module, mod, modPacks.getOrDefault(mod.id(), List.of()));
                    } catch (Throwable t) {
                        LOGGER.error("Failed to pre-process mod {} for module {}", mod.id(), module.getClass().getSimpleName(), t);
                    }
                }
            }
        }

        this.performanceTracker.recordModelResolutionCache(toPerformanceCacheMetrics(StateDefinition.cacheMetrics()));

        PackListener packListener = new PackListener(this.hydraulic, this);
        GeyserApi.api().eventBus().register(this.hydraulic, packListener);
        try {
            packListener.ensurePacksPrepared();
        } catch (Throwable t) {
            LOGGER.error("Failed to prepare Hydraulic resource packs during startup", t);
        }
    }

    private void loadModules() {
        for (PackModule<?> module : ServiceLoader.load(PackModule.class)) {
            this.modules.add(module);
            GeyserApi.api().eventBus().register(this.hydraulic, module);
            module.eventListeners().forEach((eventClass, listeners) ->
                GeyserApi.api().eventBus().subscribe(this.hydraulic, eventClass, this::callEvents)
            );
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void preProcessModule(@NotNull PackModule<?> rawModule, @NotNull ModInfo mod, @NotNull Collection<ResourcePack> packs) {
        PackModule module = rawModule;
        module.preProcess0(new PackPreProcessContext(this.hydraulic, mod, module, packs, modelProvider));
    }

    /**
     * Creates the pack for the given mod.
     *
     * @param mod the mod to create the pack for
     * @param packPath the path to the pack
     * @return {@code true} if the pack was created, {@code false} otherwise
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    PackCreationResult createPack(@NotNull ModInfo mod, @NotNull Path packPath) {
        ConversionKey conversionKey = this.conversionKey(mod);
        List<ConverterPipeline<?, ?>> pipelines = new ArrayList<>(packConverters);
        TextureDependencyGraph textureDependencies = new TextureDependencyGraph();
        ModResourceIndex resourceIndex = this.modResourceIndexes.get(mod.id());
        seedEquipmentTextureDependencies(mod, textureDependencies);
        replacePipeline(
            pipelines,
            AssetConverters.MODEL,
            AssetConverters.create(new CustomModelConverter(resourceIndex, modelProvider, textureDependencies), AssetConverters.MODEL, AssetConverters.MODEL)
        );
        replacePipeline(
            pipelines,
            AssetConverters.TEXTURE,
            AssetConverters.create(new SelectiveTextureExtractor(resourceIndex, textureDependencies), org.geysermc.pack.converter.type.texture.TextureConverter.INSTANCE)
        );
        pipelines.add(AssetConverters.create(new MetadataPackModule(mod, conversionKey)));
        this.activeTextureDependencies.put(mod.id(), textureDependencies);
        try {
            PackConverter converter = new PackConverter()
                    .packName(mod.name())
                    .logListener(new PackLogListener(LoggerFactory.getLogger(LOGGER.getName() + "/" + mod.id())))
                    .converters(pipelines)
                    .output(packPath)
                    .vanillaPackPath(vanillaPath)
                    .vanillaPackVersion(SharedConstants.getCurrentVersion().id())
                    .textureSubdirectory(mod.namespace())
                    .packageHandler(new PackPackager());

            converter.postProcessor((javaPack, bedrockPack) -> {
                for (PackModule<?> module : this.modules) {
                    PackPostProcessContext context = new PackPostProcessContext(this.hydraulic, mod, module, converter, javaPack, bedrockPack, packPath, modelProvider);
                    if (!module.test(context)) {
                        continue;
                    }

                    module.postProcess0(context);
                }
            });

            try {
                for (final Path root : mod.roots()) {
                    converter.input(root, false).convert();
                }
            } catch (IOException ex) {
                LOGGER.error("Failed to convert mod {} to pack", mod.id(), ex);
                PackValidationReport.ModValidation validation = this.packValidator.conversionFailed(
                    packPath,
                    "pack.conversion.failed",
                    "Pack conversion failed before export completed.",
                    "Inspect the conversion logs for this mod and resolve the reported asset or conversion errors."
                );
                this.packValidationTracker.record(mod.id(), validation);
                this.performanceTracker.recordModelResolutionCache(toPerformanceCacheMetrics(StateDefinition.cacheMetrics()));
                this.recordModelProviderMetrics();
                this.recordTextureResolutionMetrics();
                return new PackCreationResult(false, validation, textureDependencies.lastSelectionMetrics());
            }

            // Now export the pack
            try {
                converter.pack();
            } catch (IOException ex) {
                LOGGER.error("Failed to export pack for mod {}", mod.id(), ex);
                PackValidationReport.ModValidation validation = this.packValidator.conversionFailed(
                    packPath,
                    "pack.export.failed",
                    "Pack export failed before the generated archive could be finalized.",
                    "Inspect the packaging logs and ensure the generated pack path is writable and not locked."
                );
                this.packValidationTracker.record(mod.id(), validation);
                this.performanceTracker.recordModelResolutionCache(toPerformanceCacheMetrics(StateDefinition.cacheMetrics()));
                this.recordModelProviderMetrics();
                this.recordTextureResolutionMetrics();
                return new PackCreationResult(false, validation, textureDependencies.lastSelectionMetrics());
            }

            boolean created = Files.exists(packPath);
            PackValidationReport.ModValidation validation = this.packValidator.validate(packPath, expectedTextureOutputs(mod.id(), textureDependencies));
            this.packValidationTracker.record(mod.id(), validation);
            if (!validation.valid()) {
                LOGGER.warn(
                    "Generated pack for mod {} failed validation (errors={}, warnings={}, manualActions={})",
                    mod.id(),
                    validation.errorCount(),
                    validation.warningCount(),
                    validation.manualActionCount()
                );
            }
            this.performanceTracker.recordModelResolutionCache(toPerformanceCacheMetrics(StateDefinition.cacheMetrics()));
            this.recordModelProviderMetrics();
            this.recordTextureResolutionMetrics();
            return new PackCreationResult(created && validation.valid(), validation, textureDependencies.lastSelectionMetrics());
        } finally {
            this.activeTextureDependencies.remove(mod.id());
        }
    }

    private void callEvents(@NotNull Event event) {
        for (ModInfo mod : this.hydraulic.mods()) {
            if (shouldIgnoreMod(mod)) {
                continue;
            }

            this.callEvent(mod, event);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void callEvent(@NotNull ModInfo mod, @NotNull Event event) {
        for (PackModule<?> module : this.modules) {
            module.call(event.getClass(), new PackEventContext(this.hydraulic, mod, module, event, this.modelProvider));
        }
    }

    private LookupSummary initializeModLookups() {
        Map<String, ModResourceIndex> modResourceIndexes = this.modResourceIndexes;
        modResourceIndexes.clear();
        int modsWithAssetFiles = 0;
        int modsWithoutAssetFiles = 0;
        int indexedBlockStates = 0;
        int indexedItemAssets = 0;

        Map<String, ModResourceIndex> reusableIndexes = this.artifactCache.loadReusableIndexes(this.hydraulic.mods());
        modResourceIndexes.putAll(reusableIndexes);
        this.indexCacheHits += reusableIndexes.size();
        this.indexCacheMisses += Math.max(0, this.hydraulic.mods().size() - reusableIndexes.size());

        // Step 1: Index each mod's resource roots once, then map namespaces to owning mods
        final Multimap<String, ModInfo> namespacesToMods = this.namespacesToMods;
        namespacesToMods.clear();
        for (final ModInfo mod : hydraulic.mods()) {
            ModResourceIndex resourceIndex = modResourceIndexes.get(mod.id());
            if (resourceIndex == null) {
                resourceIndex = ModResourceIndex.create(mod, LOGGER);
                modResourceIndexes.put(mod.id(), resourceIndex);
            }
            indexedBlockStates += resourceIndex.blockStateCount();
            indexedItemAssets += resourceIndex.itemAssetCount();
            if (resourceIndex.hasAssetFiles()) {
                modsWithAssetFiles++;
            } else {
                modsWithoutAssetFiles++;
            }
            for (String namespace : resourceIndex.namespaces()) {
                if (!namespace.equals("minecraft")) {
                    namespacesToMods.put(namespace, mod);
                }
            }
        }

        // Step 2: Use namespace information to lookup which mods contain what blockstates
        final Multimap<String, Identifier> modsToBlocks = this.modsToBlocks;
        modsToBlocks.clear();
        int skippedBlocks = 0;
        for (final Identifier block : BuiltInRegistries.BLOCK.keySet()) {
            if (block.getNamespace().equals("minecraft")) continue;
            boolean found = false;
            for (final ModInfo mod : namespacesToMods.get(block.getNamespace())) {
                ModResourceIndex resourceIndex = modResourceIndexes.get(mod.id());
                if (resourceIndex != null && resourceIndex.hasBlockState(block)) {
                    modsToBlocks.put(mod.id(), block);
                    found = true;
                    break;
                }
            }
            if (!found) {
                skippedBlocks++;
            }
        }

        // Step 3: Use namespace information to lookup which mods contain item definitions or legacy item models
        // There's no ordering requirement between this and Step 2.
        final Multimap<String, Identifier> modsToItems = this.modsToItems;
        modsToItems.clear();
        int missingItemModelComponents = 0;
        int skippedItems = 0;
        for (final Identifier itemId : BuiltInRegistries.ITEM.keySet()) {
            if (itemId.getNamespace().equals("minecraft")) continue;

            Item item = BuiltInRegistries.ITEM.getValue(itemId);
            Identifier itemModel = item.components().get(DataComponents.ITEM_MODEL);
            // Item model is missing, can't do much here
            if (itemModel == null) {
                missingItemModelComponents++;
                continue;
            }

            boolean found = false;
            for (final ModInfo mod : namespacesToMods.get(itemId.getNamespace())) {
                ModResourceIndex resourceIndex = modResourceIndexes.get(mod.id());
                if (resourceIndex != null && resourceIndex.hasItemAsset(itemModel)) {
                    modsToItems.put(mod.id(), itemId);
                    found = true;
                    break;
                }
            }

            if (!found) {
                skippedItems++;
            }
        }

        LOGGER.info(
            "Indexed mod resources for Hydraulic startup (mods={}, namespaces={}, blockMatches={}, skippedBlocks={}, itemMatches={}, skippedItems={}, missingItemModels={})",
            modResourceIndexes.size(),
            namespacesToMods.keySet().size(),
            modsToBlocks.size(),
            skippedBlocks,
            modsToItems.size(),
            skippedItems,
            missingItemModelComponents
        );

        return new LookupSummary(
            modResourceIndexes.size(),
            modsWithAssetFiles,
            modsWithoutAssetFiles,
            namespacesToMods.keySet().size(),
            indexedBlockStates,
            indexedItemAssets,
            modsToBlocks.size(),
            skippedBlocks,
            modsToItems.size(),
            skippedItems,
            missingItemModelComponents
        );
    }

    private void initializeCompatibilityRegistry(@NotNull ArtifactCache.StartupCompatibilityKey cacheKey) {
        Path dataPath = this.hydraulic.dataFolder(Constants.MOD_ID);
        Path metadataPath = dataPath.resolve("metadata");
        this.compatibilityManager = new CompatibilityManager(LOGGER, dataPath, this.corpusLoader.loadAdmissibleEntries());
        ArtifactCache.CompatibilitySnapshot cached = this.artifactCache.loadCompatibilitySnapshot(cacheKey);
        if (cached != null) {
            this.compatibilityCacheHits++;
            this.compatibilityRegistry = new CompatibilityRegistry(this.metadataIndex, new MappingResolver(this.metadataIndex), cached.inventory(), cached.report());
        } else {
            this.compatibilityCacheMisses++;
            this.compatibilityRegistry = this.compatibilityManager.initialize(
                this.hydraulic.mods(),
                this.namespacesToMods,
                this.modsToBlocks,
                this.modsToItems,
                this.modResourceIndexes,
                this.metadataIndex,
                this::shouldIgnoreMod
            );
            this.artifactCache.storeCompatibilitySnapshot(new ArtifactCache.CompatibilitySnapshot(
                compatibilityManifest(cacheKey),
                this.compatibilityRegistry.inventory(),
                this.compatibilityRegistry.report()
            ));
        }
        RuntimeLifecycleCoordinator.install(this.compatibilityRegistry);

        if (!this.metadataIndex.isEmpty()) {
            LOGGER.info(
                "Loaded structural metadata overrides from {} (files={}, blockMappings={}, itemMappings={}, recipeMappings={}, entityMappings={}, menuMappings={}, patches={}, rules={}, validationIssues={})",
                metadataPath,
                this.metadataIndex.summary().fileCount(),
                this.metadataIndex.summary().blockMappingCount(),
                this.metadataIndex.summary().itemMappingCount(),
                this.metadataIndex.summary().recipeMappingCount(),
                this.metadataIndex.summary().entityMappingCount(),
                this.metadataIndex.summary().menuMappingCount(),
                this.metadataIndex.summary().patchCount(),
                this.metadataIndex.summary().ruleCount(),
                this.metadataIndex.summary().validationIssueCount()
            );
        }
    }

    void recordPackConversionMetrics(@NotNull PerformanceReport.PackConversionMetrics metrics) {
        this.performanceTracker.recordPackConversion(metrics);
    }

    @NotNull
    ConversionKey conversionKey(@NotNull ModInfo mod) {
        ModResourceIndex resourceIndex = this.modResourceIndexes.get(mod.id());
        if (resourceIndex == null) {
            resourceIndex = ModResourceIndex.create(mod, LOGGER);
            this.modResourceIndexes.put(mod.id(), resourceIndex);
        }
        return PackUtil.conversionKey(mod, resourceIndex, this.modResourceIndexes, this.metadataIndex);
    }

    /**
     * Merges the current pack-validation findings into the compatibility report and rewrites
     * {@code compatibility-report.json}, so manual actions and validation issues are visible
     * alongside compatibility findings instead of only in the sibling pack-validation artifact.
     */
    void syncCompatibilityValidation() {
        PackValidationReport validationReport = this.packValidationTracker.snapshot();
        if (validationReport.perMod().isEmpty()) {
            return;
        }

        Map<String, CompatibilityReport.PackValidationSummary> summaries = new LinkedHashMap<>();
        for (Map.Entry<String, PackValidationReport.ModValidation> entry : validationReport.perMod().entrySet()) {
            PackValidationReport.ModValidation validation = entry.getValue();
            summaries.put(entry.getKey(), new CompatibilityReport.PackValidationSummary(
                validation.valid(),
                validation.errorCount(),
                validation.warningCount(),
                validation.manualActionCount(),
                validation.manualActions()
            ));
        }

        CompatibilityReport updatedReport = this.compatibilityRegistry.report().withPackValidation(summaries);
        this.compatibilityRegistry = this.compatibilityRegistry.withReport(updatedReport);
        if (this.compatibilityManager != null) {
            this.compatibilityManager.writeReport(updatedReport);
        }
        this.artifactCache.storeValidationArtifact(validationReport, this.startupCompatibilityKey().value());
        this.recordArtifactCacheMetrics();

        // Enqueue compatibility report for handoff (non-blocking)
        enqueueCompatibilityHandoff(updatedReport);
    }

    private void enqueueCompatibilityHandoff(@NotNull CompatibilityReport report) {
        try {
            ArtifactCache.CompatibilityManifest manifest = this.compatibilityManifest(this.startupCompatibilityKey());
            HandoffEnvelope envelope = HandoffEnvelope.create(
                manifest,
                this.compatibilityRegistry.inventory(),
                report,
                Constants.VERSION,
                SharedConstants.getCurrentVersion().id(),
                resolveBedrockProtocolVersion(),
                resolveGeyserVersion()
            );
            if (this.handoffQueue.enqueue(report, envelope)) {
                LOGGER.info("Enqueued compatibility report for handoff (trackingId={})", envelope.trackingId());
                this.handoffExporter.scheduleRetryProcessing();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to enqueue compatibility report for handoff", e);
        }
    }

    @NotNull
    PerformanceReport performanceReport() {
        return this.performanceTracker.snapshot();
    }

    void recordArtifactCacheMetrics(@NotNull PerformanceReport.ArtifactCacheMetrics metrics) {
        this.performanceTracker.recordArtifactCache(metrics);
    }

    public void recordRuntimeDispatchMetrics() {
        this.performanceTracker.recordRuntimeDispatch(this.compatibilityRegistry.dispatchTable().metrics());
    }

    public void recordModelProviderMetrics() {
        if (this.modelProvider instanceof IndexedModelProvider provider) {
            IndexedModelProvider.CacheMetrics metrics = provider.cacheMetrics();
            this.performanceTracker.recordModelProviderCache(new PerformanceReport.ModelProviderMetrics(
                metrics.hits(),
                metrics.misses(),
                metrics.evictions(),
                metrics.size(),
                metrics.indexedModels()
            ));
        }
    }

    public void recordTextureResolutionMetrics() {
        TextureResolutionCache.CacheMetrics metrics = this.textureResolutionCache.metrics();
        this.performanceTracker.recordTextureResolutionCache(new PerformanceReport.TextureResolutionMetrics(
            metrics.hits(),
            metrics.misses(),
            metrics.evictions(),
            metrics.size()
        ));
    }

    void recordConversionCacheUsage(long hits, long misses) {
        this.conversionCacheHits += hits;
        this.conversionCacheMisses += misses;
        this.recordArtifactCacheMetrics();
    }

    @NotNull
    ArtifactCache artifactCache() {
        return this.artifactCache;
    }

    public boolean shouldIncludeTexture(@NotNull String modId, @NotNull net.kyori.adventure.key.Key textureKey) {
        TextureDependencyGraph dependencies = this.activeTextureDependencies.get(modId);
        return dependencies == null || dependencies.shouldInclude(textureKey);
    }

    @Nullable
    public Set<net.kyori.adventure.key.Key> selectedTextures(@NotNull String modId) {
        TextureDependencyGraph dependencies = this.activeTextureDependencies.get(modId);
        if (dependencies == null) {
            return null;
        }

        if (!dependencies.lastSelectedTextures().isEmpty() || !dependencies.requiredTextures().isEmpty()) {
            return dependencies.lastSelectedTextures();
        }
        return null;
    }

    @Nullable
    public ModResourceIndex modResourceIndex(@NotNull String modId) {
        return this.modResourceIndexes.get(modId);
    }

    @NotNull
    TextureResolutionCache textureResolutionCache() {
        return this.textureResolutionCache;
    }

    private void recordArtifactCacheMetrics() {
        this.performanceTracker.recordArtifactCache(new PerformanceReport.ArtifactCacheMetrics(
            new PerformanceReport.CacheMetrics(this.indexCacheHits, this.indexCacheMisses),
            new PerformanceReport.CacheMetrics(this.compatibilityCacheHits, this.compatibilityCacheMisses),
            new PerformanceReport.CacheMetrics(this.conversionCacheHits, this.conversionCacheMisses),
            new PerformanceReport.CacheMetrics(this.validationCacheHits, this.validationCacheMisses)
        ));
    }

    @NotNull
    ArtifactCache.StartupCompatibilityKey startupCompatibilityKey() {
        TreeMap<String, String> fingerprints = new TreeMap<>();
        for (Map.Entry<String, ModResourceIndex> entry : this.modResourceIndexes.entrySet()) {
            fingerprints.put(entry.getKey(), entry.getValue().fingerprint().stableValue());
        }
        String metadataFingerprint = PackUtil.metadataFingerprint(this.metadataIndex);
        String engineFingerprint = PackUtil.compatibilityEngineFingerprint() + ":corpus=" + this.corpusLoader.fingerprint();
        AdapterCatalog adapterCatalog = this.adapterCatalogCache.loadCatalog();
        String adapterCatalogFingerprint = adapterCatalog != null ? adapterCatalog.fingerprint() : PackUtil.adapterCatalogFingerprint();
        return new ArtifactCache.StartupCompatibilityKey(PackUtil.startupCompatibilityFingerprint(metadataFingerprint, fingerprints, engineFingerprint, adapterCatalogFingerprint));
    }

    @NotNull
    private ArtifactCache.CompatibilityManifest compatibilityManifest(@NotNull ArtifactCache.StartupCompatibilityKey cacheKey) {
        Map<String, String> fingerprints = new TreeMap<>();
        for (Map.Entry<String, ModResourceIndex> entry : this.modResourceIndexes.entrySet()) {
            fingerprints.put(entry.getKey(), entry.getValue().fingerprint().stableValue());
        }
        AdapterCatalog adapterCatalog = this.adapterCatalogCache.loadCatalog();
        String adapterCatalogFingerprint = adapterCatalog != null ? adapterCatalog.fingerprint() : PackUtil.adapterCatalogFingerprint();
        return new ArtifactCache.CompatibilityManifest(
            cacheKey,
            PackUtil.metadataFingerprint(this.metadataIndex),
            PackUtil.compatibilityEngineFingerprint() + ":corpus=" + this.corpusLoader.fingerprint(),
            adapterCatalogFingerprint,
            fingerprints.size(),
            fingerprints
        );
    }

    private static long nanosToMillis(long nanos) {
        return nanos / 1_000_000L;
    }

    record PackCreationResult(boolean success, @NotNull PackValidationReport.ModValidation validation, @NotNull TextureDependencyGraph.SelectionMetrics textureSelection) {
    }

    private void seedEquipmentTextureDependencies(@NotNull ModInfo mod, @NotNull TextureDependencyGraph textureDependencies) {
        for (Identifier itemId : this.modsToItems.get(mod.id())) {
            Item item = BuiltInRegistries.ITEM.getValue(itemId);
            if (item == null || !item.components().has(DataComponents.EQUIPPABLE)) {
                continue;
            }

            Equippable equippable = item.components().get(DataComponents.EQUIPPABLE);
            if (equippable == null || equippable.assetId().isEmpty()) {
                continue;
            }

            Identifier assetId = equippable.assetId().map(ResourceKey::identifier).orElse(null);
            if (assetId == null) {
                continue;
            }

            EquipmentAssetLoader.collectTextureDependencies(mod, assetId, LOGGER, (layerType, textureKey) -> textureDependencies.recordEquipmentTexture(assetId + "/" + layerType.name().toLowerCase(java.util.Locale.ROOT), textureKey));
        }
    }

    private static void replacePipeline(
        @NotNull List<ConverterPipeline<?, ?>> pipelines,
        @NotNull ConverterPipeline<?, ?> existing,
        @NotNull ConverterPipeline<?, ?> replacement
    ) {
        int index = pipelines.indexOf(existing);
        if (index >= 0) {
            pipelines.set(index, replacement);
            return;
        }
        pipelines.add(replacement);
    }

    @NotNull
    private static PerformanceReport.CacheMetrics toPerformanceCacheMetrics(@NotNull StateDefinition.CacheMetrics cacheMetrics) {
        return new PerformanceReport.CacheMetrics(cacheMetrics.hits(), cacheMetrics.misses());
    }

    @NotNull
    private PackValidator.TextureExpectations expectedTextureOutputs(@NotNull String modId, @NotNull TextureDependencyGraph textureDependencies) {
        if (textureDependencies.lastSelectedTextures().isEmpty()) {
            return PackValidator.TextureExpectations.empty();
        }

        Set<String> archiveEntries = new LinkedHashSet<>();
        for (net.kyori.adventure.key.Key textureKey : textureDependencies.lastSelectedTextures()) {
            archiveEntries.add(this.textureResolutionCache.resolveModelOutput(modId, textureKey));
        }
        return PackValidator.TextureExpectations.ofArchiveEntries(archiveEntries);
    }

    private record LookupSummary(
        int indexedMods,
        int modsWithAssetFiles,
        int modsWithoutAssetFiles,
        int namespaces,
        int indexedBlockStates,
        int indexedItemAssets,
        int blockMatches,
        int skippedBlocks,
        int itemMatches,
        int skippedItems,
        int missingItemModelComponents
    ) {
    }

    /**
     * Creates a {@link ModelStitcher.Provider} that first searches mods, then the Vanilla pack.
     *
     * @param modResourceIndexes The indexed mod resource metadata to search through.
     * @return A {@link ModelStitcher.Provider} that searches through mods and the Vanilla pack.
     */
    private static ModelStitcher.Provider createModelProvider(
        Map<String, ModResourceIndex> modResourceIndexes,
        Path vanillaPath
    ) {
        Map<net.kyori.adventure.key.Key, Path> indexedModels = new LinkedHashMap<>();
        for (ModResourceIndex index : modResourceIndexes.values()) {
            for (Map.Entry<net.kyori.adventure.key.Key, Path> entry : index.modelPaths().entrySet()) {
                indexedModels.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        return new IndexedModelProvider(LOGGER, indexedModels, vanillaPath);
    }

    public boolean shouldIgnoreMod(ModInfo mod) {
        return IGNORED_MODS.contains(mod.id()) || hydraulic.getConfig().ignoredMods().contains(mod.id());
    }

    boolean shouldConvertModAssets(@NotNull ModInfo mod) {
        ModResourceIndex resourceIndex = this.modResourceIndexes.get(mod.id());
        return resourceIndex != null && resourceIndex.hasAssetFiles();
    }

    public ListMultimap<String, ModInfo> getNamespacesToMods() {
        return namespacesToMods;
    }

    public ListMultimap<String, Identifier> getModsToBlocks() {
        return modsToBlocks;
    }

    public ListMultimap<String, Identifier> getModsToItems() {
        return modsToItems;
    }

    public Path getVanillaPath() {
        return vanillaPath;
    }

    @NotNull
    public MetadataIndex metadataIndex() {
        return this.metadataIndex;
    }

    @NotNull
    public CompatibilityRegistry compatibilityRegistry() {
        return this.compatibilityRegistry;
    }

    @NotNull
    public CompanionManager companionManager() {
        return this.companionManager;
    }

    /**
     * Installs the Java-side companion detection signal now that the server has
     * started, and writes the final companion report. Must be called once from
     * server-starting lifecycle code, after {@link #initialize()}.
     */
    public void installCompanionSignal(@NotNull MinecraftServer server) {
        this.companionManager.installSignalBridge(server);
    }

    @NotNull
    public MappingResolver mappingResolver() {
        return this.compatibilityRegistry.mappingResolver();
    }

    @NotNull
    public UniversalResourceIndex universalResourceIndex() {
        return this.universalResourceIndex;
    }

    @NotNull
    public LazyBlockstateProvider lazyBlockstateProvider() {
        return this.lazyBlockstateProvider;
    }

    @NotNull
    public LazyItemDefinitionProvider lazyItemDefinitionProvider() {
        return this.lazyItemDefinitionProvider;
    }

    @NotNull
    public CompatibilityHandoffQueue handoffQueue() {
        return this.handoffQueue;
    }

    @NotNull
    public AddonCorpusLoader corpusLoader() {
        return this.corpusLoader;
    }

    @NotNull
    public CorpusSnapshotImporter corpusImporter() {
        return this.corpusImporter;
    }

    public void recordLazyResourceProviderMetrics() {
        if (this.lazyBlockstateProvider != null) {
            LazyBlockstateProvider.CacheMetrics blockstateMetrics = this.lazyBlockstateProvider.cacheMetrics();
            this.performanceTracker.recordBlockstateProviderCache(new PerformanceReport.LazyResourceProviderMetrics(
                blockstateMetrics.hits(),
                blockstateMetrics.misses(),
                blockstateMetrics.size(),
                blockstateMetrics.maxSize()
            ));
        }
        if (this.lazyItemDefinitionProvider != null) {
            LazyItemDefinitionProvider.CacheMetrics itemMetrics = this.lazyItemDefinitionProvider.cacheMetrics();
            this.performanceTracker.recordItemDefinitionProviderCache(new PerformanceReport.LazyResourceProviderMetrics(
                itemMetrics.hits(),
                itemMetrics.misses(),
                itemMetrics.size(),
                itemMetrics.maxSize()
            ));
        }
    }

    @NotNull
    private static String resolveGeyserVersion() {
        try {
            return String.valueOf(GeyserApi.api().geyserApiVersion());
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    @NotNull
    private static String resolveBedrockProtocolVersion() {
        try {
            return "geyser-" + GeyserApi.api().geyserApiVersion();
        } catch (Throwable ignored) {
            return "unknown";
        }
    }
}
