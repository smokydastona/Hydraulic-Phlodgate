package org.geysermc.hydraulic.compat.runtime;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MultiBlockHighlightOverlay {
    public enum HighlightStatus {
        FORMED,
        UNFORMED,
        INVALID
    }

    public record HighlightBox(
        @NotNull String positionKey,
        int minX, int minY, int minZ,
        int maxX, int maxY, int maxZ,
        @NotNull HighlightStatus status,
        @Nullable String message
    ) {
        public HighlightBox {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("Minimum coordinates cannot exceed maximum coordinates");
            }
        }

        public int volume() {
            return (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        }
    }

    private MultiBlockHighlightOverlay() {
    }

    @NotNull
    public static StateChangeSet.FieldChange createHighlightStateChange(
        @NotNull Identifier blockIdentifier,
        @NotNull HighlightBox box
    ) {
        String field = "multiblock.highlight." + box.positionKey();
        String before = "none";
        String after = box.status().name().toLowerCase() + ":" + box.minX() + "," + box.minY() + "," + box.minZ() + "->" + box.maxX() + "," + box.maxY() + "," + box.maxZ();
        return new StateChangeSet.FieldChange(blockIdentifier, field, before, after);
    }
}
