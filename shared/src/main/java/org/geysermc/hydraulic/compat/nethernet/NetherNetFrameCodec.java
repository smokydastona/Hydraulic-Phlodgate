package org.geysermc.hydraulic.compat.nethernet;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class NetherNetFrameCodec {
    public static final int MAX_MESSAGE_SIZE = 262_143;
    public static final int MAX_SEGMENTS = 256;

    private NetherNetFrameCodec() {
    }

    @NotNull
    public static List<byte[]> segment(@NotNull byte[] packet) {
        return segment(packet, true);
    }

    @NotNull
    public static List<byte[]> segment(@NotNull byte[] packet, boolean reliable) {
        Objects.requireNonNull(packet, "packet");
        int count = Math.max(1, (packet.length + MAX_MESSAGE_SIZE - 1) / MAX_MESSAGE_SIZE);
        if (!reliable && count > 1) throw new IllegalArgumentException("Unreliable NetherNet messages cannot be segmented");
        if (count > MAX_SEGMENTS) throw new IllegalArgumentException("NetherNet message exceeds the 256-segment limit");
        List<byte[]> result = new ArrayList<>(count);
        for (int offset = 0; offset < packet.length || result.isEmpty(); offset += MAX_MESSAGE_SIZE) {
            int length = Math.min(MAX_MESSAGE_SIZE, packet.length - offset);
            byte[] segment = new byte[length + 1];
            segment[0] = (byte) (count - 1 - result.size());
            System.arraycopy(packet, offset, segment, 1, length);
            result.add(segment);
        }
        return List.copyOf(result);
    }

    public static final class Reassembler {
        private final ByteArrayOutputStream data = new ByteArrayOutputStream();
        private int remaining = -1;

        public void reset() {
            data.reset();
            remaining = -1;
        }

        @Nullable
        public byte[] accept(@NotNull byte[] segment) {
            Objects.requireNonNull(segment, "segment");
            if (segment.length < 2) throw new IllegalArgumentException("NetherNet segment carries no payload");
            int next = Byte.toUnsignedInt(segment[0]);
            if (remaining >= 0 && next != remaining - 1) {
                reset();
                throw new IllegalArgumentException("NetherNet segment sequence is invalid");
            }
            if (data.size() + segment.length - 1 > MAX_MESSAGE_SIZE * MAX_SEGMENTS) {
                reset();
                throw new IllegalArgumentException("NetherNet message exceeds the safety limit");
            }
            remaining = next;
            data.write(segment, 1, segment.length - 1);
            if (remaining > 0) return null;
            byte[] complete = data.toByteArray();
            reset();
            return complete;
        }
    }
}
