package org.geysermc.hydraulic.pack.index;

import com.google.common.collect.ListMultimap;
import net.kyori.adventure.key.Key;
import net.minecraft.resources.Identifier;
import org.geysermc.hydraulic.pack.ModResourceIndex;
import org.geysermc.hydraulic.platform.mod.ModInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Universal resource index that provides authoritative discovery across the entire pipeline.
 *
 * This separates the broader universal-index boundaries from ModResourceIndex and ensures
 * that runtime consumers use indexed surfaces instead of local resource scans.
 */
public final class UniversalResourceIndex {
    private final Map<String, ModResourceIndex> modIndexes;
    private final Map<String, Set<String>> namespaceToMods;
    private final Map<String, Set<Identifier>> modToBlocks;
    private final Map<String, Set<Identifier>> modToItems;

    private UniversalResourceIndex(
        @NotNull Map<String, ModResourceIndex> modIndexes,
        @NotNull Map<String, Set<String>> namespaceToMods,
        @NotNull Map<String, Set<Identifier>> modToBlocks,
        @NotNull Map<String, Set<Identifier>> modToItems
    ) {
        this.modIndexes = Map.copyOf(modIndexes);
        this.namespaceToMods = Map.copyOf(namespaceToMods);
        this.modToBlocks = Map.copyOf(modToBlocks);
        this.modToItems = Map.copyOf(modToItems);
    }

    @NotNull
    public static UniversalResourceIndex from(
        @NotNull Map<String, ModResourceIndex> modIndexes,
        @NotNull Map<String, Set<String>> namespaceToMods,
        @NotNull Map<String, Set<Identifier>> modToBlocks,
        @NotNull Map<String, Set<Identifier>> modToItems
    ) {
        return new UniversalResourceIndex(modIndexes, namespaceToMods, modToBlocks, modToItems);
    }

    @NotNull
    public static UniversalResourceIndex fromLookups(
        @NotNull Map<String, ModResourceIndex> modIndexes,
        @NotNull ListMultimap<String, ModInfo> namespacesToMods,
        @NotNull ListMultimap<String, Identifier> modsToBlocks,
        @NotNull ListMultimap<String, Identifier> modsToItems
    ) {
        Map<String, Set<String>> namespaceToModIds = new LinkedHashMap<>();
        for (Map.Entry<String, ModInfo> entry : namespacesToMods.entries()) {
            namespaceToModIds.computeIfAbsent(entry.getKey(), ignored -> new LinkedHashSet<>()).add(entry.getValue().id());
        }

        Map<String, Set<Identifier>> modBlocks = new LinkedHashMap<>();
        for (Map.Entry<String, Identifier> entry : modsToBlocks.entries()) {
            modBlocks.computeIfAbsent(entry.getKey(), ignored -> new LinkedHashSet<>()).add(entry.getValue());
        }

        Map<String, Set<Identifier>> modItems = new LinkedHashMap<>();
        for (Map.Entry<String, Identifier> entry : modsToItems.entries()) {
            modItems.computeIfAbsent(entry.getKey(), ignored -> new LinkedHashSet<>()).add(entry.getValue());
        }

        return from(modIndexes, copyNamespaceMap(namespaceToModIds), copyIdentifierMap(modBlocks), copyIdentifierMap(modItems));
    }

    @NotNull
    private static Map<String, Set<String>> copyNamespaceMap(@NotNull Map<String, Set<String>> source) {
        Map<String, Set<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : source.entrySet()) {
            copy.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }

    @NotNull
    private static Map<String, Set<Identifier>> copyIdentifierMap(@NotNull Map<String, Set<Identifier>> source) {
        Map<String, Set<Identifier>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Set<Identifier>> entry : source.entrySet()) {
            copy.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }

    @NotNull
    public Map<String, ModResourceIndex> modIndexes() {
        return this.modIndexes;
    }

    @NotNull
    public Map<String, Set<String>> namespaceToMods() {
        return this.namespaceToMods;
    }

    @NotNull
    public Map<String, Set<Identifier>> modToBlocks() {
        return this.modToBlocks;
    }

    @NotNull
    public Map<String, Set<Identifier>> modToItems() {
        return this.modToItems;
    }

    /**
     * Resolves the owning mod for a given namespace.
     */
    @Nullable
    public String resolveModForNamespace(@NotNull String namespace) {
        Set<String> modIds = this.namespaceToMods.get(namespace);
        if (modIds == null || modIds.isEmpty()) {
            return null;
        }
        return modIds.iterator().next();
    }

    /**
     * Resolves the owning mod for a given block identifier.
     */
    @Nullable
    public String resolveModForBlock(@NotNull Identifier block) {
        String namespace = block.getNamespace();
        String modId = resolveModForNamespace(namespace);
        if (modId == null) {
            return null;
        }

        ModResourceIndex index = this.modIndexes.get(modId);
        if (index == null || !index.hasBlockState(block)) {
            return null;
        }

        return modId;
    }

    /**
     * Resolves the owning mod for a given item identifier.
     */
    @Nullable
    public String resolveModForItem(@NotNull Identifier item) {
        String namespace = item.getNamespace();
        String modId = resolveModForNamespace(namespace);
        if (modId == null) {
            return null;
        }

        ModResourceIndex index = this.modIndexes.get(modId);
        if (index == null || !index.hasItemAsset(item)) {
            return null;
        }

        return modId;
    }

    /**
     * Resolves a model path through the universal index.
     */
    @Nullable
    public Key resolveModelKey(@NotNull Key modelKey) {
        String namespace = modelKey.namespace();
        String modId = resolveModForNamespace(namespace);
        if (modId == null) {
            return null;
        }

        ModResourceIndex index = this.modIndexes.get(modId);
        if (index == null) {
            return null;
        }

        Path modelPath = index.resolveModelPath(modelKey);
        if (modelPath == null) {
            return null;
        }

        return modelKey;
    }

    /**
     * Resolves a texture path through the universal index.
     */
    @Nullable
    public Key resolveTextureKey(@NotNull Key textureKey) {
        String namespace = textureKey.namespace();
        String modId = resolveModForNamespace(namespace);
        if (modId == null) {
            return null;
        }

        ModResourceIndex index = this.modIndexes.get(modId);
        if (index == null) {
            return null;
        }

        Path texturePath = index.resolveTexturePath(textureKey);
        if (texturePath == null) {
            return null;
        }

        return textureKey;
    }

    /**
     * Gets all indexed models across all mods.
     */
    @NotNull
    public Map<Key, Path> allModels() {
        Map<Key, Path> allModels = new java.util.LinkedHashMap<>();
        for (ModResourceIndex index : this.modIndexes.values()) {
            allModels.putAll(index.modelPaths());
        }
        return Map.copyOf(allModels);
    }

    /**
     * Gets all indexed textures across all mods.
     */
    @NotNull
    public Map<Key, Path> allTextures() {
        Map<Key, Path> allTextures = new java.util.LinkedHashMap<>();
        for (ModResourceIndex index : this.modIndexes.values()) {
            allTextures.putAll(index.texturePaths());
        }
        return Map.copyOf(allTextures);
    }

    /**
     * Resolves a Java language resource path through the universal index.
     */
    @Nullable
    public Identifier resolveLanguageKey(@NotNull Identifier language) {
        String namespace = language.getNamespace();
        String modId = resolveModForNamespace(namespace);
        if (modId == null) {
            return null;
        }

        ModResourceIndex index = this.modIndexes.get(modId);
        if (index == null || index.resolveLanguagePath(language) == null) {
            return null;
        }

        return language;
    }

    /**
     * Gets all indexed language files across all mods.
     */
    @NotNull
    public Map<Identifier, Path> allLanguages() {
        Map<Identifier, Path> allLanguages = new java.util.LinkedHashMap<>();
        for (ModResourceIndex index : this.modIndexes.values()) {
            allLanguages.putAll(index.languagePaths());
        }
        return Map.copyOf(allLanguages);
    }

    /**
     * Gets dependency namespaces for a specific mod.
     */
    @NotNull
    public Set<String> dependencyNamespaces(@NotNull String modId) {
        ModResourceIndex index = this.modIndexes.get(modId);
        if (index == null) {
            return Set.of();
        }
        return index.dependencyNamespaces();
    }

    /**
     * Gets model dependencies for a specific mod.
     */
    @NotNull
    public Map<Identifier, Set<Key>> modelDependencies(@NotNull String modId) {
        ModResourceIndex index = this.modIndexes.get(modId);
        if (index == null) {
            return Map.of();
        }
        return index.modelDependencies();
    }

    /**
     * Gets equipment dependencies for a specific mod.
     */
    @NotNull
    public Map<Identifier, Set<Key>> equipmentDependencies(@NotNull String modId) {
        ModResourceIndex index = this.modIndexes.get(modId);
        if (index == null) {
            return Map.of();
        }
        return index.equipmentDependencies();
    }

    /**
     * Generates a DiscoveryIR representation for a discovered resource.
     * Implements P0.1 Universal Resource Index consolidation.
     */
    @NotNull
    public org.geysermc.hydraulic.compat.ir.DiscoveryIR toDiscoveryIR(
        @NotNull Identifier identifier,
        @NotNull org.geysermc.hydraulic.compat.ir.DiscoveryIR.ResourceKind kind
    ) {
        String namespace = identifier.getNamespace();
        String modId = resolveModForNamespace(namespace);
        if (modId == null) {
            modId = namespace;
        }

        ModResourceIndex modIndex = this.modIndexes.get(modId);
        String relativePath = identifier.getPath();
        long size = 0L;
        long lastModified = 0L;
        String fingerprint = "";

        if (modIndex != null) {
            fingerprint = modIndex.fingerprint().digest();
            size = modIndex.fingerprint().totalSizeBytes();
            lastModified = modIndex.fingerprint().latestModifiedEpochMillis();
        }

        return new org.geysermc.hydraulic.compat.ir.DiscoveryIR(
            identifier,
            kind,
            modId,
            modId,
            relativePath,
            size,
            lastModified,
            fingerprint,
            Set.of(),
            Map.of()
        );
    }
}