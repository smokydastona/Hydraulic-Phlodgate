package org.geysermc.hydraulic.cache;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Transitive Dependent Mod Invalidation Profiler and Benchmark (Phase 9/10).
 * Simulates, profiles, and verifies cold-start and incremental cache invalidation
 * over large transitive dependency graphs (100+ mods).
 */
public final class TransitiveDependencyProfileBenchmark {

    public record BenchmarkMetrics(
        int totalMods,
        int totalEdges,
        long coldStartResolveMicros,
        long singleNodeInvalidationP50Micros,
        long singleNodeInvalidationP95Micros,
        long singleNodeInvalidationP99Micros,
        long singleNodeInvalidationMaxMicros,
        double averageTransitCount
    ) {}

    public static final class ModDependencyGraph {
        private final Map<String, Set<String>> dependencies = new LinkedHashMap<>();
        private final Map<String, Set<String>> dependents = new LinkedHashMap<>();

        public void addMod(@NotNull String modId) {
            dependencies.putIfAbsent(modId, new LinkedHashSet<>());
            dependents.putIfAbsent(modId, new LinkedHashSet<>());
        }

        public void addDependency(@NotNull String modId, @NotNull String dependsOn) {
            addMod(modId);
            addMod(dependsOn);
            dependencies.get(modId).add(dependsOn);
            dependents.get(dependsOn).add(modId);
        }

        @NotNull
        public Set<String> getDirectDependencies(@NotNull String modId) {
            return dependencies.getOrDefault(modId, Collections.emptySet());
        }

        @NotNull
        public Set<String> getDirectDependents(@NotNull String modId) {
            return dependents.getOrDefault(modId, Collections.emptySet());
        }

        @NotNull
        public Set<String> getTransitiveDependents(@NotNull String rootModId) {
            Set<String> result = new LinkedHashSet<>();
            Deque<String> queue = new ArrayDeque<>();
            queue.add(rootModId);

            while (!queue.isEmpty()) {
                String current = queue.poll();
                for (String dependent : getDirectDependents(current)) {
                    if (result.add(dependent)) {
                        queue.add(dependent);
                    }
                }
            }
            return result;
        }

        @NotNull
        public List<String> topologicalSort() {
            Map<String, Integer> inDegree = new LinkedHashMap<>();
            for (String modId : dependencies.keySet()) {
                inDegree.put(modId, 0);
            }
            for (Set<String> deps : dependencies.values()) {
                for (String dep : deps) {
                    inDegree.put(dep, inDegree.getOrDefault(dep, 0) + 1);
                }
            }

            Deque<String> queue = new ArrayDeque<>();
            for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
                if (entry.getValue() == 0) {
                    queue.add(entry.getKey());
                }
            }

            List<String> sorted = new ArrayList<>();
            while (!queue.isEmpty()) {
                String current = queue.poll();
                sorted.add(current);
                for (String dep : getDirectDependencies(current)) {
                    int remaining = inDegree.get(dep) - 1;
                    inDegree.put(dep, remaining);
                    if (remaining == 0) {
                        queue.add(dep);
                    }
                }
            }
            return sorted;
        }

        public int modCount() {
            return dependencies.size();
        }

        public int edgeCount() {
            int count = 0;
            for (Set<String> set : dependencies.values()) {
                count += set.size();
            }
            return count;
        }
    }

    public static final class SyntheticTopologyBuilder {
        private SyntheticTopologyBuilder() {
        }

        @NotNull
        public static ModDependencyGraph buildTieredGraph(int tiers, int modsPerTier) {
            ModDependencyGraph graph = new ModDependencyGraph();

            for (int t = 0; t < tiers; t++) {
                for (int m = 0; m < modsPerTier; m++) {
                    String modId = "mod_t" + t + "_m" + m;
                    graph.addMod(modId);

                    if (t > 0) {
                        // Depend on 2 mods from the previous tier
                        int parent1 = m % modsPerTier;
                        int parent2 = (m + 1) % modsPerTier;
                        graph.addDependency(modId, "mod_t" + (t - 1) + "_m" + parent1);
                        if (parent1 != parent2) {
                            graph.addDependency(modId, "mod_t" + (t - 1) + "_m" + parent2);
                        }
                    }
                }
            }
            return graph;
        }
    }

    @NotNull
    public static BenchmarkMetrics benchmark(@NotNull ModDependencyGraph graph, int trials) {
        long coldStartStart = System.nanoTime();
        List<String> sorted = graph.topologicalSort();
        long coldStartMicros = (System.nanoTime() - coldStartStart) / 1_000L;

        List<String> allMods = new ArrayList<>(graph.dependencies.keySet());
        long[] durations = new long[trials];
        int totalTransitiveCount = 0;

        for (int i = 0; i < trials; i++) {
            String target = allMods.get(i % allMods.size());
            long start = System.nanoTime();
            Set<String> affected = graph.getTransitiveDependents(target);
            long elapsed = (System.nanoTime() - start) / 1_000L;
            durations[i] = elapsed;
            totalTransitiveCount += affected.size();
        }

        Arrays.sort(durations);
        long p50 = durations[(int) (trials * 0.50)];
        long p95 = durations[(int) (trials * 0.95)];
        long p99 = durations[(int) (trials * 0.99)];
        long max = durations[trials - 1];
        double avgCount = (double) totalTransitiveCount / trials;

        return new BenchmarkMetrics(
            graph.modCount(),
            graph.edgeCount(),
            coldStartMicros,
            p50,
            p95,
            p99,
            max,
            avgCount
        );
    }
}
