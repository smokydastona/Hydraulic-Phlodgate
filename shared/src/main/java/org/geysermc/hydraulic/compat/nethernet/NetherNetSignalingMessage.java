package org.geysermc.hydraulic.compat.nethernet;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public record NetherNetSignalingMessage(@NotNull Type type, long connectionId, @NotNull String data) {
    private static final int MAX_MESSAGE_LENGTH = 256 * 1024;

    public NetherNetSignalingMessage {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(data, "data");
        if (data.indexOf('\0') >= 0 || encodedLength(type, connectionId, data) > MAX_MESSAGE_LENGTH) throw new IllegalArgumentException("NetherNet signaling message is invalid or too large");
    }

    @NotNull
    public static NetherNetSignalingMessage parse(@NotNull String value) {
        Objects.requireNonNull(value, "value");
        if (value.length() > MAX_MESSAGE_LENGTH) throw new IllegalArgumentException("NetherNet signaling message exceeds safety limit");
        int first = value.indexOf(' ');
        int second = first < 0 ? -1 : value.indexOf(' ', first + 1);
        if (first <= 0 || second <= first + 1 || second == value.length() - 1) throw new IllegalArgumentException("NetherNet signaling message must be TYPE CONNECTION_ID DATA");
        long connectionId;
        try { connectionId = Long.parseUnsignedLong(value.substring(first + 1, second)); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("NetherNet connection id is not uint64", exception); }
        return new NetherNetSignalingMessage(Type.parse(value.substring(0, first)), connectionId, value.substring(second + 1));
    }

    @Override
    public String toString() { return type.name() + " " + Long.toUnsignedString(connectionId) + " " + data; }
    private static int encodedLength(Type type, long connectionId, String data) { return type.name().length() + 2 + Long.toUnsignedString(connectionId).length() + data.length(); }

    public enum Type {
        CONNECTREQUEST,
        CONNECTRESPONSE,
        CANDIDATEADD,
        CONNECTERROR;

        private static Type parse(String value) {
            try { return valueOf(value); }
            catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Unsupported NetherNet signaling message type: " + value, exception); }
        }
    }
}
