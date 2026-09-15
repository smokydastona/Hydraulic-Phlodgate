package org.geysermc.hydraulic.util;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.mojang.logging.LogUtils;
import net.kyori.adventure.key.Key;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import org.geysermc.hydraulic.Constants;
import org.geysermc.hydraulic.cache.ConversionKey;
import org.geysermc.hydraulic.compat.CompatibilityManager;
import org.geysermc.hydraulic.compat.analysis.AnalyzerRegistry;
import org.geysermc.hydraulic.compat.analysis.BlockAnalyzer;
import org.geysermc.hydraulic.compat.analysis.BlockEntityAnalyzer;
import org.geysermc.hydraulic.compat.analysis.EntityAnalyzer;
import org.geysermc.hydraulic.compat.analysis.FluidAnalyzer;
import org.geysermc.hydraulic.compat.analysis.ItemAnalyzer;
import org.geysermc.hydraulic.compat.analysis.MenuAnalyzer;
import org.geysermc.hydraulic.compat.analysis.RecipeAnalyzer;
import org.geysermc.hydraulic.compat.adapter.CapabilityAdapterRegistry;
import org.geysermc.hydraulic.compat.mapping.ContentPatch;
import org.geysermc.hydraulic.metadata.BlockMapping;
import org.geysermc.hydraulic.metadata.IdentifierMapping;
import org.geysermc.hydraulic.metadata.MetadataIndex;
import org.geysermc.hydraulic.metadata.MetadataValidationIssue;
import org.geysermc.hydraulic.pack.ModResourceIndex;
import org.geysermc.hydraulic.platform.mod.ModInfo;
import org.geysermc.pack.converter.type.model.ModelStitcher;
import org.geysermc.pack.converter.util.JsonMappings;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import team.unnamed.creative.model.Model;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

/**
 * Utility class for packs.
 */
public class PackUtil {
    protected static final Logger LOGGER = LogUtils.getLogger();
    private static final String CONVERSION_KEY_ALGORITHM = "HYDRAULIC_CONVERSION_KEY_V3";
    private static final String COMPATIBILITY_ENGINE_ALGORITHM = "HYDRAULIC_COMPATIBILITY_ENGINE_V1";
    private static final String ADAPTER_CATALOG_ALGORITHM = "HYDRAULIC_ADAPTER_CATALOG_V1";
    private static final String BUILTIN_ADAPTER_CATALOG_FINGERPRINT = ADAPTER_CATALOG_ALGORITHM + ":builtin-only";
    private static final List<Class<?>> COMPATIBILITY_ENGINE_CLASSES = List.of(
        CompatibilityManager.class,
        AnalyzerRegistry.class,
        BlockAnalyzer.class,
        ItemAnalyzer.class,
        EntityAnalyzer.class,
        FluidAnalyzer.class,
        BlockEntityAnalyzer.class,
        MenuAnalyzer.class,
        RecipeAnalyzer.class,
        CapabilityAdapterRegistry.class
    );

    public static String getTextureName(@NotNull String modelName) {
        // Resolve vanilla namespace mappings against Bedrock mappings table
        if (modelName.startsWith(Key.MINECRAFT_NAMESPACE)) {
            String modelValue = modelName.split(":")[1];

            // Need to use the Bedrock value for vanilla textures
            JsonMappings mappings = JsonMappings.getMapping("textures");
            if (mappings != null) {
                String output = mappings.map(modelValue).getFirst();

                String value = output.substring(output.indexOf("/") + 1);

                if (modelValue.equals(output)) {
                    return value;
                }

                return Constants.MOD_ID + ":" + output;
            }

            return modelValue.substring(modelValue.indexOf("/") + 1);
        }

        return modelName.replace("block/", "").replace("item/", "");
    }

    /**
     * Walks the parent chain of the given model upwards using the given provider.
     *
     * @param provider the provider to resolve parent models with
     * @param model the model to walk the parents of
     * @return the model and its parents
     */
    @NotNull
    public static List<Key> modelParents(@NotNull ModelStitcher.Provider provider, @NotNull Model model) {
        List<Key> keys = new ArrayList<>();
        keys.add(model.key());

        Model current = model;
        Key parentKey;
        while ((parentKey = current.parent()) != null) {
            keys.add(parentKey);

            Model parent = provider.model(parentKey);
            if (parent == null) {
                break; // e.g. builtin/generated, which has no model file
            }

            current = parent;
        }

        return keys;
    }

    @NotNull
    public static ConversionKey conversionKey(@NotNull ModInfo mod, @NotNull ModResourceIndex resourceIndex, @NotNull MetadataIndex metadataIndex) {
        return conversionKey(mod, resourceIndex, Map.of(mod.id(), resourceIndex), metadataIndex);
    }

    @NotNull
    public static ConversionKey conversionKey(
        @NotNull ModInfo mod,
        @NotNull ModResourceIndex resourceIndex,
        @NotNull Map<String, ModResourceIndex> allIndexes,
        @NotNull MetadataIndex metadataIndex
    ) {
        ModResourceIndex.ResourceFingerprint resourceFingerprint = resourceIndex.fingerprint();
        DependencyFingerprint dependencyFingerprint = dependencyFingerprint(mod, allIndexes);
        return new ConversionKey(
            CONVERSION_KEY_ALGORITHM,
            mod.id(),
            mod.version(),
            Constants.VERSION,
            SharedConstants.getCurrentVersion().id(),
            resourceFingerprint.stableValue(),
            resourceFingerprint.fileCount(),
            resourceFingerprint.totalSizeBytes(),
            metadataFingerprint(metadataIndex),
            dependencyFingerprint.value(),
            dependencyFingerprint.modCount()
        );
    }

    @NotNull
    static DependencyFingerprint dependencyFingerprint(@NotNull ModInfo mod, @NotNull Map<String, ModResourceIndex> allIndexes) {
        ModResourceIndex resourceIndex = allIndexes.get(mod.id());
        if (resourceIndex == null) {
            return new DependencyFingerprint("", 0);
        }

        Hasher hasher = Hashing.sha256().newHasher();
        Set<String> dependentModIds = new java.util.TreeSet<>();
        Set<String> hashedResourceRefs = new LinkedHashSet<>();
        Set<String> visitedModelRefs = new LinkedHashSet<>();
        Queue<ModelDependencyNode> pendingModels = new LinkedList<>();

        for (Identifier modelId : resourceIndex.modelDependencies().keySet()) {
            pendingModels.add(new ModelDependencyNode(mod.id(), Key.key(modelId.getNamespace(), modelId.getPath())));
        }

        while (!pendingModels.isEmpty()) {
            ModelDependencyNode node = pendingModels.remove();
            String visitedKey = node.modId() + "|" + node.modelKey().asString();
            if (!visitedModelRefs.add(visitedKey)) {
                continue;
            }

            ModResourceIndex ownerIndex = allIndexes.get(node.modId());
            if (ownerIndex == null) {
                continue;
            }

            Identifier modelIdentifier = Identifier.fromNamespaceAndPath(node.modelKey().namespace(), node.modelKey().value());
            for (Key dependency : ownerIndex.modelDependencies().getOrDefault(modelIdentifier, Set.of())) {
                hashDependency(mod.id(), dependency, allIndexes, dependentModIds, hashedResourceRefs, hasher, pendingModels);
            }
        }

        for (Set<Key> dependencies : resourceIndex.equipmentDependencies().values()) {
            for (Key dependency : dependencies) {
                hashDependency(mod.id(), dependency, allIndexes, dependentModIds, hashedResourceRefs, hasher, pendingModels);
            }
        }

        return new DependencyFingerprint(hasher.hash().toString(), dependentModIds.size());
    }

    private static void hashDependency(
        @NotNull String rootModId,
        @NotNull Key dependency,
        @NotNull Map<String, ModResourceIndex> allIndexes,
        @NotNull Set<String> dependentModIds,
        @NotNull Set<String> hashedResourceRefs,
        @NotNull Hasher hasher,
        @NotNull Queue<ModelDependencyNode> pendingModels
    ) {
        for (Map.Entry<String, ModResourceIndex> entry : allIndexes.entrySet()) {
            ModResourceIndex index = entry.getValue();
            if (!index.namespaces().contains(dependency.namespace())) {
                continue;
            }

            Path resourcePath = index.resolveModelPath(dependency);
            boolean modelDependency = resourcePath != null;
            if (resourcePath == null) {
                resourcePath = index.resolveTexturePath(dependency);
            }
            if (resourcePath == null) {
                continue;
            }

            String resourceRef = entry.getKey() + "|" + dependency.asString();
            if (hashedResourceRefs.add(resourceRef) && !rootModId.equals(entry.getKey())) {
                dependentModIds.add(entry.getKey());
                ModResourceIndex.FileStamp fileStamp = index.resolveFileStamp(resourcePath);
                if (fileStamp != null) {
                    hasher.putString(entry.getKey(), StandardCharsets.UTF_8);
                    hasher.putString(fileStamp.stablePath(), StandardCharsets.UTF_8);
                    hasher.putLong(fileStamp.size());
                    hasher.putLong(fileStamp.lastModifiedEpochMillis());
                }
            }

            if (modelDependency) {
                pendingModels.add(new ModelDependencyNode(entry.getKey(), dependency));
            }
        }
    }

    @NotNull
    public static String metadataFingerprint(@NotNull MetadataIndex metadataIndex) {
        Hasher hasher = Hashing.sha256().newHasher();
        hashSummary(hasher, metadataIndex.summary());
        hashBlockMappings(hasher, metadataIndex.blockMappings());
        hashIdentifierMappings(hasher, "items", metadataIndex.itemMappings());
        hashIdentifierMappings(hasher, "recipes", metadataIndex.recipeMappings());
        hashIdentifierMappings(hasher, "entities", metadataIndex.entityMappings());
        hashIdentifierMappings(hasher, "menus", metadataIndex.menuMappings());
        metadataIndex.contentPatches().entrySet().stream()
            .sorted(Map.Entry.comparingByKey(Comparator.comparing(Object::toString)))
            .forEach(entry -> {
                hasher.putString("patches", StandardCharsets.UTF_8);
                hasher.putString(entry.getKey().toString(), StandardCharsets.UTF_8);
                for (ContentPatch patch : entry.getValue()) {
                    hashContentPatch(hasher, patch);
                }
            });
        for (MetadataValidationIssue issue : metadataIndex.validationIssues()) {
            hasher.putString("validation", StandardCharsets.UTF_8);
            hasher.putString(issue.code(), StandardCharsets.UTF_8);
            hasher.putString(issue.severity(), StandardCharsets.UTF_8);
            hasher.putString(Objects.toString(issue.message(), ""), StandardCharsets.UTF_8);
            hasher.putString(Objects.toString(issue.sourcePath(), ""), StandardCharsets.UTF_8);
            hasher.putString(Objects.toString(issue.target(), ""), StandardCharsets.UTF_8);
        }
        return hasher.hash().toString();
    }

    @NotNull
    public static String compatibilityEngineFingerprint() {
        Hasher hasher = Hashing.sha256().newHasher();
        hasher.putString(COMPATIBILITY_ENGINE_ALGORITHM, StandardCharsets.UTF_8);
        hasher.putString(Constants.VERSION, StandardCharsets.UTF_8);
        for (Class<?> type : COMPATIBILITY_ENGINE_CLASSES) {
            hasher.putString(type.getName(), StandardCharsets.UTF_8);
            hashClassBytes(hasher, type);
        }
        return hasher.hash().toString();
    }

    @NotNull
    public static String adapterCatalogFingerprint() {
        return BUILTIN_ADAPTER_CATALOG_FINGERPRINT;
    }

    @NotNull
    public static String startupCompatibilityFingerprint(
        @NotNull String metadataFingerprint,
        @NotNull Map<String, String> modFingerprints,
        @NotNull String engineFingerprint,
        @NotNull String adapterCatalogFingerprint
    ) {
        Hasher hasher = Hashing.sha256().newHasher();
        hasher.putString(engineFingerprint, StandardCharsets.UTF_8);
        hasher.putString(metadataFingerprint, StandardCharsets.UTF_8);
        hasher.putString(adapterCatalogFingerprint, StandardCharsets.UTF_8);
        modFingerprints.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                hasher.putString(entry.getKey(), StandardCharsets.UTF_8);
                hasher.putString(entry.getValue(), StandardCharsets.UTF_8);
            });
        return hasher.hash().toString();
    }

    @NotNull
    public static String compatibilityCacheFingerprint(@NotNull String metadataFingerprint, @NotNull Map<String, String> modFingerprints, @NotNull String engineFingerprint) {
        return startupCompatibilityFingerprint(metadataFingerprint, modFingerprints, engineFingerprint, adapterCatalogFingerprint());
    }

    private static void hashSummary(@NotNull Hasher hasher, @NotNull MetadataIndex.Summary summary) {
        hasher.putString("summary", StandardCharsets.UTF_8);
        hasher.putInt(summary.fileCount());
        hasher.putInt(summary.blockMappingCount());
        hasher.putInt(summary.itemMappingCount());
        hasher.putInt(summary.recipeMappingCount());
        hasher.putInt(summary.entityMappingCount());
        hasher.putInt(summary.menuMappingCount());
        hasher.putInt(summary.patchCount());
        hasher.putInt(summary.ruleCount());
        hasher.putInt(summary.validationIssueCount());
        summary.ownershipFileCounts().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                hasher.putString(entry.getKey(), StandardCharsets.UTF_8);
                hasher.putInt(entry.getValue());
            });
    }

    record DependencyFingerprint(@NotNull String value, int modCount) {
    }

    private record ModelDependencyNode(@NotNull String modId, @NotNull Key modelKey) {
    }

    private static void hashClassBytes(@NotNull Hasher hasher, @NotNull Class<?> type) {
        String resourceName = type.getSimpleName() + ".class";
        try (InputStream inputStream = type.getResourceAsStream(resourceName)) {
            if (inputStream == null) {
                hasher.putString("missing", StandardCharsets.UTF_8);
                return;
            }

            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                hasher.putBytes(buffer, 0, read);
            }
        } catch (IOException exception) {
            LOGGER.warn("Failed to hash compatibility engine class bytes for {}", type.getName(), exception);
            hasher.putString("io-error", StandardCharsets.UTF_8);
        }
    }

    private static void hashBlockMappings(@NotNull Hasher hasher, @NotNull Map<?, BlockMapping> mappings) {
        mappings.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(Comparator.comparing(Object::toString)))
            .forEach(entry -> {
                hasher.putString("blocks", StandardCharsets.UTF_8);
                hasher.putString(entry.getKey().toString(), StandardCharsets.UTF_8);
                hashBlockMapping(hasher, entry.getValue());
            });
    }

    private static void hashIdentifierMappings(@NotNull Hasher hasher, @NotNull String section, @NotNull Map<?, IdentifierMapping> mappings) {
        mappings.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(Comparator.comparing(Object::toString)))
            .forEach(entry -> {
                hasher.putString(section, StandardCharsets.UTF_8);
                hasher.putString(entry.getKey().toString(), StandardCharsets.UTF_8);
                hashIdentifierMapping(hasher, entry.getValue());
            });
    }

    private static void hashBlockMapping(@NotNull Hasher hasher, @NotNull BlockMapping mapping) {
        hasher.putString(mapping.javaIdentifier().toString(), StandardCharsets.UTF_8);
        for (var rule : mapping.rules()) {
            rule.javaWhen().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    hasher.putString(entry.getKey(), StandardCharsets.UTF_8);
                    hasher.putString(entry.getValue(), StandardCharsets.UTF_8);
                });
            hasher.putString(Objects.toString(rule.bedrockIdentifier(), ""), StandardCharsets.UTF_8);
            if (rule.bedrockState() != null) {
                rule.bedrockState().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        hasher.putString(entry.getKey(), StandardCharsets.UTF_8);
                        hasher.putString(entry.getValue(), StandardCharsets.UTF_8);
                    });
            }
            hasher.putString(Objects.toString(rule.geometryId(), ""), StandardCharsets.UTF_8);
            hasher.putString(Objects.toString(rule.materialId(), ""), StandardCharsets.UTF_8);
            hasher.putBoolean(rule.behaviorRequired());
            hasher.putString(Objects.toString(rule.behaviorTag(), ""), StandardCharsets.UTF_8);
            hasher.putString(rule.ownership().name(), StandardCharsets.UTF_8);
            hasher.putString(rule.sourcePath(), StandardCharsets.UTF_8);
            hasher.putInt(rule.priority());
            hasher.putInt(rule.order());
        }
    }

    private static void hashIdentifierMapping(@NotNull Hasher hasher, @NotNull IdentifierMapping mapping) {
        hasher.putString(mapping.javaIdentifier().toString(), StandardCharsets.UTF_8);
        hasher.putString(mapping.bedrockIdentifier().toString(), StandardCharsets.UTF_8);
        hasher.putString(mapping.ownership().name(), StandardCharsets.UTF_8);
        hasher.putString(mapping.sourcePath(), StandardCharsets.UTF_8);
        hasher.putInt(mapping.priority());
        hasher.putInt(mapping.order());
    }

    private static void hashContentPatch(@NotNull Hasher hasher, @NotNull ContentPatch patch) {
        hasher.putString(patch.target().toString(), StandardCharsets.UTF_8);
        hasher.putString(Objects.toString(patch.contentType(), ""), StandardCharsets.UTF_8);
        patch.operations().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                hasher.putString(entry.getKey(), StandardCharsets.UTF_8);
                hasher.putString(entry.getValue(), StandardCharsets.UTF_8);
            });
        hasher.putString(patch.ownership().name(), StandardCharsets.UTF_8);
        hasher.putString(patch.sourcePath(), StandardCharsets.UTF_8);
        hasher.putInt(patch.priority());
        hasher.putInt(patch.order());
    }
}
