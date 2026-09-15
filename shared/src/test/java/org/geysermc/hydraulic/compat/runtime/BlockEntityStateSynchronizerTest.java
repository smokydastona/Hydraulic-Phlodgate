package org.geysermc.hydraulic.compat.runtime;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockEntityStateSynchronizerTest {
    @Test
    void comparesSerializedStateWithoutRetainingRuntimeObjects() {
        CompoundTag previous = new CompoundTag();
        previous.putInt("Progress", 1);
        CompoundTag unchanged = previous.copy();
        CompoundTag changed = previous.copy();
        changed.putInt("Progress", 2);

        assertFalse(BlockEntityStateSynchronizer.changed(previous, unchanged));
        assertTrue(BlockEntityStateSynchronizer.changed(previous, changed));
    }
}