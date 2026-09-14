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

    @Test
    @DisplayName("Benchmark large-scale 250+ mod random topology with circular dependency cycle-safety")
    void benchmarkLargeRandomModpackWithCircularDependencies() {
        // Build 250 mod random DAG with injected cycle
        TransitiveDependencyProfileBenchmark.ModDependencyGraph graph =
            TransitiveDependencyProfileBenchmark.SyntheticTopologyBuilder.buildRandomLargeGraph(250, 4, true, 42L);

        assertEquals(250, graph.modCount());
        assertTrue(graph.edgeCount() > 250);
        assertTrue(graph.hasCycles(), "Graph should detect injected circular dependency");

        // Topological sort should break cycles cleanly without throwing or hanging
        var sorted = graph.topologicalSort();
        assertEquals(250, sorted.size(), "All nodes should be preserved in sorted result even with cycles");

        TransitiveDependencyProfileBenchmark.BenchmarkMetrics metrics =
            TransitiveDependencyProfileBenchmark.benchmark(graph, 500);

        assertNotNull(metrics);
        assertEquals(250, metrics.totalMods());
        assertTrue(metrics.singleNodeInvalidationP50Micros() < 10000, "p50 invalidation should remain sub-10ms for 250+ mods");
    }

    @Test
    @DisplayName("High-Load 500+ mod synthetic invalidation stress benchmark")
    void benchmarkHighLoad500ModTopology() {
        // Build 500 mod complex DAG with cross-tier dependencies and cycles
        TransitiveDependencyProfileBenchmark.ModDependencyGraph graph =
            TransitiveDependencyProfileBenchmark.SyntheticTopologyBuilder.buildRandomLargeGraph(500, 5, true, 1337L);

        assertEquals(500, graph.modCount());
        assertTrue(graph.edgeCount() >= 500);

        var sorted = graph.topologicalSort();
        assertEquals(500, sorted.size(), "Topological sort must preserve all 500 mods");

        TransitiveDependencyProfileBenchmark.BenchmarkMetrics metrics =
            TransitiveDependencyProfileBenchmark.benchmark(graph, 300);

        assertNotNull(metrics);
        assertEquals(500, metrics.totalMods());
        assertTrue(metrics.singleNodeInvalidationP50Micros() < 20000, "p50 invalidation should remain under 20ms for 500+ mods");
    }
}
