package org.geysermc.hydraulic.compat.nethernet;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NetherNetFrameCodecTest {
    @Test
    void roundTripsSingleReliablePacket() {
        byte[] packet = {0, 1, 2, 3};
        List<byte[]> segments = NetherNetFrameCodec.segment(packet);
        NetherNetFrameCodec.Reassembler reassembler = new NetherNetFrameCodec.Reassembler();

        assertEquals(1, segments.size());
        assertArrayEquals(packet, reassembler.accept(segments.getFirst()));
    }

    @Test
    void roundTripsPacketAcrossTenThousandByteSegments() {
        byte[] packet = new byte[300_000];
        for (int index = 0; index < packet.length; index++) {
            packet[index] = (byte) index;
        }
        List<byte[]> segments = NetherNetFrameCodec.segment(packet);
        NetherNetFrameCodec.Reassembler reassembler = new NetherNetFrameCodec.Reassembler();

        assertEquals(2, segments.size());
        assertNull(reassembler.accept(segments.get(0)));
        assertArrayEquals(packet, reassembler.accept(segments.get(1)));
    }

    @Test
    void rejectsOutOfOrderSegmentsAndMalformedFrames() {
        List<byte[]> segments = NetherNetFrameCodec.segment(new byte[600_000]);
        NetherNetFrameCodec.Reassembler reassembler = new NetherNetFrameCodec.Reassembler();
        assertNull(reassembler.accept(segments.getFirst()));
        assertThrows(IllegalArgumentException.class, () -> reassembler.accept(segments.get(2)));

        assertThrows(IllegalArgumentException.class, () -> new NetherNetFrameCodec.Reassembler().accept(new byte[]{1}));
    }

    @Test
    void enforcesPacketAndSegmentBounds() {
        assertThrows(IllegalArgumentException.class, () -> NetherNetFrameCodec.segment(new byte[262_143 * 256 + 1]));
        assertThrows(IllegalArgumentException.class, () -> NetherNetFrameCodec.segment(new byte[262_144], false));
        assertThrows(IllegalArgumentException.class, () -> new NetherNetFrameCodec.Reassembler().accept(new byte[]{0}));
    }
}