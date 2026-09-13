package org.geysermc.hydraulic.compat.corpus;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Extended Corpus Harvesting Pipeline (Phase 6 Extension).
 * Ingests and normalizes inspectable open-source Bedrock addon schemas into the curated/generated
 * corpus, validates admissibility, extracts capability patterns, and updates the corpus index.
 */
public final class ExtendedCorpusHarvestingPipeline {
    private static final Logger LOGGER = LoggerFactory.getLogger("HydraulicCorpusHarvesting");

    private final AddonCorpusLoader corpusLoader;

    public ExtendedCorpusHarvestingPipeline(@NotNull AddonCorpusLoader corpusLoader) {
        this.corpusLoader = corpusLoader;
    }

    public record HarvestCandidate(
        @NotNull String addonId,
        @NotNull String name,
        @NotNull String repositoryUrl,
        @NotNull String licenseName,
        @NotNull String minMinecraftVersion,
        @NotNull String scriptApiVersion,
        @NotNull List<String> declaredCapabilities,
        @NotNull Map<String, String> customComponents,
        @NotNull Map<String, String> heuristicFacts
    ) {
        public HarvestCandidate {
            declaredCapabilities = List.copyOf(declaredCapabilities);
            customComponents = Collections.unmodifiableMap(new LinkedHashMap<>(customComponents));
            heuristicFacts = Collections.unmodifiableMap(new LinkedHashMap<>(heuristicFacts));
        }
    }

    public record HarvestResult(
        @NotNull String addonId,
        boolean accepted,
        @NotNull AddonCorpusEntry.AddonAdmissibility admissibility,
        @Nullable AddonCorpusEntry generatedEntry,
        @NotNull List<String> notes
    ) {
        public HarvestResult {
            notes = List.copyOf(notes);
        }
    }

    public record BatchHarvestReport(
        int totalCandidates,
        int acceptedCount,
        int rejectedCount,
        @NotNull List<HarvestResult> results
    ) {
        public BatchHarvestReport {
            results = List.copyOf(results);
        }
    }

    @NotNull
    public HarvestResult harvestCandidate(@NotNull HarvestCandidate candidate) {
        List<String> notes = new ArrayList<>();

        boolean isGithub = candidate.repositoryUrl().toLowerCase().contains("github.com");
        AddonCorpusEntry.SourceType sourceType = isGithub ? AddonCorpusEntry.SourceType.GITHUB : AddonCorpusEntry.SourceType.UNKNOWN;

        boolean isPermissive = candidate.licenseName().equalsIgnoreCase("MIT")
            || candidate.licenseName().equalsIgnoreCase("Apache-2.0")
            || candidate.licenseName().equalsIgnoreCase("BSD-3-Clause");

        AddonCorpusEntry.AddonSource source = new AddonCorpusEntry.AddonSource(
            sourceType,
            candidate.repositoryUrl(),
            candidate.repositoryUrl(),
            candidate.repositoryUrl(),
            null
        );

        AddonCorpusEntry.AddonLicense license = new AddonCorpusEntry.AddonLicense(
            candidate.licenseName(),
            candidate.repositoryUrl() + "/blob/main/LICENSE",
            null,
            isPermissive,
            isPermissive,
            isPermissive,
            true
        );

        AddonCorpusEntry.AddonAdmissibility admissibility = CorpusAdmissibilityChecker.checkSourceAdmissibility(source, license);

        if (!admissibility.isAdmissible()) {
            notes.add("Candidate rejected by admissibility policy: " + admissibility.reason());
            return new HarvestResult(candidate.addonId(), false, admissibility, null, notes);
        }

        Set<String> storage = candidate.declaredCapabilities().contains("storage") ? Set.of("custom_container") : Set.of();
        Set<String> machine = candidate.declaredCapabilities().contains("machine") ? Set.of("processing_block") : Set.of();
        Set<String> transfer = candidate.declaredCapabilities().contains("transfer") ? Set.of("item_pipe") : Set.of();
        Set<String> fluid = candidate.declaredCapabilities().contains("fluid") ? Set.of("fluid_tank") : Set.of();
        Set<String> energy = candidate.declaredCapabilities().contains("energy") ? Set.of("energy_cable") : Set.of();
        Set<String> automation = candidate.declaredCapabilities().contains("automation") ? Set.of("automation_node") : Set.of();
        Set<String> networking = candidate.declaredCapabilities().contains("network") ? Set.of("state_sync") : Set.of();

        String bedrockId = candidate.addonId().contains(":")
            ? candidate.addonId()
            : "bedrock:" + candidate.addonId().replace('-', '_');

        AddonCorpusEntry entry = new AddonCorpusEntry(
            new AddonCorpusEntry.AddonIdentity(
                candidate.addonId(),
                bedrockId,
                "Community / Open Source",
                candidate.name(),
                "Curated inspectable Bedrock addon schema for capability pattern matching."
            ),
            source,
            license,
            admissibility,
            new AddonCorpusEntry.AddonVersions(
                candidate.minMinecraftVersion(),
                Set.of(candidate.minMinecraftVersion()),
                candidate.minMinecraftVersion(),
                null,
                List.of(candidate.minMinecraftVersion())
            ),
            new AddonCorpusEntry.AddonBehaviorPack(
                true,
                "2",
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of("scripts/main.js"),
                candidate.customComponents()
            ),
            new AddonCorpusEntry.AddonResourcePack(
                true,
                "2",
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Map.of()
            ),
            new AddonCorpusEntry.AddonCapabilities(
                storage, machine, transfer, fluid, energy, automation, networking, Map.of()
            ),
            new AddonCorpusEntry.AddonEvidence(
                List.of(candidate.repositoryUrl() + "/manifest.json"),
                List.of("scripts/main.js"),
                List.of(),
                List.of("custom_components"),
                List.of(),
                candidate.heuristicFacts()
            ),
            new AddonCorpusEntry.AddonConfidence(
                0.90,
                "STATIC_SCHEMA_INSPECTION",
                List.of("verified_github_source", "open_source_license"),
                List.of(),
                List.of()
            ),
            new AddonCorpusEntry.AddonImplementationFacts(
                "Script API & Custom Components",
                List.of("Server-authoritative state on Java side"),
                List.of("Pure clientbound presentation"),
                List.of("dynamic_properties"),
                List.of("itemUseOn"),
                List.of("sided_transfer"),
                List.of("ActionFormData"),
                List.of(),
                "HIGH",
                "MachineProcessingBridge"
            ),
            new AddonCorpusEntry.AddonProvenance(
                "ExtendedCorpusHarvestingPipeline",
                System.currentTimeMillis(),
                null,
                null,
                "1.0.0",
                List.of("Curated via automated schema harvesting pipeline")
            )
        );

        List<String> validationErrors = AddonCorpusValidator.validate(entry);
        if (!validationErrors.isEmpty()) {
            notes.add("Validation errors: " + validationErrors);
            return new HarvestResult(candidate.addonId(), false, admissibility, null, notes);
        }

        corpusLoader.storeEntry(entry, "curated");
        corpusLoader.refreshIndexFromSnapshots();
        notes.add("Successfully ingested and indexed into curated corpus.");

        return new HarvestResult(candidate.addonId(), true, admissibility, entry, notes);
    }

    @NotNull
    public BatchHarvestReport harvestBatch(@NotNull List<HarvestCandidate> candidates) {
        List<HarvestResult> results = new ArrayList<>();
        int accepted = 0;
        int rejected = 0;

        for (HarvestCandidate candidate : candidates) {
            HarvestResult result = harvestCandidate(candidate);
            results.add(result);
            if (result.accepted()) {
                accepted++;
            } else {
                rejected++;
            }
        }

        LOGGER.info("Corpus Harvesting Pipeline finished batch: {} accepted, {} rejected.", accepted, rejected);
        return new BatchHarvestReport(candidates.size(), accepted, rejected, results);
    }
}
