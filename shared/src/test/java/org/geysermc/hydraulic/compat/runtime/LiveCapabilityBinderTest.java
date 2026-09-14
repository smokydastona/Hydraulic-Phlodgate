package org.geysermc.hydraulic.compat.runtime;

import net.minecraft.resources.Identifier;
import org.geysermc.hydraulic.compat.CompatibilityStatus;
import org.geysermc.hydraulic.compat.discovery.DynamicMachineLifecycleManager;
import org.geysermc.hydraulic.compat.ir.CompiledCompatibilityPlan;
import org.geysermc.hydraulic.compat.model.Confidence;
import org.geysermc.hydraulic.compat.model.SupportLevel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LiveCapabilityBinderTest {
    @Test
    void bindsExecutesUnbindsAndRebindsUnknownRuntimeInventory() {
        RuntimeDispatchTable dispatchTable = RuntimeDispatchTable.empty();
        LiveCapabilityBinder binder = new LiveCapabilityBinder(new DynamicMachineLifecycleManager(dispatchTable));
        Identifier identifier = Identifier.parse("third_party:unknown_storage");
        UnknownInventory inventory = new UnknownInventory();

        LiveCapabilityBinder.LiveBinding binding = binder.bind(identifier, inventory, Map.of("category", "block_entity"));

        assertSame(binding, binder.resolve(inventory));
        assertEquals(1, binder.bindingCount());
        assertEquals(UnknownInventory.class.getName(), binding.runtimeType());
        assertTrue(binding.objectIdentity().startsWith(UnknownInventory.class.getName() + "#"));
        assertTrue(binding.capabilities().contains("has_inventory"));
        assertTrue(binding.adapters().contains("dynamic.semantic.adapter"));
        assertTrue(binding.bridgeKinds().contains(RuntimeBridgeKind.ITEM_TRANSFER));
        assertEquals("dynamic-semantic-v1", binding.contractVersion());
        assertTrue(binding.confidence().score() > 0.0D);

        TransferBridgeFactory.ItemTransferBridge bridge = dispatchTable.itemTransfer(identifier, inventory);
        assertNotNull(bridge);
        TransferBridgeFactory.ItemStackView stack = new TransferBridgeFactory.ItemStackView("minecraft:iron_ingot", 4);
        assertEquals(4, bridge.insert(identifier, stack, 0, "up", true));
        assertEquals(0, inventory.item.count());
        assertEquals(4, bridge.insert(identifier, stack, 0, "up", false));
        assertEquals(stack, inventory.item);

        binder.unbind(inventory);
        assertNull(binder.resolve(inventory));
        assertEquals(0, binder.bindingCount());

        LiveCapabilityBinder.LiveBinding rebound = binder.bind(identifier, inventory, Map.of("category", "block_entity"));
        assertNotSame(binding, rebound);
        assertEquals(1, binder.bindingCount());
        assertNotNull(dispatchTable.itemTransfer(identifier, inventory));
    }

    @Test
    void verifiedRuntimeBindingAugmentsExistingPresentationPlan() {
        RuntimeDispatchTable dispatchTable = RuntimeDispatchTable.empty();
        Identifier identifier = Identifier.parse("third_party:indexed_storage");
        CompiledCompatibilityPlan presentationPlan = presentationPlan(identifier);
        dispatchTable.registerDynamicPlan(presentationPlan);
        LiveCapabilityBinder binder = new LiveCapabilityBinder(new DynamicMachineLifecycleManager(dispatchTable));

        LiveCapabilityBinder.LiveBinding binding = binder.bind(
            identifier,
            new UnknownInventory(),
            Map.of("category", "block_entity")
        );

        assertEquals(SupportLevel.VISUAL_ONLY, binding.plan().overallLevel());
        assertEquals("preserved", binding.plan().inventoryFacts().get("presentation_marker"));
        assertTrue(binding.bridgeKinds().contains(RuntimeBridgeKind.ITEM_TRANSFER));
        assertTrue(dispatchTable.runtimeBridgePlans(RuntimeBridgeKind.ITEM_TRANSFER).contains(binding.plan()));
        assertFalse(dispatchTable.runtimeBridgePlans(RuntimeBridgeKind.ITEM_TRANSFER).contains(presentationPlan));
    }

    @Test
    void refreshReverifiesAndRemovesStaleRuntimeBridgeEvidence() {
        RuntimeDispatchTable dispatchTable = RuntimeDispatchTable.empty();
        Identifier identifier = Identifier.parse("third_party:changing_storage");
        LiveCapabilityBinder binder = new LiveCapabilityBinder(new DynamicMachineLifecycleManager(dispatchTable));
        UnknownInventory executable = new UnknownInventory();

        assertTrue(binder.bind(identifier, executable, Map.of()).bridgeKinds().contains(RuntimeBridgeKind.ITEM_TRANSFER));
        LiveCapabilityBinder.LiveBinding refreshed = binder.refresh(identifier, executable, Map.of("contract_disabled", "true"));

        assertTrue(refreshed.bridgeKinds().contains(RuntimeBridgeKind.ITEM_TRANSFER));
        assertEquals(1, dispatchTable.runtimeBridgePlans(RuntimeBridgeKind.ITEM_TRANSFER).size());

        binder.bind(identifier, new Object(), Map.of());
        assertTrue(dispatchTable.runtimeBridgePlans(RuntimeBridgeKind.ITEM_TRANSFER).isEmpty());
    }

    @Test
    void distinctRuntimeObjectsNeverCollapseThroughModDefinedEquality() {
        RuntimeDispatchTable dispatchTable = RuntimeDispatchTable.empty();
        LiveCapabilityBinder binder = new LiveCapabilityBinder(new DynamicMachineLifecycleManager(dispatchTable));
        Identifier identifier = Identifier.parse("third_party:equal_storage");
        EqualInventory first = new EqualInventory();
        EqualInventory second = new EqualInventory();

        LiveCapabilityBinder.LiveBinding firstBinding = binder.bind(identifier, first, Map.of());
        LiveCapabilityBinder.LiveBinding secondBinding = binder.bind(identifier, second, Map.of());

        assertNotEquals(firstBinding.objectIdentity(), secondBinding.objectIdentity());
        assertSame(firstBinding, binder.resolve(first));
        assertSame(secondBinding, binder.resolve(second));
        assertEquals(2, binder.bindingCount());
    }

    private static CompiledCompatibilityPlan presentationPlan(Identifier identifier) {
        return new CompiledCompatibilityPlan(
            identifier.getNamespace(), "block", identifier.toString(), identifier.toString(),
            SupportLevel.VISUAL_ONLY, CompatibilityStatus.PARTIAL, 0,
            new Confidence(0.7D, "indexed presentation"), List.of(), List.of(), List.of(),
            Map.of("presentation_marker", "preserved"), true, null, true, null,
            true, true, false, false, false, null, null,
            List.of(), List.of(), List.of(), null, false, false,
            SupportLevel.VISUAL_ONLY, "presentation", List.of()
        );
    }

    public static class UnknownInventory {
        private TransferBridgeFactory.ItemStackView item = new TransferBridgeFactory.ItemStackView("minecraft:air", 0);

        public int getContainerSize() {
            return 1;
        }

        public TransferBridgeFactory.ItemStackView getItem(int slot) {
            return item;
        }

        public int insertItem(TransferBridgeFactory.ItemStackView stack, int slot, String side, boolean simulate) {
            if (!simulate) {
                item = stack;
            }
            return stack.count();
        }

        public int extractItem(TransferBridgeFactory.ItemStackView stack, int slot, String side, boolean simulate) {
            int extracted = Math.min(stack.count(), item.count());
            if (!simulate && extracted > 0) {
                item = new TransferBridgeFactory.ItemStackView(item.itemId(), item.count() - extracted);
            }
            return extracted;
        }
    }

    public static final class EqualInventory extends UnknownInventory {
        @Override
        public boolean equals(Object other) {
            return other instanceof EqualInventory;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }
}