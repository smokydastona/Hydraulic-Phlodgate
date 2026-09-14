package org.geysermc.hydraulic.compat.runtime;

import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.ContainerInput;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BedrockMenuActionRouterTest {
    @Test
    void mapsCoreJavaInputsToNormalizedMenuActions() {
        BedrockMenuActionRouter.MenuSnapshot empty = snapshot(List.of(stack("minecraft:air", 0)), stack("minecraft:air", 0));
        BedrockMenuActionRouter.MenuSnapshot carried = snapshot(
            List.of(stack("minecraft:air", 0)),
            stack("minecraft:iron_ingot", 1)
        );
        BedrockMenuActionRouter.MenuSnapshot occupied = snapshot(
            List.of(stack("minecraft:stone", 1)),
            stack("minecraft:iron_ingot", 1)
        );

        assertEquals(BedrockMenuActionRouter.MenuActionType.PICKUP,
            BedrockMenuActionRouter.action(ContainerInput.PICKUP, 0, empty, 0));
        assertEquals(BedrockMenuActionRouter.MenuActionType.PLACE,
            BedrockMenuActionRouter.action(ContainerInput.PICKUP, 0, carried, 0));
        assertEquals(BedrockMenuActionRouter.MenuActionType.SWAP,
            BedrockMenuActionRouter.action(ContainerInput.PICKUP, 0, occupied, 0));
        assertEquals(BedrockMenuActionRouter.MenuActionType.SPLIT,
            BedrockMenuActionRouter.action(ContainerInput.PICKUP, 1, empty, 0));
        assertEquals(BedrockMenuActionRouter.MenuActionType.QUICK_MOVE,
            BedrockMenuActionRouter.action(ContainerInput.QUICK_MOVE, 0, empty, 0));
        assertEquals(BedrockMenuActionRouter.MenuActionType.HOTBAR_SWAP,
            BedrockMenuActionRouter.action(ContainerInput.SWAP, 2, empty, 0));
        assertEquals(BedrockMenuActionRouter.MenuActionType.DROP,
            BedrockMenuActionRouter.action(ContainerInput.THROW, 0, empty, 0));
        assertEquals(BedrockMenuActionRouter.MenuActionType.DRAG,
            BedrockMenuActionRouter.action(ContainerInput.QUICK_CRAFT, 0, empty, 0));
        assertNull(BedrockMenuActionRouter.action(ContainerInput.CLONE, 0, empty, 0));
    }

    @Test
    void rejectsInvalidButtonsAndClientOnlyClone() {
        assertNull(BedrockMenuActionRouter.validateButton(ContainerInput.PICKUP, 0, null));
        assertNotNull(BedrockMenuActionRouter.validateButton(ContainerInput.PICKUP, 2, null));
        assertNull(BedrockMenuActionRouter.validateButton(ContainerInput.SWAP, 8, null));
        assertNull(BedrockMenuActionRouter.validateButton(ContainerInput.SWAP, 40, null));
        assertNotNull(BedrockMenuActionRouter.validateButton(ContainerInput.SWAP, 41, null));
        assertNotNull(BedrockMenuActionRouter.validateButton(ContainerInput.CLONE, 0, null));
        assertNotNull(BedrockMenuActionRouter.validateButton(ContainerInput.QUICK_CRAFT, 0, null));
    }

    @Test
    void emitsOnlyAuthoritativeJavaSlotDeltas() {
        Identifier menu = Identifier.parse("test:menu");
        RuntimeTraceId trace = RuntimeTraceId.create();
        BedrockMenuActionRouter.MenuSnapshot before = snapshot(
            List.of(stack("minecraft:iron_ingot", 2), stack("minecraft:air", 0)), stack("minecraft:air", 0));
        BedrockMenuActionRouter.MenuSnapshot after = snapshot(
            List.of(stack("minecraft:iron_ingot", 1), stack("minecraft:stone", 1)), stack("minecraft:air", 0));

        StateChangeSet changes = BedrockMenuActionRouter.diff(menu, before, after, trace);

        assertEquals(2, changes.changes().size());
        assertEquals("inventory.slot.0", changes.changes().get(0).field());
        assertEquals("inventory.slot.1", changes.changes().get(1).field());
        assertTrue(changes.changes().stream().allMatch(change -> trace.equals(change.traceId())));
        assertEquals(2, ((TransferBridgeFactory.ItemStackView) changes.changes().get(0).before()).count());
        assertEquals(1, ((TransferBridgeFactory.ItemStackView) changes.changes().get(0).after()).count());
    }

    @Test
    void identicalAuthoritativeStateProducesNoDelta() {
        BedrockMenuActionRouter.MenuSnapshot before = snapshot(List.of(stack("minecraft:stone", 3)), stack("minecraft:air", 0));
        BedrockMenuActionRouter.MenuSnapshot after = snapshot(List.of(stack("minecraft:stone", 3)), stack("minecraft:air", 0));

        assertTrue(BedrockMenuActionRouter.diff(
            Identifier.parse("test:menu"), before, after, RuntimeTraceId.create()).changes().isEmpty());
    }

    @Test
    void componentOnlyChangesProduceAuthoritativeDelta() {
        BedrockMenuActionRouter.MenuSnapshot before = snapshot(
            List.of(new BedrockMenuActionRouter.MenuStack("minecraft:potion", 1, 10)), stack("minecraft:air", 0));
        BedrockMenuActionRouter.MenuSnapshot after = snapshot(
            List.of(new BedrockMenuActionRouter.MenuStack("minecraft:potion", 1, 11)), stack("minecraft:air", 0));

        assertEquals(1, BedrockMenuActionRouter.diff(
            Identifier.parse("test:menu"), before, after, RuntimeTraceId.create()).changes().size());
    }

    @Test
    void transactionCapturesExpectedAndCommittedState() {
        RuntimeTraceId trace = RuntimeTraceId.create();
        BedrockMenuActionRouter.MenuSnapshot before = snapshot(
            List.of(stack("minecraft:iron_ingot", 1)), stack("minecraft:air", 0));
        BedrockMenuActionRouter.MenuSnapshot after = snapshot(
            List.of(stack("minecraft:air", 0)), stack("minecraft:iron_ingot", 1));
        StateChangeSet changes = BedrockMenuActionRouter.diff(Identifier.parse("test:menu"), before, after, trace);

        BedrockMenuActionRouter.MenuTransaction transaction = BedrockMenuActionRouter.transaction(
            BedrockMenuActionRouter.MenuActionType.PICKUP, 0, 0, 12, before, after, changes, trace);

        assertEquals(0, transaction.source());
        assertEquals(-1, transaction.destination());
        assertEquals(12, transaction.expectedState());
        assertEquals(BedrockMenuActionRouter.TransactionResult.COMMITTED, transaction.result());
        assertSame(before, transaction.expectedSnapshot());
        assertSame(after, transaction.committedSnapshot());
        assertEquals(trace, transaction.traceId());
    }

    @Test
    void toggleRequiresExplicitCompiledButtonContract() {
        MenuActionPlan plan = new MenuActionPlan(Map.of(3, BedrockMenuActionRouter.MenuActionType.TOGGLE));

        assertEquals(BedrockMenuActionRouter.MenuActionType.TOGGLE, plan.action(3));
        assertEquals(BedrockMenuActionRouter.MenuActionType.BUTTON, plan.action(4));
    }

    private static BedrockMenuActionRouter.MenuSnapshot snapshot(
        List<BedrockMenuActionRouter.MenuStack> slots,
        BedrockMenuActionRouter.MenuStack carried
    ) {
        return new BedrockMenuActionRouter.MenuSnapshot(slots, carried, 0);
    }

    private static BedrockMenuActionRouter.MenuStack stack(String itemId, int count) {
        return new BedrockMenuActionRouter.MenuStack(itemId, count, itemId.hashCode());
    }
}
