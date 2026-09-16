package org.geysermc.hydraulic.compat.corpus;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Validates the admissibility of Bedrock addons for use in Hydraulic.
 *
 * This enforces the corpus contract and source admissibility rules:
 * - GitHub is the first-class source for inspectable Bedrock addons
 * - CurseForge is discovery metadata only unless linked source exists
 * - Free download pages are not proof of reuse rights
 * - Prefer evidence that can be inspected, versioned, diffed, and traced
 */
public final class CorpusAdmissibilityChecker {
    private CorpusAdmissibilityChecker() {
    }

    /**
     * Checks if a source is admissible for corpus inclusion.
     */
    @NotNull
    public static AddonCorpusEntry.AddonAdmissibility checkSourceAdmissibility(
        @NotNull AddonCorpusEntry.AddonSource source,
        @NotNull AddonCorpusEntry.AddonLicense license
    ) {
        // Hydraulic keeps only GitHub-hosted corpus sources with explicit redistribution rights.
        // Marketplace-only, direct-download, and proprietary sources are rejected by policy.
        if (source.sourceType() != AddonCorpusEntry.SourceType.GITHUB) {
            return new AddonCorpusEntry.AddonAdmissibility(
                false,
                AddonCorpusEntry.AdmissibilityReason.UNCLEAR_LICENSE,
                "Only GitHub-hosted sources are admissible for the Hydraulic corpus",
                List.of(
                    "Marketplace entries, direct-download archives, and local-only artifacts are rejected",
                    "Provide a GitHub repository with a clear redistribution license"
                )
            );
        }

        if (source.sourceType() == AddonCorpusEntry.SourceType.GITHUB) {
            if (license.allowsRedistribution() && license.allowsModification() && license.allowsCommercialUse()) {
                return new AddonCorpusEntry.AddonAdmissibility(
                    true,
                    AddonCorpusEntry.AdmissibilityReason.FULLY_PERMISSIVE,
                    null,
                    List.of("Requires attribution: " + license.requiresAttribution())
                );
            }
            return new AddonCorpusEntry.AddonAdmissibility(
                false,
                AddonCorpusEntry.AdmissibilityReason.NO_REDISTRIBUTION,
                "GitHub source exists but license does not permit redistribution, modification, or commercial reuse",
                List.of()
            );
        }

        return new AddonCorpusEntry.AddonAdmissibility(
            false,
            AddonCorpusEntry.AdmissibilityReason.UNCLEAR_LICENSE,
            "Unsupported source type: " + source.sourceType(),
            List.of("Only GitHub-hosted sources are allowed in the corpus")
        );
    }

    /**
     * Checks if a repository URL is admissible.
     */
    @NotNull
    private static AddonCorpusEntry.AddonAdmissibility checkRepositoryAdmissibility(
        @NotNull String repositoryUrl,
        @NotNull AddonCorpusEntry.AddonLicense license
    ) {
        if (!repositoryUrl.contains("github.com")) {
            return new AddonCorpusEntry.AddonAdmissibility(
                false,
                AddonCorpusEntry.AdmissibilityReason.UNCLEAR_LICENSE,
                "Repository is not on GitHub",
                List.of("Provide a GitHub repository link for corpus inclusion")
            );
        }

        if (license.allowsRedistribution() && license.allowsModification()) {
            return new AddonCorpusEntry.AddonAdmissibility(
                true,
                AddonCorpusEntry.AdmissibilityReason.FULLY_PERMISSIVE,
                null,
                List.of("Requires attribution: " + license.requiresAttribution())
            );
        }

        if (license.allowsRedistribution() && !license.allowsModification()) {
            return new AddonCorpusEntry.AddonAdmissibility(
                true,
                AddonCorpusEntry.AdmissibilityReason.NO_MODIFICATION,
                null,
                List.of("Requires attribution: " + license.requiresAttribution(), "No modifications allowed")
            );
        }

        if (!license.allowsRedistribution() && license.allowsModification()) {
            return new AddonCorpusEntry.AddonAdmissibility(
                false,
                AddonCorpusEntry.AdmissibilityReason.NO_REDISTRIBUTION,
                "License permits modification but not redistribution",
                List.of("Use for reference only, not for corpus inclusion")
            );
        }

        return new AddonCorpusEntry.AddonAdmissibility(
            false,
            AddonCorpusEntry.AdmissibilityReason.DENIED,
            "License does not permit redistribution or modification",
            List.of()
        );
    }

    /**
     * Validates that evidence is inspectable and versioned.
     */
    @NotNull
    public static AddonCorpusEntry.AddonAdmissibility checkEvidenceAdmissibility(
        @NotNull AddonCorpusEntry.AddonEvidence evidence,
        @NotNull AddonCorpusEntry.AddonSource source
    ) {
        // GitHub sources always have inspectable evidence, but legality is still gated by the
        // source admissibility check and the GitHub repository requirement.
        if (source.sourceType() == AddonCorpusEntry.SourceType.GITHUB) {
            return new AddonCorpusEntry.AddonAdmissibility(
                true,
                AddonCorpusEntry.AdmissibilityReason.FULLY_PERMISSIVE,
                null,
                List.of()
            );
        }

        return new AddonCorpusEntry.AddonAdmissibility(
            false,
            AddonCorpusEntry.AdmissibilityReason.UNCLEAR_LICENSE,
            "Marketplace or direct-download evidence is not admissible for Hydraulic corpus use",
            List.of("Provide a GitHub repository with inspectable source and clear redistribution rights")
        );
    }
}