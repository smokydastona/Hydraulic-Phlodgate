package org.geysermc.hydraulic.compat;

import org.geysermc.hydraulic.compat.model.ModFingerprint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;
import java.nio.file.InvalidPathException;
import java.util.Set;

public final class ContentInventory {
    private final Map<String, ModContentInventory> mods;

    public ContentInventory(@NotNull Map<String, ModContentInventory> mods) {
        this.mods = Collections.unmodifiableMap(new LinkedHashMap<>(mods));
    }

    @NotNull
    public static ContentInventory empty() {
        return new ContentInventory(Map.of());
    }

    @NotNull
    public Map<String, ModContentInventory> mods() {
        return this.mods;
    }

    public record ModContentInventory(
        @NotNull String modId,
        @NotNull String namespace,
        @NotNull String name,
        @NotNull String version,
        @NotNull List<String> roots,
        @NotNull ModFingerprint fingerprint,
        @NotNull Map<String, Integer> registryCounts,
        @NotNull Map<String, List<String>> registryEntries,
        @NotNull Map<String, Integer> assetCounts,
        @NotNull Map<String, List<String>> assetEntries,
        @NotNull Map<String, Integer> metadataCounts,
        @NotNull Map<String, List<String>> metadataEntries,
        @NotNull Map<String, Integer> patchCounts,
        @NotNull Map<String, List<String>> patchEntries,
        @NotNull Map<String, String> recipePaths
    ) {
        public ModContentInventory(
            @NotNull String modId,
            @NotNull String namespace,
            @NotNull String name,
            @NotNull String version,
            @NotNull List<String> roots,
            @NotNull ModFingerprint fingerprint,
            @NotNull Map<String, Integer> registryCounts,
            @NotNull Map<String, List<String>> registryEntries,
            @NotNull Map<String, Integer> assetCounts,
            @NotNull Map<String, List<String>> assetEntries,
            @NotNull Map<String, Integer> metadataCounts,
            @NotNull Map<String, List<String>> metadataEntries,
            @NotNull Map<String, Integer> patchCounts,
            @NotNull Map<String, List<String>> patchEntries
        ) {
            this(modId, namespace, name, version, roots, fingerprint, registryCounts, registryEntries, assetCounts,
                assetEntries, metadataCounts, metadataEntries, patchCounts, patchEntries, Map.of());
        }

        public ModContentInventory {
            roots = roots == null ? List.of() : List.copyOf(roots);
            registryCounts = immutableIntegerMap(registryCounts);
            registryEntries = immutableCopy(registryEntries);
            assetCounts = immutableIntegerMap(assetCounts);
            assetEntries = immutableCopy(assetEntries);
            metadataCounts = immutableIntegerMap(metadataCounts);
            metadataEntries = immutableCopy(metadataEntries);
            patchCounts = immutableIntegerMap(patchCounts);
            patchEntries = immutableCopy(patchEntries);
            recipePaths = recipePaths == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(recipePaths));
        }

        @Nullable
        public Path recipePath(@NotNull String recipeIdentifier) {
            String path = this.recipePaths.get(recipeIdentifier);
            if (path == null) {
                return null;
            }
            try {
                return Path.of(path);
            } catch (InvalidPathException ignored) {
                return null;
            }
        }

        @NotNull
        public List<ContentDescriptor> contentDescriptors() {
            List<ContentDescriptor> descriptors = new java.util.ArrayList<>();
            descriptors.addAll(this.descriptorsFor("block", "blocks", "block_assets"));
            descriptors.addAll(this.descriptorsFor("item", "items", "item_assets"));
            descriptors.addAll(this.descriptorsFor("entity", "entities", null));
            descriptors.addAll(this.descriptorsFor("fluid", "fluids", null));
            descriptors.addAll(this.descriptorsFor("block_entity", "block_entities", null));
            descriptors.addAll(this.descriptorsFor("menu", "menus", null));
            descriptors.addAll(this.descriptorsFor("recipe", "recipes", "recipes"));
            return List.copyOf(descriptors);
        }

        @NotNull
        private List<ContentDescriptor> descriptorsFor(@NotNull String kind, @NotNull String entryKey, String assetKey) {
            Set<String> ids = new LinkedHashSet<>();
            ids.addAll(this.registryEntries.getOrDefault(entryKey, List.of()));
            ids.addAll(this.assetEntries.getOrDefault(entryKey, List.of()));
            ids.addAll(this.metadataEntries.getOrDefault(entryKey, List.of()));
            ids.addAll(this.patchEntries.getOrDefault(entryKey, List.of()));

            List<ContentDescriptor> descriptors = new java.util.ArrayList<>();
            List<String> registeredEntries = this.registryEntries.getOrDefault(entryKey, List.of());
            List<String> assetEntries = assetKey != null ? this.assetEntries.getOrDefault(assetKey, List.of()) : List.of();
            for (String id : ids) {
                descriptors.add(new ContentDescriptor(kind, this.modId, id, registeredEntries.contains(id), assetEntries.contains(id), assetEntries.contains(id) ? List.of(id) : List.of()));
            }
            return descriptors;
        }

        @NotNull
        private static Map<String, Integer> immutableIntegerMap(@Nullable Map<String, Integer> values) {
            if (values == null) {
                return Map.of();
            }
            return Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }

        @NotNull
        private static Map<String, List<String>> immutableCopy(@Nullable Map<String, List<String>> values) {
            if (values == null) {
                return Map.of();
            }
            Map<String, List<String>> copy = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> entry : values.entrySet()) {
                copy.put(entry.getKey(), entry.getValue() == null ? List.of() : List.copyOf(entry.getValue()));
            }
            return Collections.unmodifiableMap(copy);
        }
    }

    public record ContentDescriptor(
        @NotNull String kind,
        @NotNull String modId,
        @NotNull String javaIdentifier,
        boolean registered,
        boolean assetPresent,
        @NotNull List<String> assetReferences
    ) {
        public ContentDescriptor {
            assetReferences = List.copyOf(assetReferences);
        }
    }
}