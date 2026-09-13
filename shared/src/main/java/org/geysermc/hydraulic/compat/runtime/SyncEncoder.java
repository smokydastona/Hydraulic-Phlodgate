package org.geysermc.hydraulic.compat.runtime;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Encodes normalized sync changes into explicit Bedrock-facing update records. */
public final class SyncEncoder {
    private static final Pattern INVENTORY_SLOT = Pattern.compile("inventory\\.slot\\.(\\d+)");
    private static final Pattern CONTAINER_PROPERTY = Pattern.compile("container\\.property\\.(\\d+)");

    @NotNull
    public List<EncodedSyncChange> encode(@NotNull SyncBatch batch) {
        List<EncodedSyncChange> encoded = new ArrayList<>(batch.changes().size());
        for (SyncChange change : batch.changes()) {
            Matcher matcher = INVENTORY_SLOT.matcher(change.field());
            if (matcher.matches()) {
                int slot = Integer.parseInt(matcher.group(1));
                StackValue before = StackValue.from(change.before());
                StackValue after = StackValue.from(change.after());
                encoded.add(new EncodedSyncChange(
                    change.blockIdentifier(),
                    change.field(),
                    EncodedSyncKind.INVENTORY_SLOT,
                    slot,
                    before.itemId(),
                    before.count(),
                    after.itemId(),
                    after.count(),
                    null,
                    null,
                    change.priority(),
                    change.field(),
                    change.traceId()
                ));
                continue;
            }

            matcher = CONTAINER_PROPERTY.matcher(change.field());
            if (matcher.matches()) {
                encoded.add(new EncodedSyncChange(
                    change.blockIdentifier(),
                    change.field(),
                    EncodedSyncKind.CONTAINER_PROPERTY,
                    Integer.parseInt(matcher.group(1)),
                    null,
                    0,
                    null,
                    0,
                    change.before(),
                    change.after(),
                    change.priority(),
                    change.field(),
                    change.traceId()
                ));
            } else if (change.field().startsWith("multiblock.highlight") || change.field().startsWith("multiblock.overlay")) {
                encoded.add(new EncodedSyncChange(
                    change.blockIdentifier(),
                    change.field(),
                    EncodedSyncKind.MULTIBLOCK_HIGHLIGHT,
                    -1,
                    null,
                    0,
                    null,
                    0,
                    change.before(),
                    change.after(),
                    change.priority(),
                    change.field(),
                    change.traceId()
                ));
            } else {
                encoded.add(new EncodedSyncChange(
                    change.blockIdentifier(),
                    change.field(),
                    EncodedSyncKind.GENERIC_STATE,
                    -1,
                    null,
                    0,
                    null,
                    0,
                    change.before(),
                    change.after(),
                    change.priority(),
                    change.field(),
                    change.traceId()
                ));
            }
        }
        return List.copyOf(encoded);
    }

    private record StackValue(String itemId, int count) {
        private static StackValue from(Object value) {
            if (value instanceof TransferBridgeFactory.ItemStackView stack) {
                return new StackValue(stack.itemId(), stack.count());
            }
            if (value instanceof String string) {
                int separator = string.lastIndexOf(':');
                if (separator > 0) {
                    try {
                        return new StackValue(string.substring(0, separator), Integer.parseInt(string.substring(separator + 1)));
                    } catch (NumberFormatException ignored) {
                        return new StackValue(string, 0);
                    }
                }
                return new StackValue(string, 0);
            }
            return new StackValue("minecraft:air", 0);
        }
    }
}
