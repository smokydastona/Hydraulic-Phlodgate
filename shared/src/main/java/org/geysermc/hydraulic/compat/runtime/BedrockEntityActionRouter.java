package org.geysermc.hydraulic.compat.runtime;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import org.cloudburstmc.protocol.bedrock.packet.InteractPacket;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.hydraulic.HydraulicImpl;
import org.geysermc.hydraulic.compat.CompatibilityRegistry;
import org.geysermc.hydraulic.compat.ir.CompiledCompatibilityPlan;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Routes only explicitly compiled Bedrock entity actions to the authoritative Java server. */
public final class BedrockEntityActionRouter {
    private static final Logger LOGGER = LoggerFactory.getLogger("HydraulicEntityRuntime");

    private BedrockEntityActionRouter() {
    }

    public static boolean route(
        @NotNull GeyserSession session,
        @NotNull InteractPacket packet,
        @NotNull CompatibilityRegistry compatibilityRegistry
    ) {
        try {
            Entity geyserEntity = packet.getRuntimeEntityId() == session.getPlayerEntity().geyserId()
                ? session.getPlayerEntity()
                : session.getEntityCache().getEntityByGeyserId(packet.getRuntimeEntityId());
            if (geyserEntity == null) {
                return false;
            }

            String javaIdentifier = CompatibilityRuntimeDiagnostics.resolveJavaEntityIdentifier(session, geyserEntity);
            if (javaIdentifier == null) {
                return false;
            }
            CompiledCompatibilityPlan plan = compatibilityRegistry.dispatchTable().entity(net.minecraft.resources.Identifier.parse(javaIdentifier));
            EntityInteractionActionPlan actionPlan = plan == null ? null : EntityInteractionActionPlan.fromFacts(plan.inventoryFacts());
            if (actionPlan == null || !matches(actionPlan.action(), packet.getAction())) {
                return false;
            }

            ServerPlayer player = HydraulicImpl.instance().server().getPlayerList().getPlayer(session.javaUuid());
            if (player == null) {
                return false;
            }
            net.minecraft.world.entity.Entity target = player.level().getEntity(geyserEntity.getEntityId());
            if (target == null || target == player || !player.isWithinEntityInteractionRange(target, player.entityInteractionRange())) {
                return false;
            }
            if (actionPlan.requiredItem() != null) {
                InteractionHand requiredHand = "off_hand".equals(actionPlan.hand()) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                String heldItem = BuiltInRegistries.ITEM.getKey(player.getItemInHand(requiredHand).getItem()).toString();
                if (!actionPlan.requiredItem().equals(heldItem)) {
                    return false;
                }
            }

            HydraulicImpl.instance().server().execute(() -> execute(player, target, actionPlan));
            return true;
        } catch (Throwable throwable) {
            LOGGER.warn("Entity action routing failed; allowing normal Geyser handling", throwable);
            return false;
        }
    }

    private static boolean matches(@NotNull EntityInteractionActionPlan.Action action, @NotNull InteractPacket.Action packetAction) {
        return switch (action) {
            case USE, MOUNT -> packetAction == InteractPacket.Action.INTERACT;
            case ATTACK -> packetAction == InteractPacket.Action.DAMAGE;
            case DISMOUNT -> packetAction == InteractPacket.Action.LEAVE_VEHICLE;
        };
    }

    private static void execute(@NotNull ServerPlayer player, @NotNull net.minecraft.world.entity.Entity target, @NotNull EntityInteractionActionPlan plan) {
        InteractionHand hand = "off_hand".equals(plan.hand()) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        switch (plan.action()) {
            case USE -> target.interact(player, hand, target.position());
            case ATTACK -> player.attack(target);
            case MOUNT -> player.startRiding(target, true, true);
            case DISMOUNT -> {
                if (player.isPassenger()) {
                    player.stopRiding();
                }
            }
        }
    }
}