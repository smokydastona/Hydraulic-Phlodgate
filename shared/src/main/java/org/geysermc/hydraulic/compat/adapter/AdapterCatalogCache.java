package org.geysermc.hydraulic.compat.adapter;

import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Manages the local cache for adapter catalogs.
 * Hydraulic startup can read from this cache synchronously without depending on remote availability.
 */
public final class AdapterCatalogCache {
    private static final String CACHE_ALGORITHM = "HYDRAULIC_ADAPTER_CATALOG_CACHE_V1";
    private static final String CATALOG_MANIFEST = "adapter-catalog-manifest.json";
    private static final String BUILTIN_CATALOG_FILE = "builtin-catalog.json";
    private static final String MOD_SPECIFIC_CATALOG_FILE = "mod-specific-catalog.json";

    private final Logger logger;
    private final Path cacheRoot;

    public AdapterCatalogCache(@NotNull Logger logger, @NotNull Path cacheRoot) {
        this.logger = logger;
        this.cacheRoot = cacheRoot.resolve("adapter-catalog");
    }

    public void ensureLayout() {
        try {
            Files.createDirectories(this.cacheRoot);
        } catch (Exception e) {
            this.logger.error("Failed to initialize adapter catalog cache layout at {}", this.cacheRoot, e);
        }
    }

    public void storeCatalog(@NotNull AdapterCatalog catalog) {
        this.ensureLayout();
        // Store builtin adapters
        if (!catalog.builtInAdapters().isEmpty()) {
            this.writeJson(this.cacheRoot.resolve(BUILTIN_CATALOG_FILE), catalog.builtInAdapters());
        }
        // Store mod-specific adapters
        if (!catalog.modSpecificAdapters().isEmpty()) {
            this.writeJson(this.cacheRoot.resolve(MOD_SPECIFIC_CATALOG_FILE), catalog.modSpecificAdapters());
        }
        // Store manifest
        this.writeJson(this.cacheRoot.resolve(CATALOG_MANIFEST), new CatalogManifest(
            CACHE_ALGORITHM,
            catalog.fingerprint(),
            catalog.builtInAdapters().size(),
            catalog.modSpecificAdapters().size(),
            catalog.isBuiltinOnly()
        ));
    }

    @Nullable
    public AdapterCatalog loadCatalog() {
        CatalogManifest manifest = this.readJson(this.cacheRoot.resolve(CATALOG_MANIFEST), CatalogManifest.class);
        if (manifest == null) {
            this.logger.debug("No adapter catalog manifest found, falling back to builtin-only catalog");
            return AdapterCatalog.builtinOnly();
        }

        if (!CACHE_ALGORITHM.equals(manifest.algorithm()) || manifest.fingerprint().isBlank()) {
            this.logger.warn("Adapter catalog cache manifest is invalid or obsolete, falling back to builtin-only catalog");
            return AdapterCatalog.builtinOnly();
        }

        Map<String, AdapterBinding> builtInAdapters = this.readBindings(this.cacheRoot.resolve(BUILTIN_CATALOG_FILE));
        Map<String, AdapterBinding> modSpecificAdapters = this.readBindings(this.cacheRoot.resolve(MOD_SPECIFIC_CATALOG_FILE));

        if (builtInAdapters == null) {
            builtInAdapters = Map.of();
        }
        if (modSpecificAdapters == null) {
            modSpecificAdapters = Map.of();
        }

        return AdapterCatalog.from(manifest.fingerprint(), builtInAdapters, modSpecificAdapters);
    }

    public void invalidate() {
        try {
            Files.deleteIfExists(this.cacheRoot.resolve(CATALOG_MANIFEST));
            Files.deleteIfExists(this.cacheRoot.resolve(BUILTIN_CATALOG_FILE));
            Files.deleteIfExists(this.cacheRoot.resolve(MOD_SPECIFIC_CATALOG_FILE));
            this.logger.info("Invalidated adapter catalog cache");
        } catch (Exception e) {
            this.logger.error("Failed to invalidate adapter catalog cache", e);
        }
    }

    private void writeJson(@NotNull Path path, @NotNull Object value) {
        try {
            Files.createDirectories(path.getParent());
            try (var writer = Files.newBufferedWriter(path)) {
                org.geysermc.hydraulic.Constants.GSON.toJson(value, writer);
            }
        } catch (Exception e) {
            this.logger.error("Failed to write adapter catalog cache entry {}", path, e);
        }
    }

    @Nullable
    private <T> T readJson(@NotNull Path path, @NotNull Class<T> type) {
        if (!Files.isRegularFile(path)) {
            return null;
        }

        try (var reader = Files.newBufferedReader(path)) {
            return org.geysermc.hydraulic.Constants.GSON.fromJson(reader, type);
        } catch (Exception e) {
            this.logger.error("Failed to read adapter catalog cache entry {}", path, e);
            return null;
        }
    }

    @Nullable
    private Map<String, AdapterBinding> readBindings(@NotNull Path path) {
        if (!Files.isRegularFile(path)) {
            return null;
        }

        try (var reader = Files.newBufferedReader(path)) {
            return org.geysermc.hydraulic.Constants.GSON.fromJson(reader, new TypeToken<Map<String, AdapterBinding>>() {}.getType());
        } catch (Exception e) {
            this.logger.error("Failed to read adapter binding cache entry {}", path, e);
            return null;
        }
    }

    private record CatalogManifest(
        @NotNull String algorithm,
        @NotNull String fingerprint,
        int builtInAdapterCount,
        int modSpecificAdapterCount,
        boolean isBuiltinOnly
    ) {
    }
}