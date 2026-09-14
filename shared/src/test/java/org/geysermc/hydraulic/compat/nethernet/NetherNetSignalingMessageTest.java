package org.geysermc.hydraulic.compat.nethernet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NetherNetSignalingMessageTest {
    @Test
    void parsesAndSerializesSupportedNegotiationMessages() {
        NetherNetSignalingMessage message = NetherNetSignalingMessage.parse(
            "CANDIDATEADD 7 candidate:1 1 udp 123 127.0.0.1 19132 typ host"
        );

        assertEquals(NetherNetSignalingMessage.Type.CANDIDATEADD, message.type());
        assertEquals(7L, message.connectionId());
        assertEquals("CANDIDATEADD 7 candidate:1 1 udp 123 127.0.0.1 19132 typ host", message.toString());
    }

    @Test
    void rejectsUnknownOrMalformedNegotiationMessages() {
        assertThrows(IllegalArgumentException.class, () -> NetherNetSignalingMessage.parse("UNKNOWN id data"));
        assertThrows(IllegalArgumentException.class, () -> NetherNetSignalingMessage.parse("CONNECTREQUEST id"));
        assertThrows(IllegalArgumentException.class, () -> NetherNetSignalingMessage.parse("CONNECTREQUEST nope data"));
        assertThrows(IllegalArgumentException.class, () -> NetherNetSignalingMessage.parse("CONNECTREQUEST id data\0"));
        assertEquals(NetherNetSignalingMessage.Type.CONNECTERROR, NetherNetSignalingMessage.parse("CONNECTERROR 0 37").type());
    }
}