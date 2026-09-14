package org.geysermc.hydraulic.mixin.ext;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.geysermc.hydraulic.compat.runtime.RuntimeLifecycleCoordinator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMenuLifecycleMixin {
    @Inject(method = "openMenu", at = @At("TAIL"))
    private void hydraulic$discoverRuntimeMenu(
        net.minecraft.world.MenuProvider menuProvider,
        CallbackInfoReturnable<java.util.OptionalInt> callbackInfo
    ) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || menu.getType() == null) {
            return;
        }
        var identifier = BuiltInRegistries.MENU.getKey(menu.getType());
        if (identifier != null) {
            RuntimeLifecycleCoordinator.discoverMenu(identifier, menu);
        }
    }
}