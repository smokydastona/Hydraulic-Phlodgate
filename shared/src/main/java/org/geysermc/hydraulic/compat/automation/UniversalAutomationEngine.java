package org.geysermc.hydraulic.compat.automation;

import net.minecraft.resources.Identifier;
import org.geysermc.hydraulic.compat.runtime.TransferBridgeFactory;
import org.geysermc.hydraulic.compat.runtime.TransferResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Universal Automation, Routing, and Transfer Network Engine (Phase 2).
 * Provides multi-slot transactional safety, sided filters, priority routing,
 * and conveyor/pipe network logistics across arbitrary item containers.
 */
public final class UniversalAutomationEngine {

    public record SidedFilter(
        @Nullable String side,
        @NotNull List<String> allowedItemIds,
        @NotNull List<String> deniedItemIds,
        int maxStackLimit,
        @NotNull Predicate<TransferBridgeFactory.ItemStackView> customPredicate
    ) {
        public SidedFilter {
            allowedItemIds = List.copyOf(allowedItemIds);
            deniedItemIds = List.copyOf(deniedItemIds);
        }

        public boolean allows(@NotNull TransferBridgeFactory.ItemStackView item, @Nullable String targetSide) {
            if (side != null && targetSide != null && !side.equalsIgnoreCase(targetSide)) {
                return false;
            }
            if (item.isEmpty()) {
                return false;
            }
            if (!deniedItemIds.isEmpty() && deniedItemIds.contains(item.itemId())) {
                return false;
            }
            if (!allowedItemIds.isEmpty() && !allowedItemIds.contains(item.itemId())) {
                return false;
            }
            if (maxStackLimit > 0 && item.count() > maxStackLimit) {
                return false;
            }
            return customPredicate.test(item);
        }

        public static SidedFilter allowAll() {
            return new SidedFilter(null, List.of(), List.of(), 0, item -> true);
        }

        public static SidedFilter allowOnly(@NotNull String side, @NotNull List<String> allowedItemIds) {
            return new SidedFilter(side, allowedItemIds, List.of(), 0, item -> true);
        }
    }

    public record RouteNode(
        @NotNull Identifier nodeIdentifier,
        int priority,
        @NotNull SidedFilter filter,
        @NotNull TransferBridgeFactory.ItemTransferBridge bridge,
        @Nullable String targetSide
    ) {
        public RouteNode {
            Objects.requireNonNull(nodeIdentifier, "nodeIdentifier");
            Objects.requireNonNull(filter, "filter");
            Objects.requireNonNull(bridge, "bridge");
        }
    }

    public record TransferRoute(
        @NotNull String routeId,
        @NotNull RouteNode source,
        @NotNull List<RouteNode> destinations,
        int transferRateLimit
    ) {
        public TransferRoute {
            destinations = destinations.stream()
                .sorted(Comparator.comparingInt(RouteNode::priority).reversed())
                .toList();
            transferRateLimit = Math.max(1, transferRateLimit);
        }
    }

    public record AutomationCycleReport(
        @NotNull String routeId,
        int requestedTransfer,
        int movedTransfer,
        @NotNull List<TransferResult> transferResults,
        boolean success
    ) {
        public AutomationCycleReport {
            transferResults = List.copyOf(transferResults);
        }
    }

    public static final class NetworkRouter {
        private final Map<String, TransferRoute> routes = new LinkedHashMap<>();

        public void registerRoute(@NotNull TransferRoute route) {
            routes.put(route.routeId(), route);
        }

        @Nullable
        public TransferRoute getRoute(@NotNull String routeId) {
            return routes.get(routeId);
        }

        @NotNull
        public AutomationCycleReport executeCycle(@NotNull String routeId, boolean simulate) {
            TransferRoute route = routes.get(routeId);
            if (route == null) {
                return new AutomationCycleReport(routeId, 0, 0, List.of(), false);
            }

            RouteNode source = route.source();
            int totalMoved = 0;
            List<TransferResult> results = new ArrayList<>();
            int slotCount = source.bridge().slotCount(source.nodeIdentifier());

            for (int slot = 0; slot < slotCount && totalMoved < route.transferRateLimit(); slot++) {
                TransferBridgeFactory.ItemStackView available = source.bridge().itemAt(source.nodeIdentifier(), slot);
                if (available == null || available.isEmpty()) {
                    continue;
                }

                if (!source.filter().allows(available, source.targetSide())) {
                    continue;
                }

                int remainingBudget = route.transferRateLimit() - totalMoved;
                int candidateCount = Math.min(available.count(), remainingBudget);
                TransferBridgeFactory.ItemStackView candidateStack = new TransferBridgeFactory.ItemStackView(available.itemId(), candidateCount);

                for (RouteNode dest : route.destinations()) {
                    if (!dest.filter().allows(candidateStack, dest.targetSide())) {
                        continue;
                    }

                    int destSlots = dest.bridge().slotCount(dest.nodeIdentifier());
                    for (int destSlot = 0; destSlot < destSlots && candidateStack.count() > 0; destSlot++) {
                        int inserted = dest.bridge().insert(
                            dest.nodeIdentifier(),
                            candidateStack,
                            destSlot,
                            dest.targetSide(),
                            true
                        );

                        if (inserted > 0) {
                            if (!simulate) {
                                int extracted = source.bridge().extract(
                                    source.nodeIdentifier(),
                                    new TransferBridgeFactory.ItemStackView(candidateStack.itemId(), inserted),
                                    slot,
                                    source.targetSide(),
                                    false
                                );
                                if (extracted > 0) {
                                    dest.bridge().insert(
                                        dest.nodeIdentifier(),
                                        new TransferBridgeFactory.ItemStackView(candidateStack.itemId(), extracted),
                                        destSlot,
                                        dest.targetSide(),
                                        false
                                    );
                                    totalMoved += extracted;
                                    results.add(new TransferResult(
                                        true,
                                        extracted,
                                        TransferBridgeFactory.OperationStatus.COMPLETED,
                                        null,
                                        List.of(new TransferBridgeFactory.OperationResult(candidateStack.count(), extracted, false, TransferBridgeFactory.OperationStatus.COMPLETED, null)),
                                        org.geysermc.hydraulic.compat.runtime.StateChangeSet.empty()
                                    ));
                                    candidateStack = new TransferBridgeFactory.ItemStackView(candidateStack.itemId(), candidateStack.count() - extracted);
                                }
                            } else {
                                totalMoved += inserted;
                                results.add(new TransferResult(
                                    false,
                                    inserted,
                                    TransferBridgeFactory.OperationStatus.COMPLETED,
                                    null,
                                    List.of(new TransferBridgeFactory.OperationResult(candidateStack.count(), inserted, true, TransferBridgeFactory.OperationStatus.COMPLETED, null)),
                                    org.geysermc.hydraulic.compat.runtime.StateChangeSet.empty()
                                ));
                                candidateStack = new TransferBridgeFactory.ItemStackView(candidateStack.itemId(), candidateStack.count() - inserted);
                            }
                        }
                    }
                }
            }

            return new AutomationCycleReport(routeId, route.transferRateLimit(), totalMoved, results, totalMoved > 0);
        }

        public void unregisterRoute(@NotNull String routeId) {
            this.routes.remove(routeId);
        }

        public void invalidateNode(@NotNull Identifier nodeIdentifier) {
            List<String> routesToInvalidate = new ArrayList<>();
            for (Map.Entry<String, TransferRoute> entry : this.routes.entrySet()) {
                TransferRoute route = entry.getValue();
                if (route.source().nodeIdentifier().equals(nodeIdentifier)) {
                    routesToInvalidate.add(entry.getKey());
                    continue;
                }
                boolean destMatches = route.destinations().stream()
                    .anyMatch(dest -> dest.nodeIdentifier().equals(nodeIdentifier));
                if (destMatches) {
                    routesToInvalidate.add(entry.getKey());
                }
            }
            routesToInvalidate.forEach(this.routes::remove);
        }

        public void invalidateRoutesForDimension(@NotNull String dimensionPrefix) {
            List<String> toRemove = new ArrayList<>();
            for (String routeId : this.routes.keySet()) {
                if (routeId.startsWith(dimensionPrefix)) {
                    toRemove.add(routeId);
                }
            }
            toRemove.forEach(this.routes::remove);
        }

        public int routeCount() {
            return this.routes.size();
        }

        public void clear() {
            this.routes.clear();
        }

        @NotNull
        public List<String> activeRouteIds() {
            return List.copyOf(this.routes.keySet());
        }
    }
}
