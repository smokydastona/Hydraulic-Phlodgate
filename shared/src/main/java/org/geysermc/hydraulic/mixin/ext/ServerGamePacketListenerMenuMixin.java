package org.geysermc.hydraulic.mixin.ext;

import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.geysermc.hydraulic.compat.runtime.BedrockMenuActionRouter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Wraps authoritative Java menu packet handling for Bedrock-backed players. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMenuMixin {
    @Unique
    private static final Logger HYDRAULIC_MENU_LOGGER = LoggerFactory.getLogger("HydraulicMenuActions");

    @Shadow
    public ServerPlayer player;

    @Unique
    private BedrockMenuActionRouter.ActionContext hydraulic$clickContext;
    @Unique
    private BedrockMenuActionRouter.ActionContext hydraulic$buttonContext;

    @Inject(method = "handleContainerClick", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true)
    private void hydraulic$validateMenuClick(ServerboundContainerClickPacket packet, CallbackInfo callback) {
        try {
            BedrockMenuActionRouter.Routing routing = BedrockMenuActionRouter.beforeClick(this.player, packet);
            this.hydraulic$clickContext = routing.context();
            if (routing.bedrock() && !routing.accepted()) {
                callback.cancel();
            }
        } catch (Throwable throwable) {
            this.hydraulic$clickContext = null;
            HYDRAULIC_MENU_LOGGER.warn("Menu click validation failed; falling back to vanilla handling", throwable);
        }
    }

    @Inject(method = "handleContainerClick", at = @At("RETURN"))
    private void hydraulic$completeMenuClick(ServerboundContainerClickPacket packet, CallbackInfo callback) {
        BedrockMenuActionRouter.ActionContext context = this.hydraulic$clickContext;
        this.hydraulic$clickContext = null;
        try {
            BedrockMenuActionRouter.afterAction(this.player, context);
        } catch (Throwable throwable) {
            HYDRAULIC_MENU_LOGGER.warn("Menu click synchronization failed after Java handling", throwable);
        }
    }

    @Inject(method = "handleContainerButtonClick", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true)
    private void hydraulic$validateMenuButton(ServerboundContainerButtonClickPacket packet, CallbackInfo callback) {
        try {
            BedrockMenuActionRouter.Routing routing = BedrockMenuActionRouter.beforeButton(this.player, packet);
            this.hydraulic$buttonContext = routing.context();
            if (routing.bedrock() && !routing.accepted()) {
                callback.cancel();
            }
        } catch (Throwable throwable) {
            this.hydraulic$buttonContext = null;
            HYDRAULIC_MENU_LOGGER.warn("Menu button validation failed; falling back to vanilla handling", throwable);
        }
    }

    @Inject(method = "handleContainerButtonClick", at = @At("RETURN"))
    private void hydraulic$completeMenuButton(ServerboundContainerButtonClickPacket packet, CallbackInfo callback) {
        BedrockMenuActionRouter.ActionContext context = this.hydraulic$buttonContext;
        this.hydraulic$buttonContext = null;
        try {
            BedrockMenuActionRouter.afterAction(this.player, context);
        } catch (Throwable throwable) {
            HYDRAULIC_MENU_LOGGER.warn("Menu button synchronization failed after Java handling", throwable);
        }
    }

    @Inject(method = "handleContainerClose", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER))
    private void hydraulic$closeMenuSession(ServerboundContainerClosePacket packet, CallbackInfo callback) {
        BedrockMenuActionRouter.close(this.player);
    }

    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void hydraulic$disconnectMenuSession(DisconnectionDetails details, CallbackInfo callback) {
        BedrockMenuActionRouter.close(this.player);
    }
}
