package org.geysermc.hydraulic.cache;

import org.geysermc.hydraulic.Constants;
import org.geysermc.hydraulic.compat.CompatibilityReport;
import org.geysermc.hydraulic.compat.ContentInventory;
import org.geysermc.hydraulic.pack.ModResourceIndex;
import org.geysermc.hydraulic.platform.mod.ModInfo;
import org.geysermc.hydraulic.pack.PackValidationReport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ArtifactCache {
    private static final String INDEX_MANIFEST = "index-manifest.json";
    private static final String COMPATIBILITY_MANIFEST = "compatibility-manifest.json";
    private static final String CONTENT_INVENTORY = "content-inventory.json";
    private static final String COMPATIBILITY_REPORT = "compatibility-report.json";
    private static final String CONVERSION_MANIFEST = "conversion-manifest.json";
    private static final String VALIDATION_REPORT = "pack-validation-report.json";

    private final Logger logger;
    private final Path root;

    public ArtifactCache(@NotNull Logger logger, @NotNull Path root) {
        this.logger = logger;
        this.root = root;
    }

    public void ensureLayout() {
        try {
            Files.createDirectories(this.indexPath());
            Files.createDirectories(this.compatibilityPath());
            Files.createDirectories(this.conversionsPath());
            Files.createDirectories(this.validationPath());
            Files.createDirectories(this.manifestsPath());
        } catch (IOException e) {
            this.logger.error("Failed to initialize Hydraulic artifact cache layout at {}", this.root, e);
        }
    }

    public void storeIndexSnapshot(@NotNull IndexSnapshot snapshot) {
        this.writeJson(this.indexPath().resolve(INDEX_MANIFEST), snapshot);
    }

    @Nullable
    public IndexSnapshot loadIndexSnapshot() {
        return this.readJson(this.indexPath().resolve(INDEX_MANIFEST), IndexSnapshot.class);
    }

    @NotNull
    public Map<String, ModResourceIndex> loadReusableIndexes(@NotNull Collection<ModInfo> mods) {
        IndexSnapshot snapshot = this.loadIndexSnapshot();
        if (snapshot == null || !IndexSnapshot.ALGORITHM.equals(snapshot.algorithm())) {
            return Map.of();
        }

        Map<String, ModResourceIndex> rehydrated = new LinkedHashMap<>();
        for (ModInfo mod : mods) {
            IndexedMod indexed = snapshot.mods().get(mod.id());
            if (indexed == null || !indexed.matches(mod)) {
                continue;
            }

            if (!indexed.isReusable()) {
                continue;
            }

            rehydrated.put(mod.id(), ModResourceIndex.rehydrate(indexed.snapshot()));
        }
        return Map.copyOf(rehydrated);
    }

    public void storeCompatibilitySnapshot(@NotNull CompatibilitySnapshot snapshot) {
        Path compatibilityPath = this.compatibilityPath();
        this.writeJson(compatibilityPath.resolve(COMPATIBILITY_MANIFEST), snapshot.manifest());
        this.writeJson(compatibilityPath.resolve(CONTENT_INVENTORY), snapshot.inventory());
        this.writeJson(compatibilityPath.resolve(COMPATIBILITY_REPORT), snapshot.report());
    }

    @Nullable
    public CompatibilitySnapshot loadCompatibilitySnapshot(@NotNull StartupCompatibilityKey key) {
        CompatibilityManifest manifest = this.readJson(this.compatibilityPath().resolve(COMPATIBILITY_MANIFEST), CompatibilityManifest.class);
        if (manifest == null || !manifest.startupKey().value().equals(key.value())) {
            return null;
        }

        ContentInventory inventory = this.readJson(this.compatibilityPath().resolve(CONTENT_INVENTORY), ContentInventory.class);
        CompatibilityReport report = this.readJson(this.compatibilityPath().resolve(COMPATIBILITY_REPORT), CompatibilityReport.class);
        if (inventory == null || report == null) {
            return null;
        }
        return new CompatibilitySnapshot(manifest, inventory, report);
    }

    public void storeConversionArtifact(@NotNull String modId, @NotNull ConversionArtifact artifact) {
        this.writeJson(this.conversionsPath().resolve(modId).resolve(CONVERSION_MANIFEST), artifact);
    }

    public void storeValidationArtifact(@NotNull PackValidationReport report, @NotNull String cacheKey) {
        Path validationPath = this.validationPath();
        this.writeJson(validationPath.resolve(VALIDATION_REPORT), report);
        this.writeJson(this.manifestsPath().resolve("validation-manifest.json"), new ValidationManifest(cacheKey));
    }

    @NotNull
    private Path indexPath() {
        return this.root.resolve("index");
    }

    @NotNull
    private Path compatibilityPath() {
        return this.root.resolve("compatibility");
    }

    @NotNull
    private Path conversionsPath() {
        return this.root.resolve("conversions");
    }

    @NotNull
    private Path validationPath() {
        return this.root.resolve("validation");
    }

    @NotNull
    private Path manifestsPath() {
        return this.root.resolve("manifests");
    }

    private void writeJson(@NotNull Path path, @NotNull Object value) {
        try {
            Files.createDirectories(path.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(path)) {
                Constants.GSON.toJson(value, writer);
            }
        } catch (IOException e) {
            this.logger.error("Failed to write Hydraulic artifact cache entry {}", path, e);
        }
    }

    @Nullable
    private <T> T readJson(@NotNull Path path, @NotNull Class<T> type) {
        if (!Files.isRegularFile(path)) {
            return null;
        }

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            return Constants.GSON.fromJson(reader, type);
        } catch (IOException e) {
            this.logger.error("Failed to read Hydraulic artifact cache entry {}", path, e);
            return null;
        }
    }

    public record StartupCompatibilityKey(@NotNull String value) {
    }

    public record CompatibilityManifest(
        @NotNull StartupCompatibilityKey startupKey,
        @NotNull String metadataFingerprint,
        @NotNull String engineFingerprint,
        @NotNull String adapterCatalogFingerprint,
        int modCount,
        @NotNull Map<String, String> modFingerprints
    ) {
        public CompatibilityManifest {
            startupKey = startupKey == null ? new StartupCompatibilityKey("") : startupKey;
            engineFingerprint = engineFingerprint == null ? "" : engineFingerprint;
            adapterCatalogFingerprint = adapterCatalogFingerprint == null ? "" : adapterCatalogFingerprint;
            modFingerprints = Map.copyOf(new LinkedHashMap<>(modFingerprints));
        }

        @Deprecated
        public String cacheKey() {
            return this.startupKey.value();
        }
    }

    public record CompatibilitySnapshot(
        @NotNull CompatibilityManifest manifest,
        @NotNull ContentInventory inventory,
        @NotNull CompatibilityReport report
    ) {
    }

    public record IndexSnapshot(
        @NotNull String algorithm,
        int modCount,
        @NotNull Map<String, IndexedMod> mods
    ) {
        public static final String ALGORITHM = "HYDRAULIC_INDEX_SNAPSHOT_V2";

        public IndexSnapshot {
            algorithm = algorithm == null ? "" : algorithm;
            mods = mods == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(mods));
        }

        public static IndexSnapshot from(@NotNull Collection<ModInfo> mods, @NotNull Map<String, ModResourceIndex> indexes) {
            Map<String, IndexedMod> indexedMods = new LinkedHashMap<>();
            for (ModInfo mod : mods) {
                ModResourceIndex index = indexes.get(mod.id());
                if (index == null) {
                    continue;
                }
                indexedMods.put(mod.id(), new IndexedMod(
                    index.fingerprint(),
                    index.namespaces().size(),
                    index.blockStateCount(),
                    index.itemAssetCount(),
                    index.hasAssetFiles(),
                    summarizeAssetCounts(index),
                    mod.roots().stream().map(Path::toString).toList(),
                    index.scanRoots(),
                    index.fileStamps(),
                    index.directoryStamps(),
                    index.snapshot()
                ));
            }
            return new IndexSnapshot(ALGORITHM, indexedMods.size(), indexedMods);
        }

        @NotNull
        private static Map<String, Integer> summarizeAssetCounts(@NotNull ModResourceIndex index) {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (String category : java.util.List.of("blockstates", "item_models", "models", "textures", "sounds", "lang", "recipes", "tags", "loot_tables")) {
                counts.put(category, index.assetEntries(category).size());
            }
            return counts;
        }
    }

    public record IndexedMod(
        @NotNull ModResourceIndex.ResourceFingerprint fingerprint,
        int namespaceCount,
        int blockStateCount,
        int itemAssetCount,
        boolean hasAssetFiles,
        @NotNull Map<String, Integer> assetCounts,
        @NotNull List<String> roots,
        @NotNull List<ModResourceIndex.ScanRoot> scanRoots,
        @NotNull List<ModResourceIndex.FileStamp> fileStamps,
        @NotNull List<ModResourceIndex.DirectoryStamp> directoryStamps,
        @NotNull ModResourceIndex.Snapshot snapshot
    ) {
        public IndexedMod {
            assetCounts = assetCounts == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(assetCounts));
            roots = roots == null ? List.of() : List.copyOf(roots);
            scanRoots = scanRoots == null ? List.of() : List.copyOf(scanRoots);
            fileStamps = fileStamps == null ? List.of() : List.copyOf(fileStamps);
            directoryStamps = directoryStamps == null ? List.of() : List.copyOf(directoryStamps);
            snapshot = snapshot == null ? new ModResourceIndex.Snapshot(
                java.util.Set.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                java.util.Set.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                fingerprint,
                Map.of(),
                hasAssetFiles,
                List.of(),
                List.of(),
                List.of()
            ) : snapshot;
        }

        boolean matches(@NotNull ModInfo mod) {
            List<String> currentRoots = mod.roots().stream().map(Path::toString).toList();
            return this.roots.equals(currentRoots);
        }

        boolean isReusable() {
            for (ModResourceIndex.ScanRoot scanRoot : this.scanRoots) {
                Path path = Path.of(scanRoot.path());
                boolean exists = Files.isDirectory(path);
                if (exists != scanRoot.exists()) {
                    return false;
                }
                if (exists && lastModified(path) != scanRoot.lastModifiedEpochMillis()) {
                    return false;
                }
            }

            for (ModResourceIndex.DirectoryStamp directory : this.directoryStamps) {
                Path path = Path.of(directory.path());
                if (!Files.isDirectory(path) || lastModified(path) != directory.lastModifiedEpochMillis()) {
                    return false;
                }
            }

            for (ModResourceIndex.FileStamp file : this.fileStamps) {
                Path path = Path.of(file.path());
                if (!Files.isRegularFile(path)) {
                    return false;
                }
                if (size(path) != file.size() || lastModified(path) != file.lastModifiedEpochMillis()) {
                    return false;
                }
            }

            return true;
        }

        private static long size(@NotNull Path path) {
            try {
                return Files.size(path);
            } catch (IOException e) {
                return -1L;
            }
        }

        private static long lastModified(@NotNull Path path) {
            try {
                return Files.getLastModifiedTime(path).toMillis();
            } catch (IOException e) {
                return -1L;
            }
        }
    }

    public record ConversionArtifact(
        @NotNull String modId,
        @NotNull ConversionKey conversionKey,
        @NotNull String packPath,
        @NotNull String packUuid
    ) {
    }

    public record ValidationManifest(@NotNull String cacheKey) {
    }
}