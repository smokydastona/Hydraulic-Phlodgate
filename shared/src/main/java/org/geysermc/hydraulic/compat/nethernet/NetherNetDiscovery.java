package org.geysermc.hydraulic.compat.nethernet;

import org.jetbrains.annotations.NotNull;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

public final class NetherNetDiscovery {
    public static final int PORT = 7551;
    public static final int REQUEST = 0;
    public static final int RESPONSE = 1;
    public static final int MESSAGE = 2;
    public static final int SERVER_DATA_VERSION = 7;
    public static final int CONNECTION_TYPE_LAN = 4;
    private static final int HMAC_SIZE = 32;
    private static final int MAX_PAYLOAD_LENGTH = 0xffff;
    private static final byte[] KEY = sha256(ByteBuffer.allocate(Long.BYTES).order(ByteOrder.LITTLE_ENDIAN).putLong(0xdeadbeefL).array());

    private NetherNetDiscovery() {
    }

    @NotNull
    public static byte[] encodeRequest(long senderId) {
        return encode(senderId, REQUEST, 0L, new byte[0]);
    }

    @NotNull
    public static byte[] encodeResponse(long senderId, @NotNull ServerData data) {
        byte[] encoded = HexFormat.of().formatHex(encodeServerData(data)).getBytes(StandardCharsets.US_ASCII);
        return encode(senderId, RESPONSE, 0L, encoded);
    }

    @NotNull
    public static byte[] encodeMessage(long senderId, long recipientId, @NotNull String data) {
        return encode(senderId, MESSAGE, recipientId, Objects.requireNonNull(data, "data").getBytes(StandardCharsets.UTF_8));
    }

    @NotNull
    private static byte[] encode(long senderId, int packetId, long recipientId, byte[] data) {
        if (data.length > MAX_PAYLOAD_LENGTH || (packetId == REQUEST && data.length != 0)) {
            throw new IllegalArgumentException("Discovery payload exceeds its wire limit");
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream(2 + 8 + 8 + data.length + 8);
        writeU16(body, packetId);
        writeU64(body, senderId);
        body.writeBytes(new byte[8]);
        if (packetId == RESPONSE || packetId == MESSAGE) {
            if (packetId == MESSAGE) {
                writeU64(body, recipientId);
            }
            writeU32(body, data.length);
            body.writeBytes(data);
        }
        byte[] payload = new byte[2 + body.size()];
        ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN).putShort((short) payload.length).put(body.toByteArray());
        byte[] encrypted = crypt(Cipher.ENCRYPT_MODE, pad(payload));
        return concat(hmac(payload), encrypted);
    }

    @NotNull
    public static Packet decode(@NotNull byte[] wire) {
        Objects.requireNonNull(wire, "wire");
        if (wire.length < HMAC_SIZE + 16 || (wire.length - HMAC_SIZE) % 16 != 0) {
            throw new IllegalArgumentException("Invalid NetherNet discovery packet length");
        }
        byte[] payload = unpad(crypt(Cipher.DECRYPT_MODE, Arrays.copyOfRange(wire, HMAC_SIZE, wire.length)));
        if (!MessageDigest.isEqual(hmac(payload), Arrays.copyOf(wire, HMAC_SIZE))) {
            throw new IllegalArgumentException("Invalid NetherNet discovery authentication tag");
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        int declaredLength = Short.toUnsignedInt(readU16(buffer, "packet length"));
        if (declaredLength != payload.length) {
            throw new IllegalArgumentException("Discovery packet length does not match payload");
        }
        int packetId = Short.toUnsignedInt(readU16(buffer, "packet id"));
        long senderId = readU64(buffer, "sender id");
        require(buffer, 8, "reserved header");
        buffer.position(buffer.position() + 8);
        long recipientId = 0L;
        byte[] data = new byte[0];
        if (packetId == RESPONSE || packetId == MESSAGE) {
            if (packetId == MESSAGE) {
                recipientId = readU64(buffer, "recipient id");
            }
            long length = Integer.toUnsignedLong(readU32(buffer, "payload length"));
            if (length > MAX_PAYLOAD_LENGTH || length > buffer.remaining()) {
                throw new IllegalArgumentException("Discovery payload length is invalid");
            }
            data = new byte[(int) length];
            buffer.get(data);
            if (packetId == RESPONSE) {
                try {
                    data = HexFormat.of().parseHex(new String(data, StandardCharsets.US_ASCII));
                } catch (IllegalArgumentException exception) {
                    throw new IllegalArgumentException("Discovery response data is not valid hexadecimal", exception);
                }
            }
        } else if (packetId != REQUEST) {
            throw new IllegalArgumentException("Unknown discovery packet id: " + packetId);
        }
        if (buffer.hasRemaining()) {
            throw new IllegalArgumentException("Discovery packet has trailing bytes");
        }
        return new Packet(packetId, senderId, recipientId, data);
    }

    @NotNull
    public static byte[] encodeServerData(@NotNull ServerData data) {
        Objects.requireNonNull(data, "data");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(data.version());
        writeString(output, data.serverName());
        writeVarInt(output, data.protocol());
        writeString(output, data.versionName());
        writeString(output, data.levelName());
        writeVarInt(output, data.playerCount());
        writeVarInt(output, data.maxPlayerCount());
        writeVarInt(output, data.gameType());
        output.write(data.editorWorld() ? 1 : 0);
        output.write(data.hardcore() ? 1 : 0);
        output.write(data.acceptsOnlineAuth() ? 1 : 0);
        output.write(data.acceptsSelfSignedAuth() ? 1 : 0);
        writeString(output, data.nonce());
        writeVarInt(output, data.connectionType());
        return output.toByteArray();
    }

    @NotNull
    public static ServerData decodeServerData(@NotNull byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        Cursor cursor = new Cursor(encoded);
        int version = cursor.readU8("version");
        if (version != SERVER_DATA_VERSION) {
            throw new IllegalArgumentException("Unsupported discovery server-data version: " + version);
        }
        ServerData data = new ServerData(version, cursor.readString("server name"), cursor.readVarInt("protocol"),
            cursor.readString("game version"), cursor.readString("level name"), cursor.readVarInt("player count"),
            cursor.readVarInt("max player count"), cursor.readVarInt("game type"), cursor.readU8("editor world") != 0,
            cursor.readU8("hardcore") != 0, cursor.readU8("online authentication") != 0,
            cursor.readU8("self-signed authentication") != 0, cursor.readString("nonce"), cursor.readVarInt("connection type"));
        if (cursor.remaining() != 0) {
            throw new IllegalArgumentException("Discovery server data has trailing bytes");
        }
        return data;
    }

    private static void writeU16(ByteArrayOutputStream output, int value) {
        output.write(value & 0xff); output.write((value >>> 8) & 0xff);
    }

    private static void writeU32(ByteArrayOutputStream output, int value) {
        for (int shift = 0; shift < 32; shift += 8) output.write((value >>> shift) & 0xff);
    }

    private static void writeU64(ByteArrayOutputStream output, long value) {
        for (int shift = 0; shift < 64; shift += 8) output.write((int) (value >>> shift) & 0xff);
    }

    private static void writeString(ByteArrayOutputStream output, String value) {
        byte[] bytes = Objects.requireNonNull(value, "value").getBytes(StandardCharsets.UTF_8);
        writeUnsignedVarInt(output, bytes.length); output.writeBytes(bytes);
    }

    private static void writeVarInt(ByteArrayOutputStream output, int value) {
        int zigzag = (value << 1) ^ (value >> 31);
        while ((zigzag & ~0x7f) != 0) { output.write((zigzag & 0x7f) | 0x80); zigzag >>>= 7; }
        output.write(zigzag);
    }

    private static void writeUnsignedVarInt(ByteArrayOutputStream output, int value) {
        while ((value & ~0x7f) != 0) {
            output.write((value & 0x7f) | 0x80);
            value >>>= 7;
        }
        output.write(value);
    }

    private static short readU16(ByteBuffer buffer, String field) { require(buffer, 2, field); return buffer.getShort(); }
    private static int readU32(ByteBuffer buffer, String field) { require(buffer, 4, field); return buffer.getInt(); }
    private static long readU64(ByteBuffer buffer, String field) { require(buffer, 8, field); return buffer.getLong(); }
    private static void require(ByteBuffer buffer, int length, String field) {
        if (buffer.remaining() < length) throw new IllegalArgumentException("Discovery packet is truncated at " + field);
    }

    private static byte[] hmac(byte[] value) {
        try { Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(KEY, "HmacSHA256")); return mac.doFinal(value); }
        catch (GeneralSecurityException exception) { throw new IllegalStateException("JRE does not provide HmacSHA256", exception); }
    }

    private static byte[] crypt(int mode, byte[] value) {
        try { Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding"); cipher.init(mode, new SecretKeySpec(KEY, "AES")); return cipher.doFinal(value); }
        catch (GeneralSecurityException exception) { throw new IllegalStateException("JRE does not provide AES/ECB", exception); }
    }

    private static byte[] pad(byte[] value) { int padding = 16 - value.length % 16; byte[] result = Arrays.copyOf(value, value.length + padding); Arrays.fill(result, value.length, result.length, (byte) padding); return result; }
    private static byte[] unpad(byte[] value) {
        if (value.length == 0) throw new IllegalArgumentException("Discovery packet has no payload");
        int padding = Byte.toUnsignedInt(value[value.length - 1]);
        if (padding == 0 || padding > 16 || padding > value.length) throw new IllegalArgumentException("Invalid discovery padding");
        for (int index = value.length - padding; index < value.length; index++) if (Byte.toUnsignedInt(value[index]) != padding) throw new IllegalArgumentException("Invalid discovery padding");
        return Arrays.copyOf(value, value.length - padding);
    }
    private static byte[] concat(byte[] first, byte[] second) { byte[] result = Arrays.copyOf(first, first.length + second.length); System.arraycopy(second, 0, result, first.length, second.length); return result; }
    private static byte[] sha256(byte[] value) { try { return MessageDigest.getInstance("SHA-256").digest(value); } catch (GeneralSecurityException exception) { throw new ExceptionInInitializerError(exception); } }

    public record Packet(int packetId, long senderId, long recipientId, @NotNull byte[] data) {
        public Packet { data = Objects.requireNonNull(data, "data").clone(); }
        @Override public byte[] data() { return data.clone(); }
    }

    public record ServerData(int version, @NotNull String serverName, int protocol, @NotNull String versionName,
                             @NotNull String levelName, int playerCount, int maxPlayerCount, int gameType,
                             boolean editorWorld, boolean hardcore, boolean acceptsOnlineAuth,
                             boolean acceptsSelfSignedAuth, @NotNull String nonce, int connectionType) {
        public ServerData {
            if (version != SERVER_DATA_VERSION || playerCount < 0 || maxPlayerCount < 0) throw new IllegalArgumentException("Invalid discovery server metadata");
            Objects.requireNonNull(serverName, "serverName"); Objects.requireNonNull(versionName, "versionName"); Objects.requireNonNull(levelName, "levelName"); Objects.requireNonNull(nonce, "nonce");
        }
    }

    private static final class Cursor {
        private final byte[] data; private int offset;
        private Cursor(byte[] data) { this.data = data; }
        private int remaining() { return data.length - offset; }
        private int readU8(String field) { if (remaining() < 1) throw new IllegalArgumentException("Discovery server data is truncated at " + field); return Byte.toUnsignedInt(data[offset++]); }
        private int readVarInt(String field) { int value = 0; for (int shift = 0; shift < 35; shift += 7) { int next = readU8(field); value |= (next & 0x7f) << shift; if ((next & 0x80) == 0) return (value >>> 1) ^ -(value & 1); } throw new IllegalArgumentException("Discovery varint is too long at " + field); }
        private String readString(String field) { int length = readUnsignedVarInt(field + " length"); if (length < 0 || length > remaining()) throw new IllegalArgumentException("Discovery string length is invalid at " + field); String value = new String(data, offset, length, StandardCharsets.UTF_8); offset += length; return value; }
        private int readUnsignedVarInt(String field) { int value = 0; for (int shift = 0; shift < 35; shift += 7) { int next = readU8(field); value |= (next & 0x7f) << shift; if ((next & 0x80) == 0) return value; } throw new IllegalArgumentException("Discovery unsigned varint is too long at " + field); }
    }
}
