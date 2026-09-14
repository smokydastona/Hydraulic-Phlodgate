package org.geysermc.hydraulic.compat.discovery;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticDiscoveryEngineTest {
    @Test
    void unregisteredContentDoesNotReceiveRegistryCapabilityFacts() {
        SemanticDiscoveryEngine.DiscoveredSemanticProfile profile = SemanticDiscoveryEngine.discover(
            Identifier.fromNamespaceAndPath("example", "missing"),
            Map.of(),
            false
        );

        assertFalse(profile.facts().containsKey("channel_a.registry_discovered"));
        assertTrue(profile.evidenceList().stream().noneMatch(evidence -> evidence.channel() == SemanticDiscoveryEngine.Channel.MINECRAFT_REGISTRY));
    }

    @Test
    void registeredContentReceivesRegistryEvidence() {
        SemanticDiscoveryEngine.DiscoveredSemanticProfile profile = SemanticDiscoveryEngine.discover(
            Identifier.fromNamespaceAndPath("example", "registered"),
            Map.of(),
            true
        );

        assertTrue(profile.facts().containsKey("channel_a.registry_discovered"));
        assertTrue(profile.evidenceList().stream().anyMatch(evidence -> evidence.channel() == SemanticDiscoveryEngine.Channel.MINECRAFT_REGISTRY));
    }

    @Test
    void runtimeObjectContractProducesExecutableCapabilityFacts() {
        SemanticDiscoveryEngine.DiscoveredSemanticProfile profile = SemanticDiscoveryEngine.discoverRuntimeObject(
            Identifier.fromNamespaceAndPath("example", "machine"),
            new RuntimeMachineShape(),
            Map.of()
        );

        assertEquals("true", profile.facts().get("has_inventory"));
        assertEquals("true", profile.facts().get("can_insert"));
        assertEquals("true", profile.facts().get("can_extract"));
        assertEquals("true", profile.facts().get("has_fluid"));
        assertEquals("true", profile.facts().get("has_energy"));
        assertEquals("true", profile.facts().get("has_processing"));
        assertTrue(profile.evidenceList().stream().anyMatch(evidence -> evidence.capabilityKey().equals("ITEM_TRANSFER_CONTRACT")));
        assertTrue(profile.evidenceList().stream().anyMatch(evidence -> evidence.capabilityKey().equals("FLUID_TRANSFER_CONTRACT")));
        assertTrue(profile.evidenceList().stream().anyMatch(evidence -> evidence.capabilityKey().equals("ENERGY_TRANSFER_CONTRACT")));
    }

    private static final class RuntimeMachineShape {
        public int getContainerSize() { return 2; }
        public Object getItem(int slot) { return null; }
        public Object insertItem(Object stack) { return stack; }
        public Object extractItem(Object stack) { return stack; }
        public int tankCount() { return 1; }
        public int fill(Object fluid) { return 0; }
        public int drain(Object fluid) { return 0; }
        public int receiveEnergy(int amount) { return amount; }
        public int extractEnergy(int amount) { return amount; }
        public int getEnergyStored() { return 0; }
        public Object getCurrentRecipe() { return null; }
        public int getProgress() { return 0; }
        public void tick() {}
    }
}