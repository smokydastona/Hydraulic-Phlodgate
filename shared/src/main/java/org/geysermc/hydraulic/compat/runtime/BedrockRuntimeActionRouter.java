package org.geysermc.hydraulic.compat.runtime;

import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.hydraulic.HydraulicImpl;
import org.geysermc.hydraulic.compat.CompatibilityRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BedrockRuntimeActionRouter {
    private static final Logger LOGGER = LoggerFactory.getLogger("HydraulicRuntimeActions");
    private static final int ITEM_USE_ON_BLOCK = 0;

    // Per-session dirty-state/sync pipeline so any resolved block-use mutation is delivered back
    // to the originating Bedrock client, not only recorded server-side.
    private static final Map<GeyserSession, SessionSync> SESSION_SYNC = new ConcurrentHashMap<>();

    private BedrockRuntimeActionRouter() {
    }

    @NotNull
    public static RuntimeActionResult route(
        @NotNull GeyserSession session,
        @NotNull InventoryTransactionPacket packet,
        @NotNull CompatibilityRegistry compatibilityRegistry
    ) {
        RuntimeTraceId traceId = RuntimeTraceId.create();
        ServerPlayer player = player(session);
        if (player == null) {
            SESSION_SYNC.remove(session);
            return new RuntimeActionResult(traceId, Status.TARGET_UNAVAILABLE, null, null, "No Java player for Bedrock session");
        }

        RuntimeTargetDiscovery discovery = RuntimeTargetDiscovery.forGeyserSession(compatibilityRegistry.dispatchTable(), session);
        RuntimeActionResult routed = route(packet, discovery, player.level().dimension().identifier().toString(), traceId);
        if (routed.status() != Status.TARGET_RESOLVED || routed.blockIdentifier() == null) {
            return routed;
        }

        var plan = compatibilityRegistry.dispatchTable().block(routed.blockIdentifier());
        BlockUseActionPlan action = plan == null ? null : BlockUseActionPlan.from(plan.inventoryFacts());
        if (action == null) {
            return routed;
        }

        SessionSync sync = sessionSync(session);
        RuntimeActionResult mutated = executeItemAction(routed, discovery, action, new PlayerHeldItemAccess(player), sync.dirtyStateTracker());
        if (mutated.status() == Status.MUTATED) {
            flushAndLog(sync);
        }
        return mutated;
    }

    @NotNull
    public static RuntimeActionResult executeBlockUse(
        @NotNull ServerPlayer player,
        @NotNull BlockPos blockPosition,
        @NotNull CompatibilityRegistry compatibilityRegistry,
        @NotNull RuntimeTraceId traceId
    ) {
        RuntimeTargetDiscovery.Position position = new RuntimeTargetDiscovery.Position(
            player.level().dimension().identifier().toString(),
            blockPosition.getX(),
            blockPosition.getY(),
            blockPosition.getZ()
        );
        RuntimeTargetDiscovery discovery = new RuntimeTargetDiscovery(
            compatibilityRegistry.dispatchTable(),
            new MinecraftRuntimeTargetSource(player.level())
        );
        RuntimeTargetDiscovery.Resolution resolution = discovery.discover(position, traceId);
        if (!resolution.resolved()) {
            return new RuntimeActionResult(
                traceId,
                resolution.status() == RuntimeTargetDiscovery.Status.TARGET_UNAVAILABLE ? Status.TARGET_UNAVAILABLE : Status.CAPABILITY_UNAVAILABLE,
                position,
                resolution.blockIdentifier(),
                resolution.reason()
            );
        }
        RuntimeActionResult routed = new RuntimeActionResult(traceId, Status.TARGET_RESOLVED, position, resolution.blockIdentifier(), null);
        var plan = compatibilityRegistry.dispatchTable().block(resolution.blockIdentifier());
        BlockUseActionPlan action = plan == null ? null : BlockUseActionPlan.from(plan.inventoryFacts());
        if (action == null) {
            return routed;
        }

        GeyserSession bedrockSession = bedrockSessionFor(player);
        SessionSync sync = bedrockSession != null ? sessionSync(bedrockSession) : null;
        RuntimeActionResult mutated = executeItemAction(routed, discovery, action, new PlayerHeldItemAccess(player), sync != null ? sync.dirtyStateTracker() : null);
        if (mutated.status() == Status.MUTATED && sync != null) {
            flushAndLog(sync);
        }
        return mutated;
    }

    @Nullable
    private static GeyserSession bedrockSessionFor(@NotNull ServerPlayer player) {
        try {
            GeyserConnection connection = GeyserApi.api().connectionByUuid(player.getUUID());
            return connection instanceof GeyserSession session ? session : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    @NotNull
    private static SessionSync sessionSync(@NotNull GeyserSession session) {
        return SESSION_SYNC.computeIfAbsent(session, s -> {
            DirtyStateTracker dirtyStateTracker = new DirtyStateTracker();
            SyncDispatcher dispatcher = new SyncDispatcher(dirtyStateTracker, new SyncPlanner(), new SyncEncoder(), new GeyserSyncTransport(s));
            return new SessionSync(dirtyStateTracker, dispatcher);
        });
    }

    private static void flushAndLog(@NotNull SessionSync sync) {
        for (SyncDeliveryResult result : sync.dispatcher().flush()) {
            if (!result.successfulHandoff() && result.status() != SyncDeliveryStatus.UNSUPPORTED) {
                LOGGER.warn("Runtime sync delivery failed for {} ({}): {}", result.change().blockIdentifier(), result.status(), result.reason());
            }
        }
    }

    private record SessionSync(@NotNull DirtyStateTracker dirtyStateTracker, @NotNull SyncDispatcher dispatcher) {
    }

    @NotNull
    static RuntimeActionResult route(
        @NotNull InventoryTransactionPacket packet,
        @NotNull RuntimeTargetDiscovery discovery,
        @NotNull String level,
        @NotNull RuntimeTraceId traceId
    ) {
        if (packet.getTransactionType() != InventoryTransactionType.ITEM_USE || packet.getActionType() != ITEM_USE_ON_BLOCK) {
            return new RuntimeActionResult(traceId, Status.IGNORED, null, null, "Inventory transaction is not a block item-use action");
        }
        Vector3i blockPosition = packet.getBlockPosition();
        if (blockPosition == null) {
            return new RuntimeActionResult(traceId, Status.TARGET_UNAVAILABLE, null, null, "Block item-use action has no block position");
        }

        RuntimeTargetDiscovery.Position position = new RuntimeTargetDiscovery.Position(
            level,
            blockPosition.getX(),
            blockPosition.getY(),
            blockPosition.getZ()
        );
        RuntimeTargetDiscovery.Resolution resolution = discovery.discover(position, traceId);
        RuntimeActionResult result = new RuntimeActionResult(
            traceId,
            switch (resolution.status()) {
                case RESOLVED -> Status.TARGET_RESOLVED;
                case TARGET_UNAVAILABLE -> Status.TARGET_UNAVAILABLE;
                case CAPABILITY_UNAVAILABLE -> Status.CAPABILITY_UNAVAILABLE;
            },
            position,
            resolution.blockIdentifier(),
            resolution.reason()
        );
        if (result.status() != Status.IGNORED) {
            LOGGER.debug("Runtime action {} routed with status {} at {} for {}", traceId.value(), result.status(), position.asKey(), result.blockIdentifier());
        }
        return result;
    }

    @NotNull
    static RuntimeActionResult executeItemAction(
        @NotNull RuntimeActionResult routed,
        @NotNull RuntimeTargetDiscovery discovery,
        @NotNull BlockUseActionPlan action,
        @NotNull HeldItemAccess heldItemAccess,
        @Nullable DirtyStateTracker dirtyStateTracker
    ) {
        if (routed.status() != Status.TARGET_RESOLVED || routed.position() == null || routed.blockIdentifier() == null) {
            return routed;
        }

        if (heldItemAccess.isSneaking() && action.extract() != null) {
            return executeExtractAction(routed, discovery, action.extract(), heldItemAccess, dirtyStateTracker);
        }

        TransferBridgeFactory.ItemStackView heldItem = heldItemAccess.heldItem();
        if (heldItem == null || heldItem.isEmpty() || heldItem.count() < action.count()) {
            return new RuntimeActionResult(routed.traceId(), Status.MUTATION_REJECTED, routed.position(), routed.blockIdentifier(), "Held item does not satisfy the compiled block-use action");
        }

        TransferResult transfer = discovery.transferItem(
            routed.position(),
            TransferDirection.INSERT,
            new TransferBridgeFactory.ItemStackView(heldItem.itemId(), action.count()),
            action.slot(),
            action.side(),
            dirtyStateTracker,
            routed.traceId()
        );
        if (!transfer.committed() || transfer.moved() != action.count()) {
            return new RuntimeActionResult(routed.traceId(), Status.MUTATION_REJECTED, routed.position(), routed.blockIdentifier(), transfer.failureReason());
        }

        heldItemAccess.consume(action.count());
        return new RuntimeActionResult(routed.traceId(), Status.MUTATED, routed.position(), routed.blockIdentifier(), null);
    }

    /**
     * Shift-click extraction counterpart to the primary insert action. The transferred count is
     * always delivered back to the player (inventory add, or a world drop if the inventory is
     * full) once the underlying transaction commits, so a resolved extraction never destroys the
     * removed stack even if the player's inventory cannot hold it.
     */
    @NotNull
    private static RuntimeActionResult executeExtractAction(
        @NotNull RuntimeActionResult routed,
        @NotNull RuntimeTargetDiscovery discovery,
        @NotNull BlockUseActionPlan.ExtractAction extract,
        @NotNull HeldItemAccess heldItemAccess,
        @Nullable DirtyStateTracker dirtyStateTracker
    ) {
        TransferBridgeFactory.ItemStackView requested = new TransferBridgeFactory.ItemStackView(extract.itemId(), extract.count());
        if (!heldItemAccess.canGive(requested)) {
            return new RuntimeActionResult(routed.traceId(), Status.MUTATION_REJECTED, routed.position(), routed.blockIdentifier(), "Player cannot receive the extracted item");
        }

        TransferResult transfer = discovery.transferItem(
            routed.position(),
            TransferDirection.EXTRACT,
            requested,
            extract.slot(),
            extract.side(),
            dirtyStateTracker,
            routed.traceId()
        );
        if (!transfer.committed() || transfer.moved() <= 0) {
            return new RuntimeActionResult(routed.traceId(), Status.MUTATION_REJECTED, routed.position(), routed.blockIdentifier(), transfer.failureReason());
        }

        TransferBridgeFactory.ItemStackView delivered = new TransferBridgeFactory.ItemStackView(extract.itemId(), transfer.moved());
        if (!heldItemAccess.give(delivered)) {
            return new RuntimeActionResult(routed.traceId(), Status.MUTATION_REJECTED, routed.position(), routed.blockIdentifier(), "Extracted item could not be delivered to the player");
        }
        return new RuntimeActionResult(routed.traceId(), Status.MUTATED, routed.position(), routed.blockIdentifier(), null);
    }

    @Nullable
    private static ServerPlayer player(@NotNull GeyserSession session) {
        try {
            return HydraulicImpl.instance().server().getPlayerList().getPlayer(session.javaUuid());
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    public enum Status {
        IGNORED,
        TARGET_RESOLVED,
        TARGET_UNAVAILABLE,
        CAPABILITY_UNAVAILABLE,
        MUTATED,
        MUTATION_REJECTED
    }

    public record RuntimeActionResult(
        @NotNull RuntimeTraceId traceId,
        @NotNull Status status,
        @Nullable RuntimeTargetDiscovery.Position position,
        @Nullable Identifier blockIdentifier,
        @Nullable String reason
    ) {
    }

    interface HeldItemAccess {
        @Nullable TransferBridgeFactory.ItemStackView heldItem();

        void consume(int count);

        default boolean isSneaking() {
            return false;
        }

        default boolean canGive(@NotNull TransferBridgeFactory.ItemStackView item) {
            return true;
        }

        default boolean give(@NotNull TransferBridgeFactory.ItemStackView item) {
            return false;
        }
    }

    private record PlayerHeldItemAccess(@NotNull ServerPlayer player) implements HeldItemAccess {
        @Override
        public TransferBridgeFactory.ItemStackView heldItem() {
            ItemStack stack = this.player.getMainHandItem();
            Identifier identifier = BuiltInRegistries.ITEM.getKey(stack.getItem());
            return stack.isEmpty() || identifier == null
                ? null
                : new TransferBridgeFactory.ItemStackView(identifier.toString(), stack.getCount());
        }

        @Override
        public void consume(int count) {
            if (!this.player.hasInfiniteMaterials()) {
                this.player.getMainHandItem().shrink(count);
            }
            this.player.containerMenu.broadcastChanges();
        }

        @Override
        public boolean isSneaking() {
            return this.player.isShiftKeyDown();
        }

        @Override
        public boolean canGive(@NotNull TransferBridgeFactory.ItemStackView item) {
            if (item.isEmpty()) {
                return false;
            }
            try {
                Identifier identifier = Identifier.parse(item.itemId());
                Item resolved = BuiltInRegistries.ITEM.getValue(identifier);
                return BuiltInRegistries.ITEM.getKey(resolved).equals(identifier);
            } catch (RuntimeException ignored) {
                return false;
            }
        }

        @Override
        public boolean give(@NotNull TransferBridgeFactory.ItemStackView item) {
            if (!canGive(item)) {
                return false;
            }
            Item resolved = BuiltInRegistries.ITEM.getValue(Identifier.parse(item.itemId()));
            ItemStack stack = new ItemStack(resolved, item.count());
            if (!this.player.getInventory().add(stack)) {
                this.player.drop(stack, false);
            }
            this.player.containerMenu.broadcastChanges();
            return true;
        }
    }
}
