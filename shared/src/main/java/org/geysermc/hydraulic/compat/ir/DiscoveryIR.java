package org.geysermc.hydraulic.compat.ir;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Discovery Intermediate Representation (IR).
 *
 * Represents pure, immutable facts discovered from Java mod roots, registries, datapacks,
 * and assets before Bedrock capability decisions or compatibility evaluations are made.
 *
 * Part of the Universal Resource Index Consolidation (P0.1).
 */
public record DiscoveryIR(
    @NotNull Identifier identifier,
    @NotNull ResourceKind kind,
    @NotNull String sourceModId,
    @NotNull String sourceRoot,
    @NotNull String relativePath,
    long fileSize,
    long lastModified,
    @NotNull String fingerprint,
    @NotNull Set<String> directDependencies,
    @NotNull Map<String, String> discoveredFacts
) {
    public DiscoveryIR {
        Objects.requireNonNull(identifier, "identifier");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(sourceModId, "sourceModId");
        Objects.requireNonNull(sourceRoot, "sourceRoot");
        Objects.requireNonNull(relativePath, "relativePath");
        Objects.requireNonNull(fingerprint, "fingerprint");
        directDependencies = Set.copyOf(directDependencies);
        discoveredFacts = Collections.unmodifiableMap(new LinkedHashMap<>(discoveredFacts));
    }

    public enum ResourceKind {
        BLOCKSTATE,
        ITEM_DEFINITION,
        ITEM_MODEL,
        MODEL,
        TEXTURE,
        ANIMATION,
        SOUND,
        LANG,
        RECIPE,
        TAG,
        LOOT_TABLE,
        ENTITY,
        BLOCK_ENTITY,
        MENU,
        DATA,
        CONFIG,
        PACK,
        DEPENDENCY,
        UNKNOWN
    }

    @NotNull
    public static DiscoveryIR of(
        @NotNull Identifier identifier,
        @NotNull ResourceKind kind,
        @NotNull String sourceModId,
        @NotNull String relativePath
    ) {
        return new DiscoveryIR(
            identifier,
            kind,
            sourceModId,
            "",
            relativePath,
            0L,
            0L,
            "",
            Set.of(),
            Map.of()
        );
    }

    @NotNull
    public DiscoveryIR withFact(@NotNull String key, @NotNull String value) {
        Map<String, String> updated = new LinkedHashMap<>(this.discoveredFacts);
        updated.put(key, value);
        return new DiscoveryIR(
            this.identifier,
            this.kind,
            this.sourceModId,
            this.sourceRoot,
            this.relativePath,
            this.fileSize,
            this.lastModified,
            this.fingerprint,
            this.directDependencies,
            updated
        );
    }

    @Nullable
    public String getFact(@NotNull String key) {
        return this.discoveredFacts.get(key);
    }

    public boolean hasFact(@NotNull String key) {
        return this.discoveredFacts.containsKey(key);
    }

    public boolean isFactTrue(@NotNull String key) {
        return "true".equalsIgnoreCase(this.discoveredFacts.get(key));
    }
}
