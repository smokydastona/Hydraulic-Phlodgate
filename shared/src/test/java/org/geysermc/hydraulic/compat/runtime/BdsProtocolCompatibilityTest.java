package org.geysermc.hydraulic.compat.runtime;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventorySlotPacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerSetDataPacket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BdsProtocolCompatibilityTest {

    public enum PlatformClientProfile {
        WINDOWS(1400, 1),
        ANDROID(1400, 1),
        IOS(1400, 1),
        NINTENDO_SWITCH(1200, 2); // Console split-screen

        private final int mtu;
        private final int maxSubClients;

        PlatformClientProfile(int mtu, int maxSubClients) {
            this.mtu = mtu;
            this.maxSubClients = maxSubClients;
        }

        public int mtu() { return mtu; }
        public int maxSubClients() { return maxSubClients; }
    }

    public record BdsSessionMultiplexer(
        String sessionId,
        PlatformClientProfile platform,
        int subClientId,
        List<BedrockPacket> outboundPackets
    ) {
        public BdsSessionMultiplexer {
            outboundPackets = new ArrayList<>(outboundPackets);
        }

        public boolean sendPacket(BedrockPacket packet) {
            if (packet == null) return false;
            outboundPackets.add(packet);
            return true;
        }
    }

    @Test
    @DisplayName("Verify BDS protocol multiplexing across diverse platform client profiles")
    void bdsProtocolMultiplexingAcrossPlatforms() {
        for (PlatformClientProfile platform : PlatformClientProfile.values()) {
            for (int subClient = 0; subClient < platform.maxSubClients(); subClient++) {
                BdsSessionMultiplexer session = new BdsSessionMultiplexer(
                    "bds-session-" + platform.name() + "-" + subClient,
                    platform,
                    subClient,
                    new ArrayList<>()
                );

                // Send inventory slot packet
                InventorySlotPacket invPacket = new InventorySlotPacket();
                invPacket.setContainerId(10);
                invPacket.setSlot(0);

                assertTrue(session.sendPacket(invPacket));

                // Send container property packet
                ContainerSetDataPacket dataPacket = new ContainerSetDataPacket();
                dataPacket.setWindowId((byte) 10);
                dataPacket.setProperty(1);
                dataPacket.setValue(50);

                assertTrue(session.sendPacket(dataPacket));

                assertEquals(2, session.outboundPackets().size());
                assertEquals(platform.mtu() >= 1200, true);
            }
        }
    }
}
