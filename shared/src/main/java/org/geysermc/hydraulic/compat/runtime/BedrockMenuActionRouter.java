package org.geysermc.hydraulic.compat.runtime;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.hydraulic.compat.CompatibilityRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Routes Geyser-originated Java menu packets through live-session validation and authoritative resync. */
public final class BedrockMenuActionRouter {
    private static final int CLICKED_OUTSIDE = -999;
    private static final int MAX_BUTTON_ID = 65_535;
    private static final Map<UUID, MenuSession> SESSIONS = new ConcurrentHashMap<>();
    private static final SessionAutoFlushCoordinator MENU_SYNC = new SessionAutoFlushCoordinator();

    private BedrockMenuActionRouter() {
    }

    public static void open(
        @NotNull ServerPlayer player,
        @NotNull Identifier menuIdentifier,
        @NotNull AbstractContainerMenu menu,
        @Nullable CompatibilityRegistry compatibilityRegistry
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(menuIdentifier, "menuIdentifier");
        Objects.requireNonNull(menu, "menu");
        if (bedrockSession(player) == null) {
            return;
        }
        var plan = compatibilityRegistry == null ? null : compatibilityRegistry.dispatchTable().menu(menuIdentifier);
        MenuActionPlan actionPlan = plan == null ? new MenuActionPlan(Map.of()) : MenuActionPlan.from(plan);
        SESSIONS.put(player.getUUID(), new MenuSession(menuIdentifier, menu.containerId, new WeakReference<>(menu), actionPlan));
    }

    public static void close(@NotNull ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
        GeyserSession session = bedrockSession(player);
        if (session != null) {
            MENU_SYNC.unregisterSession(session);
        }
    }

    @NotNull
    public static Routing beforeClick(@NotNull ServerPlayer player, @NotNull ServerboundContainerClickPacket packet) {
        GeyserSession geyserSession = bedrockSession(player);
        if (geyserSession == null) {
            return Routing.passthrough();
        }
        AbstractContainerMenu menu = player.containerMenu;
        MenuSession session = SESSIONS.get(player.getUUID());
        String rejection = validate(session, menu, packet, player);
        if (rejection != null) {
            resync(menu, geyserSession);
            return Routing.rejected(rejection);
        }
        MenuSnapshot before = snapshot(menu);
        MenuActionType action = action(packet.containerInput(), packet.buttonNum(), before, packet.slotNum());
        if (action == null) {
            resync(menu, geyserSession);
            return Routing.rejected("Unsupported menu input " + packet.containerInput());
        }
        return Routing.accepted(new ActionContext(
            session.menuIdentifier(), menu.containerId, action, packet.slotNum(), packet.buttonNum(),
            packet.stateId(), before, RuntimeTraceId.create(), geyserSession
        ));
    }

    @NotNull
    public static Routing beforeButton(@NotNull ServerPlayer player, @NotNull ServerboundContainerButtonClickPacket packet) {
        GeyserSession geyserSession = bedrockSession(player);
        if (geyserSession == null) {
            return Routing.passthrough();
        }
        AbstractContainerMenu menu = player.containerMenu;
        MenuSession session = SESSIONS.get(player.getUUID());
        if (session == null || session.menu().get() != menu || session.containerId() != packet.containerId()) {
            resync(menu, geyserSession);
            return Routing.rejected("Menu button targets a stale or unknown session");
        }
        if (!menu.stillValid(player) || packet.buttonId() < 0 || packet.buttonId() > MAX_BUTTON_ID) {
            resync(menu, geyserSession);
            return Routing.rejected("Menu button failed authoritative validation");
        }
        return Routing.accepted(new ActionContext(
            session.menuIdentifier(), menu.containerId, session.actionPlan().action(packet.buttonId()), -1, packet.buttonId(),
            menu.getStateId(), snapshot(menu), RuntimeTraceId.create(), geyserSession
        ));
    }

    @Nullable
    public static MenuTransaction afterAction(@NotNull ServerPlayer player, @Nullable ActionContext context) {
        if (context == null) {
            return null;
        }
        AbstractContainerMenu menu = player.containerMenu;
        MenuSession session = SESSIONS.get(player.getUUID());
        if (session == null || session.menu().get() != menu || menu.containerId != context.containerId()) {
            resync(menu, context.geyserSession());
            return null;
        }
        MenuSnapshot after = snapshot(menu);
        StateChangeSet changes = diff(context.menuIdentifier(), context.before(), after, context.traceId());
        MenuTransaction transaction = transaction(context, after, changes);
        if (!changes.changes().isEmpty()) {
            MENU_SYNC.autoFlushMenuStateDeltas(context.geyserSession(), changes, menu::broadcastFullState);
        } else if (context.action() == MenuActionType.BUTTON || context.action() == MenuActionType.TOGGLE) {
            MENU_SYNC.autoFlushMenuStateDeltas(context.geyserSession(), StateChangeSet.empty(), menu::broadcastFullState);
        }
        return transaction;
    }

    @Nullable
    private static String validate(
        @Nullable MenuSession session,
        @NotNull AbstractContainerMenu menu,
        @NotNull ServerboundContainerClickPacket packet,
        @NotNull ServerPlayer player
    ) {
        if (session == null || session.menu().get() != menu || session.containerId() != packet.containerId()) {
            return "Menu click targets a stale or unknown session";
        }
        if (!menu.stillValid(player)) {
            return "Menu target is no longer valid";
        }
        if (packet.stateId() != menu.getStateId()) {
            return "Menu click uses stale state " + packet.stateId() + " (expected " + menu.getStateId() + ")";
        }
        int slot = packet.slotNum();
        if (slot != CLICKED_OUTSIDE && (slot < 0 || slot >= menu.slots.size())) {
            return "Menu slot is outside the authoritative menu";
        }
        if (slot == CLICKED_OUTSIDE && packet.containerInput() != ContainerInput.PICKUP
            && packet.containerInput() != ContainerInput.THROW
            && packet.containerInput() != ContainerInput.QUICK_CRAFT) {
            return "Menu action cannot target the outside slot";
        }
        for (int changedSlot : packet.changedSlots().keySet()) {
            if (changedSlot < 0 || changedSlot >= menu.slots.size()) {
                return "Changed-slot claim is outside the authoritative menu";
            }
        }
        return validateButton(packet.containerInput(), packet.buttonNum(), player);
    }

    @Nullable
    static String validateButton(@NotNull ContainerInput input, int button, @Nullable net.minecraft.world.entity.player.Player player) {
        return switch (input) {
            case PICKUP, QUICK_MOVE, THROW, PICKUP_ALL -> button >= 0 && button <= 1 ? null : "Invalid menu button";
            case SWAP -> button >= 0 && (button <= 8 || button == 40) ? null : "Invalid hotbar swap button";
            case QUICK_CRAFT -> {
                int header = AbstractContainerMenu.getQuickcraftHeader(button);
                int type = AbstractContainerMenu.getQuickcraftType(button);
                yield header >= 0 && header <= 2 && player != null && AbstractContainerMenu.isValidQuickcraftType(type, player)
                    ? null
                    : "Invalid drag stage or type";
            }
            case CLONE -> "Creative clone is not a normalized menu action";
        };
    }

    @Nullable
    static MenuActionType action(@NotNull ContainerInput input, int button, @NotNull MenuSnapshot snapshot, int slot) {
        return switch (input) {
            case PICKUP -> {
                if (button == 1) {
                    yield MenuActionType.SPLIT;
                }
                MenuStack carried = snapshot.carried();
                MenuStack target = slot >= 0 && slot < snapshot.slots().size() ? snapshot.slots().get(slot) : MenuStack.EMPTY;
                if (carried.empty()) yield MenuActionType.PICKUP;
                if (target.empty()) yield MenuActionType.PLACE;
                yield MenuActionType.SWAP;
            }
            case QUICK_MOVE -> MenuActionType.QUICK_MOVE;
            case SWAP -> MenuActionType.HOTBAR_SWAP;
            case THROW -> MenuActionType.DROP;
            case QUICK_CRAFT -> MenuActionType.DRAG;
            case PICKUP_ALL -> MenuActionType.PICKUP;
            case CLONE -> null;
        };
    }

    @NotNull
    private static MenuSnapshot snapshot(@NotNull AbstractContainerMenu menu) {
        List<MenuStack> slots = new ArrayList<>(menu.slots.size());
        for (var slot : menu.slots) {
            slots.add(stackValue(slot.getItem()));
        }
        return new MenuSnapshot(List.copyOf(slots), stackValue(menu.getCarried()), menu.getStateId());
    }

    @NotNull
    static StateChangeSet diff(
        @NotNull Identifier menuIdentifier,
        @NotNull MenuSnapshot before,
        @NotNull MenuSnapshot after,
        @NotNull RuntimeTraceId traceId
    ) {
        List<StateChangeSet.FieldChange> changes = new ArrayList<>();
        int size = Math.max(before.slots().size(), after.slots().size());
        for (int slot = 0; slot < size; slot++) {
            MenuStack oldStack = slot < before.slots().size() ? before.slots().get(slot) : MenuStack.EMPTY;
            MenuStack newStack = slot < after.slots().size() ? after.slots().get(slot) : MenuStack.EMPTY;
            if (!oldStack.equals(newStack)) {
                changes.add(new StateChangeSet.FieldChange(
                    menuIdentifier, "inventory.slot." + slot, oldStack.stackView(), newStack.stackView(), traceId
                ));
            }
        }
        if (!before.carried().equals(after.carried())) {
            changes.add(new StateChangeSet.FieldChange(
                menuIdentifier, "menu.carried", before.carried().stackView(), after.carried().stackView(), traceId
            ));
        }
        return new StateChangeSet(changes, traceId);
    }

    @NotNull
    private static MenuStack stackValue(@NotNull ItemStack stack) {
        if (stack.isEmpty()) {
            return MenuStack.EMPTY;
        }
        Identifier identifier = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return new MenuStack(
            identifier == null ? "minecraft:air" : identifier.toString(),
            stack.getCount(),
            ItemStack.hashItemAndComponents(stack)
        );
    }

    private static void resync(@NotNull AbstractContainerMenu menu, @NotNull GeyserSession session) {
        MENU_SYNC.autoFlushMenuStateDeltas(session, StateChangeSet.empty(), menu::broadcastFullState);
    }

    @NotNull
    static MenuTransaction transaction(
        @NotNull ActionContext context,
        @NotNull MenuSnapshot after,
        @NotNull StateChangeSet changes
    ) {
        return transaction(
            context.action(), context.slot(), context.button(), context.expectedState(),
            context.before(), after, changes, context.traceId()
        );
    }

    @NotNull
    static MenuTransaction transaction(
        @NotNull MenuActionType action,
        int slot,
        int button,
        int expectedState,
        @NotNull MenuSnapshot before,
        @NotNull MenuSnapshot after,
        @NotNull StateChangeSet changes,
        @NotNull RuntimeTraceId traceId
    ) {
        int source = switch (action) {
            case PLACE, DRAG, BUTTON, TOGGLE -> -1;
            default -> slot;
        };
        int destination = switch (action) {
            case PLACE, DRAG -> slot;
            case HOTBAR_SWAP -> button;
            case QUICK_MOVE -> -2;
            default -> -1;
        };
        TransactionResult result = changes.changes().isEmpty() ? TransactionResult.NO_CHANGE : TransactionResult.COMMITTED;
        return new MenuTransaction(
            source, destination, action, expectedState, before, after,
            action.name(), result, changes, traceId
        );
    }

    @Nullable
    private static GeyserSession bedrockSession(@NotNull ServerPlayer player) {
        try {
            GeyserConnection connection = GeyserApi.api().connectionByUuid(player.getUUID());
            return connection instanceof GeyserSession session ? session : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public enum MenuActionType {
        PICKUP, PLACE, SPLIT, SWAP, QUICK_MOVE, DRAG, DROP, HOTBAR_SWAP, BUTTON, TOGGLE
    }

    public record MenuStack(@NotNull String itemId, int count, int componentHash) {
        static final MenuStack EMPTY = new MenuStack("minecraft:air", 0, 0);
        public boolean empty() { return count <= 0 || "minecraft:air".equals(itemId); }
        @NotNull TransferBridgeFactory.ItemStackView stackView() {
            return new TransferBridgeFactory.ItemStackView(itemId, count);
        }
    }

    public record MenuSnapshot(@NotNull List<MenuStack> slots, @NotNull MenuStack carried, int stateId) {
        public MenuSnapshot { slots = List.copyOf(slots); }
    }

    public record ActionContext(
        @NotNull Identifier menuIdentifier,
        int containerId,
        @NotNull MenuActionType action,
        int slot,
        int button,
        int expectedState,
        @NotNull MenuSnapshot before,
        @NotNull RuntimeTraceId traceId,
        @NotNull GeyserSession geyserSession
    ) {}

    public record MenuTransaction(
        int source,
        int destination,
        @NotNull MenuActionType action,
        int expectedState,
        @NotNull MenuSnapshot expectedSnapshot,
        @NotNull MenuSnapshot committedSnapshot,
        @NotNull String proposedMutation,
        @NotNull TransactionResult result,
        @NotNull StateChangeSet changes,
        @NotNull RuntimeTraceId traceId
    ) {}

    public enum TransactionResult {
        COMMITTED,
        NO_CHANGE,
        REJECTED,
        RESYNCHRONIZED
    }

    public record Routing(boolean bedrock, boolean accepted, @Nullable ActionContext context, @Nullable String reason) {
        static Routing passthrough() { return new Routing(false, true, null, null); }
        static Routing accepted(ActionContext context) { return new Routing(true, true, context, null); }
        static Routing rejected(String reason) { return new Routing(true, false, null, reason); }
    }

    private record MenuSession(
        @NotNull Identifier menuIdentifier,
        int containerId,
        @NotNull WeakReference<AbstractContainerMenu> menu,
        @NotNull MenuActionPlan actionPlan
    ) {}
}
