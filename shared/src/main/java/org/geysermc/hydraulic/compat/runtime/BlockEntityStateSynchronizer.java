package org.geysermc.hydraulic.compat.runtime;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * Observes authoritative serialized block-entity state without retaining the live object as state.
 * Runtime snapshots are disposable and are rebuilt after unload, replacement, or restart.
 */
public final class BlockEntityStateSynchronizer {
    private CompoundTag previousState;

    public void observe(
        @NotNull BlockEntity blockEntity,
        @NotNull Identifier blockIdentifier,
        @NotNull ServerLevel level,
        @NotNull SessionAutoFlushCoordinator autoFlushCoordinator
    ) {
        CompoundTag currentState = blockEntity.saveWithoutMetadata(level.registryAccess());
        if (this.previousState == null) {
            this.previousState = currentState.copy();
            return;
        }
        if (this.previousState.equals(currentState)) {
            return;
        }

        StateChangeSet changes = new StateChangeSet(List.of(new StateChangeSet.FieldChange(
            blockIdentifier,
            "block_entity.state",
            this.previousState.copy(),
            currentState.copy()
        )));
        this.previousState = currentState.copy();
        autoFlushCoordinator.autoFlushStateDeltas(changes, level, blockEntity.getBlockPos());
    }

    public void reset() {
        this.previousState = null;
    }

    static boolean changed(@NotNull CompoundTag previous, @NotNull CompoundTag current) {
        return !Objects.equals(previous, current);
    }
}