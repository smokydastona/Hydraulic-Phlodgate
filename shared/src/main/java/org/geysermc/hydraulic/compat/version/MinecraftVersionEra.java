package org.geysermc.hydraulic.compat.version;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Universal version identifier across Minecraft Java, Bedrock protocol, and schema eras.
 * Inspired by ViaVersion/ViaBackwards protocol version indexing and Retromod version bounds.
 */
public final class MinecraftVersionEra implements Comparable<MinecraftVersionEra> {
    private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:-.*)?$");

    public static final MinecraftVersionEra V1_12_2 = new MinecraftVersionEra(1, 12, 2, "1.12.2", 340, 282, EraType.LEGACY_PRE_FLATTENING);
    public static final MinecraftVersionEra V1_16_5 = new MinecraftVersionEra(1, 16, 5, "1.16.5", 754, 428, EraType.POST_FLATTENING_LEGACY);
    public static final MinecraftVersionEra V1_20_1 = new MinecraftVersionEra(1, 20, 1, "1.20.1", 763, 589, EraType.MODERN_PRE_COMPONENTS);
    public static final MinecraftVersionEra V1_20_4 = new MinecraftVersionEra(1, 20, 4, "1.20.4", 765, 630, EraType.MODERN_PRE_COMPONENTS);
    public static final MinecraftVersionEra V1_20_5 = new MinecraftVersionEra(1, 20, 5, "1.20.5", 766, 671, EraType.MODERN_COMPONENTS);
    public static final MinecraftVersionEra V1_21_0 = new MinecraftVersionEra(1, 21, 0, "1.21.0", 767, 685, EraType.MODERN_COMPONENTS);
    public static final MinecraftVersionEra V1_21_4 = new MinecraftVersionEra(1, 21, 4, "1.21.4", 768, 766, EraType.MODERN_COMPONENTS);
    public static final MinecraftVersionEra V26_2 = new MinecraftVersionEra(26, 2, 0, "26.2", 769, 786, EraType.LATEST_ACTIVE);

    private static final Map<String, MinecraftVersionEra> KNOWN_VERSIONS;

    static {
        Map<String, MinecraftVersionEra> map = new HashMap<>();
        register(map, V1_12_2);
        register(map, V1_16_5);
        register(map, V1_20_1);
        register(map, V1_20_4);
        register(map, V1_20_5);
        register(map, V1_21_0);
        register(map, V1_21_4);
        register(map, V26_2);
        KNOWN_VERSIONS = Collections.unmodifiableMap(map);
    }

    private static void register(Map<String, MinecraftVersionEra> map, MinecraftVersionEra era) {
        map.put(era.rawName(), era);
        map.put(era.major() + "." + era.minor() + (era.patch() > 0 ? "." + era.patch() : ""), era);
    }

    public enum EraType {
        LEGACY_PRE_FLATTENING,
        POST_FLATTENING_LEGACY,
        MODERN_PRE_COMPONENTS,
        MODERN_COMPONENTS,
        LATEST_ACTIVE
    }

    private final int major;
    private final int minor;
    private final int patch;
    private final String rawName;
    private final int javaProtocolVersion;
    private final int bedrockProtocolVersion;
    private final EraType eraType;

    public MinecraftVersionEra(int major, int minor, int patch, @NotNull String rawName, int javaProtocolVersion, int bedrockProtocolVersion, @NotNull EraType eraType) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.rawName = Objects.requireNonNull(rawName, "rawName");
        this.javaProtocolVersion = javaProtocolVersion;
        this.bedrockProtocolVersion = bedrockProtocolVersion;
        this.eraType = Objects.requireNonNull(eraType, "eraType");
    }

    public int major() {
        return major;
    }

    public int minor() {
        return minor;
    }

    public int patch() {
        return patch;
    }

    @NotNull
    public String rawName() {
        return rawName;
    }

    public int javaProtocolVersion() {
        return javaProtocolVersion;
    }

    public int bedrockProtocolVersion() {
        return bedrockProtocolVersion;
    }

    @NotNull
    public EraType eraType() {
        return eraType;
    }

    public boolean isLegacy() {
        return eraType == EraType.LEGACY_PRE_FLATTENING || eraType == EraType.POST_FLATTENING_LEGACY;
    }

    public boolean usesComponents() {
        return eraType == EraType.MODERN_COMPONENTS || eraType == EraType.LATEST_ACTIVE;
    }

    @NotNull
    public static MinecraftVersionEra parse(@Nullable String versionStr) {
        if (versionStr == null || versionStr.trim().isEmpty()) {
            return V26_2;
        }
        String clean = versionStr.trim();
        if (KNOWN_VERSIONS.containsKey(clean)) {
            return KNOWN_VERSIONS.get(clean);
        }

        Matcher matcher = VERSION_PATTERN.matcher(clean);
        if (matcher.matches()) {
            int maj = Integer.parseInt(matcher.group(1));
            int min = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0;
            int pat = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;

            EraType type;
            if (maj == 1 && min <= 12) {
                type = EraType.LEGACY_PRE_FLATTENING;
            } else if (maj == 1 && min <= 19) {
                type = EraType.POST_FLATTENING_LEGACY;
            } else if (maj == 1 && min == 20 && pat < 5) {
                type = EraType.MODERN_PRE_COMPONENTS;
            } else if (maj >= 26 || (maj == 1 && (min >= 21 || (min == 20 && pat >= 5)))) {
                type = EraType.MODERN_COMPONENTS;
            } else {
                type = EraType.LATEST_ACTIVE;
            }
            return new MinecraftVersionEra(maj, min, pat, clean, 0, 0, type);
        }

        return new MinecraftVersionEra(26, 2, 0, clean, 769, 786, EraType.LATEST_ACTIVE);
    }

    @Override
    public int compareTo(@NotNull MinecraftVersionEra o) {
        if (this.major != o.major) return Integer.compare(this.major, o.major);
        if (this.minor != o.minor) return Integer.compare(this.minor, o.minor);
        return Integer.compare(this.patch, o.patch);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MinecraftVersionEra that = (MinecraftVersionEra) o;
        return major == that.major && minor == that.minor && patch == that.patch;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch);
    }

    @Override
    public String toString() {
        return "MinecraftVersionEra[" + rawName + ", javaProto=" + javaProtocolVersion + ", bedrockProto=" + bedrockProtocolVersion + "]";
    }
}
