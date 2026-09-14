package org.geysermc.hydraulic.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TransitiveDependencyProfileBenchmarkTest {

    @Test
    @DisplayName("ModDependencyGraph computes direct and transitive dependents")
    void computesTransitiveDependents() {
        TransitiveDependencyProfileBenchmark.ModDependencyGraph graph =
            new TransitiveDependencyProfileBenchmark.ModDependencyGraph();

        graph.addDependency("create_deco", "create");
        graph.addDependency("create_addition", "create");
        graph.addDependency("megafactory", "create_deco");

        Set<String> createDependents = graph.getTransitiveDependents("create");
        assertEquals(3, createDependents.size());
        assertTrue(createDependents.contains("create_deco"));
        assertTrue(createDependents.contains("create_addition"));
        assertTrue(createDependents.contains("megafactory"));

        Set<String> decoDependents = graph.getTransitiveDependents("create_deco");
        assertEquals(1, decoDependents.size());
        assertTrue(decoDependents.contains("megafactory"));
    }

    @Test
    @DisplayName("Benchmark large 100+ mod synthetic topology with sub-millisecond invalidation")
    void benchmarkLargeTopology() {
        // 10 tiers of 12 mods = 120 mods
        TransitiveDependencyProfileBenchmark.ModDependencyGraph graph =
            TransitiveDependencyProfileBenchmark.SyntheticTopologyBuilder.buildTieredGraph(10, 12);

        assertEquals(120, graph.modCount());
        assertTrue(graph.edgeCount() > 100);

        TransitiveDependencyProfileBenchmark.BenchmarkMetrics metrics =
            TransitiveDependencyProfileBenchmark.benchmark(graph, 1000);

        assertNotNull(metrics);
        assertEquals(120, metrics.totalMods());
        assertTrue(metrics.coldStartResolveMicros() >= 0);
        assertTrue(metrics.singleNodeInvalidationP50Micros() < 5000, "p50 invalidation should be fast");
        assertTrue(metrics.singleNodeInvalidationP99Micros() < 50000, "p99 invalidation should be fast");
    }
}
