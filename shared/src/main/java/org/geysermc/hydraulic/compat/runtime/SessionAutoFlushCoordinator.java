package org.geysermc.hydraulic.compat.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.hydraulic.HydraulicImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    /**
     * Reconciles pipelines with Geyser's current connection snapshot so disconnected
     * sessions cannot retain dirty-state and transport references after a machine tick.
     */
    public void reconcileSessions(@NotNull Iterable<? extends GeyserSession> sessions) {
        Objects.requireNonNull(sessions, "sessions");
        Set<GeyserSession> currentSessions = new HashSet<>();
        for (GeyserSession session : sessions) {
            if (session != null) {
                currentSessions.add(session);
                registerSession(session);
            }
        }
        activeSessions.keySet().removeIf(session -> !currentSessions.contains(session));
    }

    public int activeSessionCount() {
        return activeSessions.size();
    }

    /**
     * Dispatches a state change set across all active Bedrock viewer sessions and immediately flushes packets.
     */
    @NotNull
    public List<SyncDeliveryResult> autoFlushStateDeltas(@NotNull StateChangeSet changeSet) {
        return autoFlushStateDeltas(changeSet, null, null);
    }

    @NotNull
    public List<SyncDeliveryResult> autoFlushStateDeltas(
        @NotNull StateChangeSet changeSet,
        @Nullable ServerLevel level,
        @Nullable BlockPos position
    ) {
        Objects.requireNonNull(changeSet, "changeSet");
        if (changeSet.changes().isEmpty() || activeSessions.isEmpty()) {
            return List.of();
        }

        List<SyncDeliveryResult> allResults = new ArrayList<>();
        for (Map.Entry<GeyserSession, SessionPipeline> entry : activeSessions.entrySet()) {
            if (level != null && position != null && !tracksPosition(entry.getKey(), level, position)) {
                continue;
            }
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

    private boolean tracksPosition(@NotNull GeyserSession session, @NotNull ServerLevel level, @NotNull BlockPos position) {
        try {
            ServerPlayer player = HydraulicImpl.instance().server().getPlayerList().getPlayer(session.javaUuid());
            return player != null
                && player.level() == level
                && player.distanceToSqr(position.getX() + 0.5D, position.getY() + 0.5D, position.getZ() + 0.5D) <= 128D * 128D;
        } catch (IllegalStateException ignored) {
            return false;
        }
    }
}
