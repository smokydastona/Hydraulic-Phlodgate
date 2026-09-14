package org.geysermc.hydraulic.mixin.ext;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.geysermc.hydraulic.companion.CompanionSignalBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps scoreboard objective loading idempotent when a saved world contains duplicate
 * serialized entries. Minecraft's normal addObjective contract rejects duplicates, but
 * rejecting them during load prevents the server from recovering the world at all.
 */
@Mixin(Scoreboard.class)
public abstract class ScoreboardObjectiveMixin {
    @Inject(method = "addObjective", at = @At("HEAD"), cancellable = true)
    private void hydraulic$reuseExistingObjective(
        String name,
        ObjectiveCriteria criteria,
        Component displayName,
        ObjectiveCriteria.RenderType renderType,
        boolean displayAutoUpdate,
        NumberFormat numberFormat,
        CallbackInfoReturnable<Objective> callback
    ) {
        if (!CompanionSignalBridge.OBJECTIVE_NAME.equals(name)) {
            return;
        }
        Scoreboard scoreboard = (Scoreboard) (Object) this;
        Objective existing = scoreboard.getObjective(name);
        if (existing != null) {
            callback.setReturnValue(existing);
        }
    }
}
