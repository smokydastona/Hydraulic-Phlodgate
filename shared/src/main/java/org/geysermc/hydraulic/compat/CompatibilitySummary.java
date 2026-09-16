package org.geysermc.hydraulic.compat;

import org.geysermc.hydraulic.compat.model.SupportLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Compact, human-readable projection of the detailed compatibility report.
 */
public record CompatibilitySummary(
    @NotNull String generatedAt,
    int modCount,
    int objectCount,
    @NotNull Map<String, Integer> overallLevels,
    @NotNull Map<String, ModSummary> mods
) {
    public CompatibilitySummary {
        overallLevels = Map.copyOf(new LinkedHashMap<>(overallLevels));
        mods = Map.copyOf(new LinkedHashMap<>(mods));
    }

    @NotNull
    public static CompatibilitySummary from(@NotNull CompatibilityReport report) {
        Map<String, Integer> overallLevels = new LinkedHashMap<>();
        Map<String, ModSummary> mods = new LinkedHashMap<>();
        int objectCount = 0;
        for (Map.Entry<String, CompatibilityProfile> entry : report.mods().entrySet()) {
            CompatibilityProfile profile = entry.getValue();
            int profileObjectCount = profile.objects().size();
            objectCount += profileObjectCount;
            overallLevels.merge(profile.overallLevel().name(), 1, Integer::sum);
            mods.put(entry.getKey(), new ModSummary(
                profile.modId(),
                profile.overallLevel(),
                profile.overallStatus(),
                profile.overallScore(),
                profileObjectCount,
                profile.levelCounts(),
                report.packValidation().get(entry.getKey())
            ));
        }
        return new CompatibilitySummary(report.generatedAt(), mods.size(), objectCount, overallLevels, mods);
    }

    public record ModSummary(
        @NotNull String modId,
        @NotNull SupportLevel overallLevel,
        @NotNull CompatibilityStatus overallStatus,
        int overallScore,
        int objectCount,
        @NotNull Map<String, Integer> levelCounts,
        @Nullable CompatibilityReport.PackValidationSummary packValidation
    ) {
        public ModSummary {
            levelCounts = Map.copyOf(new LinkedHashMap<>(levelCounts));
        }
    }
}
