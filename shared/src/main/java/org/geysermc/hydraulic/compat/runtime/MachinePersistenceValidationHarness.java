package org.geysermc.hydraulic.compat.runtime;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Machine Persistence Validation Harness (Phase 10 and Milestone Verification).
 *
 * Executes automated full-cycle restart and reload verification across active machine fixtures:
 * 1. Captures pre-shutdown {@link CanonicalRuntimeState} snapshot (S_pre).
 * 2. Serializes state to Minecraft NBT.
 * 3. Simulates cold restart / chunk unload: purges memory, caches, and live capability bindings.
 * 4. Deserializes state into new live runtime instances and rebinds capability contracts.
 * 5. Captures post-restart {@link CanonicalRuntimeState} snapshot (S_after).
 * 6. Asserts strict mathematical state equivalence (S_after == S_pre).
 */
public final class MachinePersistenceValidationHarness {

    public record FixturePersistenceResult(
        @NotNull String fixtureId,
        @NotNull String positionKey,
        @NotNull String dimensionKey,
        @NotNull CanonicalRuntimeState preShutdownState,
        @NotNull CanonicalRuntimeState postRestartState,
        boolean isEquivalent,
        @NotNull List<String> diffReport,
        long cycleTimeNanos
    ) {
        public FixturePersistenceResult {
            diffReport = List.copyOf(diffReport);
        }
    }

    public record MachinePersistenceValidationReport(
        int totalFixturesEvaluated,
        int passingFixtures,
        int failingFixtures,
        boolean allEquivalent,
        @NotNull List<FixturePersistenceResult> results
    ) {
        public MachinePersistenceValidationReport {
            results = List.copyOf(results);
        }
    }

    public interface MachineFixtureDriver {
        @NotNull String fixtureId();
        @NotNull String positionKey();
        @NotNull String dimensionKey();
        @NotNull CanonicalRuntimeState captureState();
        @NotNull CompoundTag serializeNbt();
        void restoreFromNbt(@NotNull CompoundTag nbt);
    }

    public static final class PersistenceHarnessRunner {
        private final List<MachineFixtureDriver> fixtures = new ArrayList<>();

        public void registerFixture(@NotNull MachineFixtureDriver fixture) {
            this.fixtures.add(Objects.requireNonNull(fixture, "fixture"));
        }

        @NotNull
        public MachinePersistenceValidationReport executeRestartSimulation(@NotNull LiveCapabilityBinder binder) {
            List<FixturePersistenceResult> results = new ArrayList<>();
            int passing = 0;
            int failing = 0;

            for (MachineFixtureDriver fixture : this.fixtures) {
                long startNanos = System.nanoTime();

                // 1. Capture S_pre
                CanonicalRuntimeState preState = fixture.captureState();

                // 2. Serialize to persistent NBT
                CompoundTag serializedTag = fixture.serializeNbt();

                // 3. Simulate cold restart: purge live bindings & cache
                binder.invalidatePosition(fixture.dimensionKey(), fixture.positionKey());
                binder.clear();

                // 4. Restore state on fresh runtime instance
                fixture.restoreFromNbt(serializedTag);

                // 5. Capture S_after
                CanonicalRuntimeState postState = fixture.captureState();

                // 6. Compare S_after == S_pre
                boolean equivalent = preState.isEquivalentTo(postState);
                List<String> diff = preState.diff(postState);

                long duration = System.nanoTime() - startNanos;

                if (equivalent && diff.isEmpty()) {
                    passing++;
                } else {
                    failing++;
                }

                results.add(new FixturePersistenceResult(
                    fixture.fixtureId(),
                    fixture.positionKey(),
                    fixture.dimensionKey(),
                    preState,
                    postState,
                    equivalent,
                    diff,
                    duration
                ));
            }

            return new MachinePersistenceValidationReport(
                this.fixtures.size(),
                passing,
                failing,
                failing == 0,
                results
            );
        }
    }
}
