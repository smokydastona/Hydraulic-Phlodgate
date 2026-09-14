package org.geysermc.hydraulic.compat.runtime;

import org.geysermc.geyser.session.GeyserSession;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coordinates auto-flushing of transaction and machine tick state deltas
 * directly to active Bedrock player sessions (Phase 8).
 */
public final class SessionAutoFlushCoordinator {
    private static final Logger LOGGER = LoggerFactory.getLogger("HydraulicSessionAutoFlush");

    private final Map<GeyserSession, SessionPipeline> activeSessions = new ConcurrentHashMap<>();

    public record SessionPipeline(
        @NotNull DirtyStateTracker dirtyStateTracker,
        @NotNull SyncDispatcher dispatcher
    ) {}

    public void registerSession(@NotNull GeyserSession session) {
        Objects.requireNonNull(session, "session");
        activeSessions.computeIfAbsent(session, s -> {
            DirtyStateTracker tracker = new DirtyStateTracker();
            SyncDispatcher dispatcher = new SyncDispatcher(tracker, new SyncPlanner(), new SyncEncoder(), new GeyserSyncTransport(s));
            return new SessionPipeline(tracker, dispatcher);
        });
    }

    public void unregisterSession(@NotNull GeyserSession session) {
        activeSessions.remove(session);
    }

    public int activeSessionCount() {
        return activeSessions.size();
    }

    /**
     * Dispatches a state change set across all active Bedrock viewer sessions and immediately flushes packets.
     */
    @NotNull
    public List<SyncDeliveryResult> autoFlushStateDeltas(@NotNull StateChangeSet changeSet) {
        Objects.requireNonNull(changeSet, "changeSet");
        if (changeSet.changes().isEmpty() || activeSessions.isEmpty()) {
            return List.of();
        }

        List<SyncDeliveryResult> allResults = new ArrayList<>();
        for (Map.Entry<GeyserSession, SessionPipeline> entry : activeSessions.entrySet()) {
            SessionPipeline pipeline = entry.getValue();
            pipeline.dirtyStateTracker().record(changeSet);
            List<SyncDeliveryResult> results = pipeline.dispatcher().flush();
            allResults.addAll(results);
            for (SyncDeliveryResult result : results) {
                if (!result.successfulHandoff() && result.status() != SyncDeliveryStatus.UNSUPPORTED) {
                    LOGGER.warn("Auto-flush sync delivery failed for {} ({}): {}",
                        result.change().blockIdentifier(), result.status(), result.reason());
                }
            }
        }
        return List.copyOf(allResults);
    }
}
