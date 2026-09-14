package org.geysermc.hydraulic.mixin.ext;

import net.minecraft.world.level.block.entity.BlockEntity;
import org.geysermc.hydraulic.compat.runtime.RuntimeLifecycleCoordinator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntity.class)
public abstract class BlockEntityLifecycleMixin {
    @Inject(method = "setLevel", at = @At("TAIL"))
    private void hydraulic$discoverRuntimeContract(CallbackInfo callbackInfo) {
        BlockEntity blockEntity = (BlockEntity) (Object) this;
        if (blockEntity.getLevel() == null) {
            RuntimeLifecycleCoordinator.unbindBlockEntity(blockEntity);
            return;
        }
        RuntimeLifecycleCoordinator.discoverBlockEntity(blockEntity);
    }

    @Inject(method = "setRemoved", at = @At("TAIL"))
    private void hydraulic$unbindRuntimeContract(CallbackInfo callbackInfo) {
        RuntimeLifecycleCoordinator.unbindBlockEntity((BlockEntity) (Object) this);
    }

    @Inject(method = "clearRemoved", at = @At("TAIL"))
    private void hydraulic$rebindRuntimeContract(CallbackInfo callbackInfo) {
        RuntimeLifecycleCoordinator.discoverBlockEntity((BlockEntity) (Object) this);
    }
}