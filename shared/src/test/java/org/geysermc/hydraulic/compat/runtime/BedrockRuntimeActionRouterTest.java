package org.geysermc.hydraulic.compat.runtime;

import net.minecraft.resources.Identifier;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BedrockRuntimeActionRouterTest {
    private static final Identifier MACHINE = Identifier.fromNamespaceAndPath("hydraulic", "runtime_machine");
    private static final String LEVEL = "minecraft:overworld";

    @Test
    void routesBlockUseActionIntoRuntimeTargetDiscovery() {
        RuntimeTraceId traceId = new RuntimeTraceId("bedrock-use-block");
        RuntimeTargetDiscovery discovery = discovery(new RuntimeTargetDiscovery.Target(MACHINE, new Object(), null, null), true);
        InventoryTransactionPacket packet = blockUsePacket();

        BedrockRuntimeActionRouter.RuntimeActionResult result = BedrockRuntimeActionRouter.route(packet, discovery, LEVEL, traceId);

        assertEquals(BedrockRuntimeActionRouter.Status.TARGET_RESOLVED, result.status());
        assertEquals(traceId, result.traceId());
        assertEquals(MACHINE, result.blockIdentifier());
        assertEquals("minecraft:overworld:4,70,9", result.position().asKey());
        assertNull(result.reason());
    }

    @Test
    void reportsMissingCapabilityForDiscoveredTarget() {
        RuntimeTraceId traceId = new RuntimeTraceId("bedrock-use-no-capability");
        RuntimeTargetDiscovery discovery = discovery(new RuntimeTargetDiscovery.Target(MACHINE, new Object(), null, null), false);

        BedrockRuntimeActionRouter.RuntimeActionResult result = BedrockRuntimeActionRouter.route(blockUsePacket(), discovery, LEVEL, traceId);

        assertEquals(BedrockRuntimeActionRouter.Status.CAPABILITY_UNAVAILABLE, result.status());
        assertEquals(traceId, result.traceId());
        assertEquals(MACHINE, result.blockIdentifier());
    }

    @Test
    void reportsMissingTargetForUnknownPosition() {
        RuntimeTraceId traceId = new RuntimeTraceId("bedrock-use-missing-target");
        RuntimeTargetDiscovery discovery = discovery(null, false);

        BedrockRuntimeActionRouter.RuntimeActionResult result = BedrockRuntimeActionRouter.route(blockUsePacket(), discovery, LEVEL, traceId);

        assertEquals(BedrockRuntimeActionRouter.Status.TARGET_UNAVAILABLE, result.status());
        assertEquals(traceId, result.traceId());
    }

    @Test
    void ignoresNonBlockUseInventoryTransactions() {
        RuntimeTraceId traceId = new RuntimeTraceId("bedrock-normal-inventory");
        InventoryTransactionPacket packet = new InventoryTransactionPacket();
        packet.setTransactionType(InventoryTransactionType.NORMAL);

        BedrockRuntimeActionRouter.RuntimeActionResult result = BedrockRuntimeActionRouter.route(packet, discovery(null, false), LEVEL, traceId);

        assertEquals(BedrockRuntimeActionRouter.Status.IGNORED, result.status());
        assertEquals(traceId, result.traceId());
    }

    @Test
    void reportsMissingBlockPosition() {
        RuntimeTraceId traceId = new RuntimeTraceId("bedrock-use-no-position");
        InventoryTransactionPacket packet = new InventoryTransactionPacket();
        packet.setTransactionType(InventoryTransactionType.ITEM_USE);
        packet.setActionType(0);

        BedrockRuntimeActionRouter.RuntimeActionResult result = BedrockRuntimeActionRouter.route(packet, discovery(null, false), LEVEL, traceId);

        assertEquals(BedrockRuntimeActionRouter.Status.TARGET_UNAVAILABLE, result.status());
        assertEquals(traceId, result.traceId());
    }

    @Test
    void executesCompiledHeldItemInsertionAndConsumesExactCount() {
        RuntimeTraceId traceId = new RuntimeTraceId("bedrock-owned-insert");
        RecordingAutomationAccess automation = new RecordingAutomationAccess(true);
        RuntimeTargetDiscovery discovery = discovery(new RuntimeTargetDiscovery.Target(MACHINE, new Object(), null, null), automation);
        MutableHeldItem held = new MutableHeldItem(new TransferBridgeFactory.ItemStackView("minecraft:cobblestone", 4));
        BedrockRuntimeActionRouter.RuntimeActionResult routed = BedrockRuntimeActionRouter.route(blockUsePacket(), discovery, LEVEL, traceId);

        BedrockRuntimeActionRouter.RuntimeActionResult result = BedrockRuntimeActionRouter.executeItemAction(
            routed,
            discovery,
            new BlockUseActionPlan(BlockUseActionPlan.Action.INSERT_HELD_ITEM, 2, 1, "up", null),
            held,
            null
        );

        assertEquals(BedrockRuntimeActionRouter.Status.MUTATED, result.status());
        assertEquals(3, held.item.count());
        assertEquals(1, automation.lastRequest.item().count());
        assertEquals(2, automation.lastRequest.slot());
        assertEquals("up", automation.lastRequest.side());
    }

    @Test
    void rejectsMutationWithoutConsumingHeldItem() {
        RuntimeTraceId traceId = new RuntimeTraceId("bedrock-rejected-insert");
        RecordingAutomationAccess automation = new RecordingAutomationAccess(false);
        RuntimeTargetDiscovery discovery = discovery(new RuntimeTargetDiscovery.Target(MACHINE, new Object(), null, null), automation);
        MutableHeldItem held = new MutableHeldItem(new TransferBridgeFactory.ItemStackView("minecraft:cobblestone", 1));
        BedrockRuntimeActionRouter.RuntimeActionResult routed = BedrockRuntimeActionRouter.route(blockUsePacket(), discovery, LEVEL, traceId);

        BedrockRuntimeActionRouter.RuntimeActionResult result = BedrockRuntimeActionRouter.executeItemAction(
            routed,
            discovery,
            new BlockUseActionPlan(BlockUseActionPlan.Action.INSERT_HELD_ITEM, 0, 1, null, null),
            held,
            null
        );

        assertEquals(BedrockRuntimeActionRouter.Status.MUTATION_REJECTED, result.status());
        assertEquals(1, held.item.count());
    }

    @Test
    void rejectsInsufficientHeldItemBeforeTargetMutation() {
        RuntimeTraceId traceId = new RuntimeTraceId("bedrock-insufficient-held-item");
        RecordingAutomationAccess automation = new RecordingAutomationAccess(true);
        RuntimeTargetDiscovery discovery = discovery(new RuntimeTargetDiscovery.Target(MACHINE, new Object(), null, null), automation);
        MutableHeldItem held = new MutableHeldItem(new TransferBridgeFactory.ItemStackView("minecraft:cobblestone", 1));
        BedrockRuntimeActionRouter.RuntimeActionResult routed = BedrockRuntimeActionRouter.route(blockUsePacket(), discovery, LEVEL, traceId);

        BedrockRuntimeActionRouter.RuntimeActionResult result = BedrockRuntimeActionRouter.executeItemAction(
            routed,
            discovery,
            new BlockUseActionPlan(BlockUseActionPlan.Action.INSERT_HELD_ITEM, 0, 2, null, null),
            held,
            null
        );

        assertEquals(BedrockRuntimeActionRouter.Status.MUTATION_REJECTED, result.status());
        assertNull(automation.lastRequest);
        assertEquals(1, held.item.count());
    }

    @Test
    void sneakingExtractsFromCompiledSlotAndDeliversItemToPlayer() {
        RuntimeTraceId traceId = new RuntimeTraceId("bedrock-sneak-extract");
        RecordingAutomationAccess automation = new RecordingAutomationAccess(true);
        RuntimeTargetDiscovery discovery = discovery(new RuntimeTargetDiscovery.Target(MACHINE, new Object(), null, null), automation);
        SneakingHeldItem held = new SneakingHeldItem(true, null);
        BedrockRuntimeActionRouter.RuntimeActionResult routed = BedrockRuntimeActionRouter.route(blockUsePacket(), discovery, LEVEL, traceId);

        BedrockRuntimeActionRouter.RuntimeActionResult result = BedrockRuntimeActionRouter.executeItemAction(
            routed,
            discovery,
            new BlockUseActionPlan(
                BlockUseActionPlan.Action.INSERT_HELD_ITEM, 0, 1, null,
                new BlockUseActionPlan.ExtractAction(1, "minecraft:stone", 2, "down")
            ),
            held,
            null
        );

        assertEquals(BedrockRuntimeActionRouter.Status.MUTATED, result.status());
        assertEquals(TransferDirection.EXTRACT, automation.lastRequest.direction());
        assertEquals(1, automation.lastRequest.slot());
        assertEquals("minecraft:stone", automation.lastRequest.item().itemId());
        assertEquals(2, automation.lastRequest.item().count());
        assertEquals("down", automation.lastRequest.side());
        assertEquals("minecraft:stone", held.given.itemId());
        assertEquals(2, held.given.count());
    }

    @Test
    void sneakingExtractionRejectionNeverDeliversAnItem() {
        RuntimeTraceId traceId = new RuntimeTraceId("bedrock-sneak-extract-rejected");
        RecordingAutomationAccess automation = new RecordingAutomationAccess(false);
        RuntimeTargetDiscovery discovery = discovery(new RuntimeTargetDiscovery.Target(MACHINE, new Object(), null, null), automation);
        SneakingHeldItem held = new SneakingHeldItem(true, null);
        BedrockRuntimeActionRouter.RuntimeActionResult routed = BedrockRuntimeActionRouter.route(blockUsePacket(), discovery, LEVEL, traceId);

        BedrockRuntimeActionRouter.RuntimeActionResult result = BedrockRuntimeActionRouter.executeItemAction(
            routed,
            discovery,
            new BlockUseActionPlan(
                BlockUseActionPlan.Action.INSERT_HELD_ITEM, 0, 1, null,
                new BlockUseActionPlan.ExtractAction(1, "minecraft:stone", 1, null)
            ),
            held,
            null
        );

        assertEquals(BedrockRuntimeActionRouter.Status.MUTATION_REJECTED, result.status());
        assertNull(held.given);
    }

    @Test
    void rejectsExtractionBeforeMutationWhenPlayerCannotReceiveItem() {
        RuntimeTraceId traceId = new RuntimeTraceId("bedrock-sneak-extract-no-recipient");
        RecordingAutomationAccess automation = new RecordingAutomationAccess(true);
        RuntimeTargetDiscovery discovery = discovery(new RuntimeTargetDiscovery.Target(MACHINE, new Object(), null, null), automation);
        SneakingHeldItem held = new SneakingHeldItem(true, null, false);
        BedrockRuntimeActionRouter.RuntimeActionResult routed = BedrockRuntimeActionRouter.route(blockUsePacket(), discovery, LEVEL, traceId);

        BedrockRuntimeActionRouter.RuntimeActionResult result = BedrockRuntimeActionRouter.executeItemAction(
            routed,
            discovery,
            new BlockUseActionPlan(
                BlockUseActionPlan.Action.INSERT_HELD_ITEM, 0, 1, null,
                new BlockUseActionPlan.ExtractAction(1, "minecraft:stone", 1, null)
            ),
            held,
            null
        );

        assertEquals(BedrockRuntimeActionRouter.Status.MUTATION_REJECTED, result.status());
        assertNull(automation.lastRequest);
        assertNull(held.given);
    }

    @Test
    void sneakingWithoutCompiledExtractActionFallsBackToInsertion() {
        RuntimeTraceId traceId = new RuntimeTraceId("bedrock-sneak-no-extract");
        RecordingAutomationAccess automation = new RecordingAutomationAccess(true);
        RuntimeTargetDiscovery discovery = discovery(new RuntimeTargetDiscovery.Target(MACHINE, new Object(), null, null), automation);
        SneakingHeldItem held = new SneakingHeldItem(true, new TransferBridgeFactory.ItemStackView("minecraft:cobblestone", 4));
        BedrockRuntimeActionRouter.RuntimeActionResult routed = BedrockRuntimeActionRouter.route(blockUsePacket(), discovery, LEVEL, traceId);

        BedrockRuntimeActionRouter.RuntimeActionResult result = BedrockRuntimeActionRouter.executeItemAction(
            routed,
            discovery,
            new BlockUseActionPlan(BlockUseActionPlan.Action.INSERT_HELD_ITEM, 0, 1, null, null),
            held,
            null
        );

        assertEquals(BedrockRuntimeActionRouter.Status.MUTATED, result.status());
        assertEquals(TransferDirection.INSERT, automation.lastRequest.direction());
    }

    @Test
    void executesExactFluidDrainAndRecordsTankAndPropertyState() {
        RuntimeTraceId traceId = new RuntimeTraceId("bedrock-fluid-drain");
        MutableFluidTank tank = new MutableFluidTank("minecraft:water", 0, 1_000);
        RuntimeTargetDiscovery discovery = fluidDiscovery(tank);
        BedrockRuntimeActionRouter.RuntimeActionResult routed = new BedrockRuntimeActionRouter.RuntimeActionResult(traceId, BedrockRuntimeActionRouter.Status.TARGET_RESOLVED, new RuntimeTargetDiscovery.Position(LEVEL, 4, 70, 9), MACHINE, null);
        MutableFluidContainer held = new MutableFluidContainer("minecraft:water_bucket");
        DirtyStateTracker dirty = new DirtyStateTracker();

        BedrockRuntimeActionRouter.RuntimeActionResult result = BedrockRuntimeActionRouter.executeFluidAction(
            routed,
            discovery,
            new FluidBlockUseActionPlan(
                FluidBlockUseActionPlan.Action.DRAIN_HELD_CONTAINER,
                "minecraft:water_bucket", "minecraft:bucket", "minecraft:water", 0, 1_000, "up", 2
            ),
            held,
            dirty
        );

        assertEquals(BedrockRuntimeActionRouter.Status.MUTATED, result.status());
        assertEquals("minecraft:bucket", held.itemId);
        assertEquals(1_000, tank.amount);
        StateChangeSet changes = dirty.drain();
        assertEquals(2, changes.changes().size());
        assertEquals(traceId, changes.traceId());
        assertEquals("container.property.2", changes.changes().get(1).field());
        assertEquals(1_000, changes.changes().get(1).after());
    }

    @Test
    void rejectsPartialFluidDrainWithoutExchangingHeldItem() {
        MutableFluidTank tank = new MutableFluidTank("minecraft:water", 500, 1_000);
        RuntimeTargetDiscovery discovery = fluidDiscovery(tank);
        BedrockRuntimeActionRouter.RuntimeActionResult routed = new BedrockRuntimeActionRouter.RuntimeActionResult(new RuntimeTraceId("bedrock-fluid-partial"), BedrockRuntimeActionRouter.Status.TARGET_RESOLVED, new RuntimeTargetDiscovery.Position(LEVEL, 4, 70, 9), MACHINE, null);
        MutableFluidContainer held = new MutableFluidContainer("minecraft:water_bucket");

        BedrockRuntimeActionRouter.RuntimeActionResult result = BedrockRuntimeActionRouter.executeFluidAction(
            routed,
            discovery,
            new FluidBlockUseActionPlan(
                FluidBlockUseActionPlan.Action.DRAIN_HELD_CONTAINER,
                "minecraft:water_bucket", "minecraft:bucket", "minecraft:water", 0, 1_000, null, null
            ),
            held,
            null
        );

        assertEquals(BedrockRuntimeActionRouter.Status.MUTATION_REJECTED, result.status());
        assertEquals("minecraft:water_bucket", held.itemId);
        assertEquals(500, tank.amount);
    }

    @Test
    void restoresTankWhenHeldItemExchangeFailsAfterFluidCommit() {
        MutableFluidTank tank = new MutableFluidTank("minecraft:water", 0, 1_000);
        RuntimeTargetDiscovery discovery = fluidDiscovery(tank);
        BedrockRuntimeActionRouter.RuntimeActionResult routed = new BedrockRuntimeActionRouter.RuntimeActionResult(new RuntimeTraceId("bedrock-fluid-rollback"), BedrockRuntimeActionRouter.Status.TARGET_RESOLVED, new RuntimeTargetDiscovery.Position(LEVEL, 4, 70, 9), MACHINE, null);
        MutableFluidContainer held = new MutableFluidContainer("minecraft:water_bucket", false);

        BedrockRuntimeActionRouter.RuntimeActionResult result = BedrockRuntimeActionRouter.executeFluidAction(
            routed,
            discovery,
            new FluidBlockUseActionPlan(
                FluidBlockUseActionPlan.Action.DRAIN_HELD_CONTAINER,
                "minecraft:water_bucket", "minecraft:bucket", "minecraft:water", 0, 1_000, null, null
            ),
            held,
            null
        );

        assertEquals(BedrockRuntimeActionRouter.Status.MUTATION_REJECTED, result.status());
        assertEquals("minecraft:water_bucket", held.itemId);
        assertEquals(0, tank.amount);
    }

    @Test
    void executesExactEnergyActionAndRecordsPropertyState() {
        RuntimeTraceId traceId = new RuntimeTraceId("bedrock-energy-receive");
        EnergyRecordingAutomation automation = new EnergyRecordingAutomation();
        RuntimeTargetDiscovery discovery = discovery(
            new RuntimeTargetDiscovery.Target(MACHINE, null, null, new Object()), automation
        );
        BedrockRuntimeActionRouter.RuntimeActionResult routed = new BedrockRuntimeActionRouter.RuntimeActionResult(
            traceId, BedrockRuntimeActionRouter.Status.TARGET_RESOLVED,
            new RuntimeTargetDiscovery.Position(LEVEL, 4, 70, 9), MACHINE, null
        );
        DirtyStateTracker dirty = new DirtyStateTracker();

        BedrockRuntimeActionRouter.RuntimeActionResult result = BedrockRuntimeActionRouter.executeEnergyAction(
            routed,
            discovery,
            new EnergyBlockUseActionPlan(EnergyBlockUseActionPlan.Action.RECEIVE, 250, "up", 4),
            dirty
        );

        assertEquals(BedrockRuntimeActionRouter.Status.MUTATED, result.status());
        assertEquals(250, automation.lastRequest.amount());
        StateChangeSet changes = dirty.drain();
        assertEquals(traceId, changes.traceId());
        assertEquals("container.property.4", changes.changes().get(1).field());
        assertEquals(250, changes.changes().get(1).after());
    }

    private static InventoryTransactionPacket blockUsePacket() {
        InventoryTransactionPacket packet = new InventoryTransactionPacket();
        packet.setTransactionType(InventoryTransactionType.ITEM_USE);
        packet.setActionType(0);
        packet.setBlockPosition(Vector3i.from(4, 70, 9));
        return packet;
    }

    private static RuntimeTargetDiscovery discovery(RuntimeTargetDiscovery.Target target, boolean resolvable) {
        return new RuntimeTargetDiscovery(
            ignored -> resolvable ? new NoopAutomationAccess() : null,
            position -> target
        );
    }

    private static RuntimeTargetDiscovery discovery(RuntimeTargetDiscovery.Target target, MachineBridgeFactory.ResourceAutomationAccess automation) {
        return new RuntimeTargetDiscovery(ignored -> automation, position -> target);
    }

    private static RuntimeTargetDiscovery fluidDiscovery(MutableFluidTank tank) {
        RuntimeTargetDiscovery.Target target = new RuntimeTargetDiscovery.Target(MACHINE, null, tank, null);
        return new RuntimeTargetDiscovery(
            ignored -> new NoopAutomationAccess(),
            (ignored, tankIndex, capacity) -> new FluidContainerBridge(tank, tankIndex, capacity),
            position -> target
        );
    }

    private static final class MutableHeldItem implements BedrockRuntimeActionRouter.HeldItemAccess {
        private TransferBridgeFactory.ItemStackView item;

        private MutableHeldItem(TransferBridgeFactory.ItemStackView item) {
            this.item = item;
        }

        @Override
        public TransferBridgeFactory.ItemStackView heldItem() {
            return this.item;
        }

        @Override
        public void consume(int count) {
            this.item = new TransferBridgeFactory.ItemStackView(this.item.itemId(), this.item.count() - count);
        }
    }

    private static final class SneakingHeldItem implements BedrockRuntimeActionRouter.HeldItemAccess {
        private final boolean sneaking;
        private final boolean deliverable;
        private TransferBridgeFactory.ItemStackView item;
        private TransferBridgeFactory.ItemStackView given;

        private SneakingHeldItem(boolean sneaking, TransferBridgeFactory.ItemStackView item) {
            this(sneaking, item, true);
        }

        private SneakingHeldItem(boolean sneaking, TransferBridgeFactory.ItemStackView item, boolean deliverable) {
            this.sneaking = sneaking;
            this.item = item;
            this.deliverable = deliverable;
        }

        @Override
        public TransferBridgeFactory.ItemStackView heldItem() {
            return this.item;
        }

        @Override
        public void consume(int count) {
            this.item = new TransferBridgeFactory.ItemStackView(this.item.itemId(), this.item.count() - count);
        }

        @Override
        public boolean isSneaking() {
            return this.sneaking;
        }

        @Override
        public boolean canGive(TransferBridgeFactory.ItemStackView item) {
            return this.deliverable;
        }

        @Override
        public boolean give(TransferBridgeFactory.ItemStackView item) {
            if (!this.deliverable) {
                return false;
            }
            this.given = item;
            return true;
        }
    }

    private static final class RecordingAutomationAccess extends NoopAutomationAccess {
        private final boolean commit;
        private TransferRequest lastRequest;

        private RecordingAutomationAccess(boolean commit) {
            this.commit = commit;
        }

        @Override
        public TransferResult transferItem(TransferRequest request) {
            this.lastRequest = request;
            if (!this.commit) {
                return TransferResult.rejected("target rejected insertion");
            }
            StateChangeSet changes = new StateChangeSet(List.of(new StateChangeSet.FieldChange(
                request.blockIdentifier(),
                "inventory.slot." + request.slot(),
                new TransferBridgeFactory.ItemStackView("minecraft:air", 0),
                request.item()
            )));
            return new TransferResult(true, request.item().count(), TransferBridgeFactory.OperationStatus.COMPLETED, null, List.of(), changes);
        }
    }

    private static class NoopAutomationAccess implements MachineBridgeFactory.ResourceAutomationAccess {
        @Override
        public boolean supportsSidedItemInsertion(Identifier blockIdentifier) {
            return true;
        }

        @Override
        public boolean supportsSidedItemExtraction(Identifier blockIdentifier) {
            return true;
        }

        @Override
        public boolean supportsSidedFluidInsertion(Identifier blockIdentifier) {
            return false;
        }

        @Override
        public boolean supportsSidedFluidExtraction(Identifier blockIdentifier) {
            return false;
        }

        @Override
        public boolean supportsSidedEnergyReceive(Identifier blockIdentifier) {
            return false;
        }

        @Override
        public boolean supportsSidedEnergyExtraction(Identifier blockIdentifier) {
            return false;
        }

        @Override
        public String filterType(Identifier blockIdentifier) {
            return null;
        }

        @Override
        public TransferResult transferItem(TransferRequest request) {
            return TransferResult.rejected("not used by action routing test");
        }

        @Override
        public TransferResult transferFluid(FluidTransferRequest request) {
            return TransferResult.rejected("not used by action routing test");
        }

        @Override
        public TransferResult transferEnergy(EnergyTransferRequest request) {
            return TransferResult.rejected("not used by action routing test");
        }
    }

    private static final class EnergyRecordingAutomation extends NoopAutomationAccess {
        private EnergyTransferRequest lastRequest;

        @Override
        public TransferResult transferEnergy(EnergyTransferRequest request) {
            this.lastRequest = request;
            StateChangeSet changes = new StateChangeSet(List.of(new StateChangeSet.FieldChange(
                request.blockIdentifier(), "energy.amount", 0, request.amount()
            )));
            return new TransferResult(true, request.amount(), TransferBridgeFactory.OperationStatus.COMPLETED, null, List.of(), changes);
        }
    }

    private static final class MutableFluidContainer implements BedrockRuntimeActionRouter.FluidContainerAccess {
        private String itemId;
        private final boolean replaceable;

        private MutableFluidContainer(String itemId) {
            this(itemId, true);
        }

        private MutableFluidContainer(String itemId, boolean replaceable) {
            this.itemId = itemId;
            this.replaceable = replaceable;
        }

        @Override
        public String heldItemId() {
            return this.itemId;
        }

        @Override
        public boolean canReplace(String outputItemId) {
            return true;
        }

        @Override
        public boolean replaceHeldItem(String inputItemId, String outputItemId) {
            if (!this.replaceable || !this.itemId.equals(inputItemId)) {
                return false;
            }
            this.itemId = outputItemId;
            return true;
        }
    }

    private static final class MutableFluidTank implements TransferBridgeFactory.FluidTransferBridge {
        private final String fluidId;
        private int amount;
        private final int capacity;

        private MutableFluidTank(String fluidId, int amount, int capacity) {
            this.fluidId = fluidId;
            this.amount = amount;
            this.capacity = capacity;
        }

        @Override
        public boolean executable() {
            return true;
        }

        @Override
        public boolean canInsertFluid(Identifier blockIdentifier) {
            return true;
        }

        @Override
        public boolean canExtractFluid(Identifier blockIdentifier) {
            return true;
        }

        @Override
        public String tankType(Identifier blockIdentifier) {
            return "test";
        }

        @Override
        public TransferBridgeFactory.FluidStackView tankAt(Identifier blockIdentifier, int tank) {
            return new TransferBridgeFactory.FluidStackView(this.fluidId, this.amount);
        }

        @Override
        public int insertFluid(Identifier blockIdentifier, TransferBridgeFactory.FluidStackView fluid, int tank, String side, boolean simulate) {
            int moved = this.fluidId.equals(fluid.fluidId()) ? Math.min(fluid.amount(), this.capacity - this.amount) : 0;
            if (!simulate) {
                this.amount += moved;
            }
            return moved;
        }

        @Override
        public int extractFluid(Identifier blockIdentifier, TransferBridgeFactory.FluidStackView fluid, int tank, String side, boolean simulate) {
            int moved = this.fluidId.equals(fluid.fluidId()) ? Math.min(fluid.amount(), this.amount) : 0;
            if (!simulate) {
                this.amount -= moved;
            }
            return moved;
        }
    }
}
