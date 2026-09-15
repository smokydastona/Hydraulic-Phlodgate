package org.geysermc.hydraulic.mixin.ext;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.cloudburstmc.protocol.bedrock.packet.InteractPacket;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.bedrock.entity.player.BedrockInteractTranslator;
import org.geysermc.hydraulic.compat.CompatibilityRegistry;
import org.geysermc.hydraulic.compat.runtime.CompatibilityRuntimeDiagnostics;
import org.geysermc.hydraulic.compat.runtime.BedrockEntityActionRouter;
import org.geysermc.hydraulic.compat.runtime.EntityInteractionPromptResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BedrockInteractTranslator.class, remap = false)
public abstract class BedrockInteractTranslatorMixin {
    @Inject(
        method = "translate(Lorg/geysermc/geyser/session/GeyserSession;Lorg/cloudburstmc/protocol/bedrock/packet/InteractPacket;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void hydraulic$executeCompiledEntityAction(
        GeyserSession session,
        InteractPacket packet,
        CallbackInfo callbackInfo
    ) {
        if (BedrockEntityActionRouter.route(session, packet, CompatibilityRuntimeDiagnostics.currentRegistry())) {
            callbackInfo.cancel();
        }
    }

    @WrapOperation(
        method = "translate(Lorg/geysermc/geyser/session/GeyserSession;Lorg/cloudburstmc/protocol/bedrock/packet/InteractPacket;)V",
        at = @At(
            value = "INVOKE",
            target = "Lorg/geysermc/geyser/entity/type/Entity;updateInteractiveTag()V"
        )
    )
    private void hydraulic$applyEntityInteractionPrompt(
        Entity entity,
        Operation<Void> original,
        GeyserSession session,
        InteractPacket packet
    ) {
        original.call(entity);

        CompatibilityRegistry compatibilityRegistry = CompatibilityRuntimeDiagnostics.currentRegistry();
        EntityInteractionPromptResolver.apply(session, entity, compatibilityRegistry);
    }
}