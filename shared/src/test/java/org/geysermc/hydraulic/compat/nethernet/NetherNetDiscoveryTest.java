package org.geysermc.hydraulic.compat.nethernet;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NetherNetDiscoveryTest {
    @Test
    void authenticatesAndRoundTripsDiscoveryPacket() {
        byte[] data = "CONNECTREQUEST 42 offer".getBytes(StandardCharsets.UTF_8);

        NetherNetDiscovery.Packet decoded = NetherNetDiscovery.decode(
            NetherNetDiscovery.encodeMessage(0x1122334455667788L, 99L, new String(data, StandardCharsets.UTF_8))
        );

        assertEquals(NetherNetDiscovery.MESSAGE, decoded.packetId());
        assertEquals(0x1122334455667788L, decoded.senderId());
        assertEquals(99L, decoded.recipientId());
        assertArrayEquals(data, decoded.data());
    }

    @Test
    void rejectsTamperedAuthenticationAndLength() {
        byte[] wire = NetherNetDiscovery.encodeRequest(7L);
        wire[0] ^= 1;
        assertThrows(IllegalArgumentException.class, () -> NetherNetDiscovery.decode(wire));

        byte[] truncated = Arrays.copyOf(wire, wire.length - 1);
        assertThrows(IllegalArgumentException.class, () -> NetherNetDiscovery.decode(truncated));
    }

    @Test
    void roundTripsHexEncodedServerData() {
        NetherNetDiscovery.ServerData expected = new NetherNetDiscovery.ServerData(
            NetherNetDiscovery.SERVER_DATA_VERSION, "Hydraulic", 800, "26.2", "Overworld", 3, 20, 0,
            false, false, true, true, "nonce", NetherNetDiscovery.CONNECTION_TYPE_LAN
        );

        byte[] encoded = NetherNetDiscovery.encodeServerData(expected);
        NetherNetDiscovery.ServerData actual = NetherNetDiscovery.decodeServerData(encoded);

        assertEquals(expected, actual);

        NetherNetDiscovery.Packet response = NetherNetDiscovery.decode(NetherNetDiscovery.encodeResponse(7L, expected));
        assertArrayEquals(encoded, response.data());
    }

    @Test
    void rejectsMalformedServerData() {
        assertThrows(IllegalArgumentException.class,
            () -> NetherNetDiscovery.decodeServerData("not-hex".getBytes(StandardCharsets.US_ASCII)));
        assertThrows(IllegalArgumentException.class,
            () -> NetherNetDiscovery.decodeServerData("00".getBytes(StandardCharsets.US_ASCII)));
    }
}