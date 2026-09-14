package org.geysermc.hydraulic.mixin.ext;

import net.minecraft.world.level.block.entity.BlockEntity;
import org.geysermc.hydraulic.compat.runtime.RuntimeLifecycleCoordinator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class BoundTickingBlockEntityMixin {
    @Shadow
    @Final
    private BlockEntity blockEntity;

    @Inject(method = "tick", at = @At("TAIL"))
    private void hydraulic$flushMachineState(CallbackInfo callbackInfo) {
        RuntimeLifecycleCoordinator.tickBlockEntity(this.blockEntity);
    }
}