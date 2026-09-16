package org.geysermc.hydraulic.compat.runtime;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Converts normalized dirty state into coalesced transport-neutral synchronization changes. */
public final class SyncPlanner {
    @NotNull
    public SyncBatch plan(@NotNull StateChangeSet changes) {
        Map<String, SyncChange> coalesced = new LinkedHashMap<>();
        for (StateChangeSet.FieldChange change : changes.changes()) {
            String key = change.blockIdentifier() + "|" + change.field();
            SyncChange next = new SyncChange(
                change.blockIdentifier(),
                change.field(),
                change.before(),
                change.after(),
                priority(change.field()),
                change.traceId() != null ? change.traceId() : changes.traceId()
            );
            SyncChange previous = coalesced.get(key);
            coalesced.put(key, previous == null
                ? next
                : new SyncChange(next.blockIdentifier(), next.field(), previous.before(), next.after(), higher(previous.priority(), next.priority()), combineTrace(previous.traceId(), next.traceId())));
        }
        return new SyncBatch(new ArrayList<>(coalesced.values()));
    }

    @Nullable
    private static RuntimeTraceId combineTrace(@Nullable RuntimeTraceId first, @Nullable RuntimeTraceId second) {
        if (first == null) {
            return second;
        }
        if (second == null || first.equals(second)) {
            return first;
        }
        return null;
    }

    @NotNull
    private static SyncPriority priority(@NotNull String field) {
        return field.startsWith("inventory.") || field.startsWith("energy.") || field.startsWith("fluid.")
            ? SyncPriority.IMMEDIATE
            : SyncPriority.NEXT_CYCLE;
    }

    @NotNull
    private static SyncPriority higher(@NotNull SyncPriority left, @NotNull SyncPriority right) {
        return left.ordinal() <= right.ordinal() ? left : right;
    }
}
