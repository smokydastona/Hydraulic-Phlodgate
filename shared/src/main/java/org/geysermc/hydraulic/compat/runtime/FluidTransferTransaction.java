package org.geysermc.hydraulic.compat.runtime;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Atomic fluid transfer transaction over one executable runtime bridge. */
public final class FluidTransferTransaction {
    private final TransferBridgeFactory.FluidTransferBridge bridge;
    private final List<FluidTransferRequest> requests = new ArrayList<>();

    public FluidTransferTransaction(@NotNull TransferBridgeFactory.FluidTransferBridge bridge) {
        if (!bridge.executable()) {
            throw new IllegalArgumentException("Fluid transactions require an executable bridge");
        }
        this.bridge = bridge;
    }

    @NotNull
    public FluidTransferTransaction add(@NotNull FluidTransferRequest request) {
        this.requests.add(request);
        return this;
    }

    @NotNull
    public TransferResult execute() {
        if (this.requests.isEmpty()) {
            return TransferResult.rejected("transaction has no operations");
        }

        List<TransferBridgeFactory.OperationResult> simulations = new ArrayList<>(this.requests.size());
        for (FluidTransferRequest request : this.requests) {
            TransferBridgeFactory.OperationResult result = operate(request, true);
            simulations.add(result);
            if (!isComplete(result, request)) {
                return new TransferResult(false, 0, result.status(), result.failureReason(), simulations, StateChangeSet.empty());
            }
        }

        List<CommittedOperation> committed = new ArrayList<>(this.requests.size());
        List<TransferBridgeFactory.OperationResult> results = new ArrayList<>(this.requests.size());
        List<StateChangeSet.FieldChange> changes = new ArrayList<>(this.requests.size());
        int moved = 0;
        for (FluidTransferRequest request : this.requests) {
            TransferBridgeFactory.FluidStackView before = this.bridge.tankAt(request.blockIdentifier(), request.tank());
            TransferBridgeFactory.OperationResult result = operate(request, false);
            results.add(result);
            if (!isComplete(result, request)) {
                rollback(committed);
                return new TransferResult(false, 0, result.status(), result.failureReason(), results, StateChangeSet.empty());
            }
            committed.add(new CommittedOperation(request, result.moved()));
            TransferBridgeFactory.FluidStackView after = this.bridge.tankAt(request.blockIdentifier(), request.tank());
            if (!Objects.equals(before, after)) {
                changes.add(new StateChangeSet.FieldChange(
                    request.blockIdentifier(),
                    "fluid.tank." + request.tank(),
                    before,
                    after
                ));
            }
            moved += result.moved();
        }

        return new TransferResult(true, moved, TransferBridgeFactory.OperationStatus.COMPLETED, null, results, new StateChangeSet(changes));
    }

    @NotNull
    public TransferResult execute(@NotNull DirtyStateTracker dirtyStateTracker) {
        TransferResult result = execute();
        if (result.committed()) {
            dirtyStateTracker.record(result.stateChanges());
        }
        return result;
    }

    private TransferBridgeFactory.OperationResult operate(@NotNull FluidTransferRequest request, boolean simulate) {
        if (request.direction() == TransferDirection.INSERT) {
            return this.bridge.insertFluidResult(request.blockIdentifier(), request.fluid(), request.tank(), request.side(), simulate);
        }
        return this.bridge.extractFluidResult(request.blockIdentifier(), request.fluid(), request.tank(), request.side(), simulate);
    }

    private static boolean isComplete(
        @NotNull TransferBridgeFactory.OperationResult result,
        @NotNull FluidTransferRequest request
    ) {
        return result.successful() && result.moved() == request.fluid().amount();
    }

    private void rollback(@NotNull List<CommittedOperation> committed) {
        for (int index = committed.size() - 1; index >= 0; index--) {
            CommittedOperation operation = committed.get(index);
            FluidTransferRequest request = operation.request();
            FluidTransferRequest inverse = new FluidTransferRequest(
                request.blockIdentifier(),
                request.direction() == TransferDirection.INSERT ? TransferDirection.EXTRACT : TransferDirection.INSERT,
                new TransferBridgeFactory.FluidStackView(request.fluid().fluidId(), operation.moved()),
                request.tank(),
                request.side()
            );
            operate(inverse, false);
        }
    }

    private record CommittedOperation(@NotNull FluidTransferRequest request, int moved) {
    }
}
