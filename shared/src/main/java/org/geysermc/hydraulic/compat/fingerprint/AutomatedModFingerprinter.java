package org.geysermc.hydraulic.compat.fingerprint;

import net.minecraft.resources.Identifier;
import org.geysermc.hydraulic.compat.ir.CompiledCompatibilityPlan;
import org.geysermc.hydraulic.compat.model.SupportLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Automated Mod Fingerprinting and Plan Optimization Engine (Phase 9).
 * Classifies mod capability archetypes (Industrial, Magic, Storage, Logistics, Decoration),
 * automatically ranks adapter opportunities, and optimizes compiled compatibility plans.
 */
public final class AutomatedModFingerprinter {

    public enum ModArchetype {
        TECH_INDUSTRIAL,
        MAGIC_SPELLCASTING,
        STORAGE_MANAGEMENT,
        LOGISTICS_PIPES,
        AGRICULTURE_FOOD,
        DECORATION_BUILDING,
        UTILITY_GENERIC
    }

    public record ModFingerprintReport(
        @NotNull String modId,
        @NotNull ModArchetype primaryArchetype,
        int totalObjects,
        int fullySupportedObjects,
        int partialObjects,
        int visualOnlyObjects,
        int unsupportedObjects,
        int averageScore,
        @NotNull List<String> detectedFeatures
    ) {
        public ModFingerprintReport {
            detectedFeatures = List.copyOf(detectedFeatures);
        }
    }

    public record RankedAdapterCandidate(
        @NotNull String objectId,
        @NotNull String modId,
        @NotNull String suggestedBridge,
        int estimatedImpactScore,
        @NotNull String reason
    ) {}

    public static final class PlanOptimizer {
        private PlanOptimizer() {
        }

        @NotNull
        public static ModFingerprintReport analyzeMod(@NotNull String modId, @NotNull List<CompiledCompatibilityPlan> plans) {
            int total = plans.size();
            int full = 0;
            int partial = 0;
            int visual = 0;
            int unsupported = 0;
            int sumScore = 0;

            boolean hasTech = false;
            boolean hasStorage = false;
            boolean hasMagic = false;
            boolean hasFood = false;

            for (CompiledCompatibilityPlan plan : plans) {
                sumScore += plan.overallScore();
                if (plan.overallLevel() == SupportLevel.AUTOMATIC || plan.overallLevel() == SupportLevel.NATIVE) {
                    full++;
                } else if (plan.overallLevel() == SupportLevel.ADAPTED || plan.overallLevel() == SupportLevel.APPROXIMATED) {
                    partial++;
                } else if (plan.overallLevel() == SupportLevel.VISUAL_ONLY) {
                    visual++;
                } else {
                    unsupported++;
                }

                if (plan.inventoryFacts().containsKey("has_processing") || plan.inventoryFacts().containsKey("can_receive_energy")) {
                    hasTech = true;
                }
                if (plan.inventoryFacts().containsKey("container.slot.storage")) {
                    hasStorage = true;
                }
                if (plan.javaIdentifier().contains("botania") || plan.javaIdentifier().contains("mana") || plan.javaIdentifier().contains("spell")) {
                    hasMagic = true;
                }
                if (plan.javaIdentifier().contains("food") || plan.javaIdentifier().contains("cook") || plan.javaIdentifier().contains("delight")) {
                    hasFood = true;
                }
            }

            ModArchetype archetype = ModArchetype.UTILITY_GENERIC;
            if (hasTech) archetype = ModArchetype.TECH_INDUSTRIAL;
            else if (hasStorage) archetype = ModArchetype.STORAGE_MANAGEMENT;
            else if (hasMagic) archetype = ModArchetype.MAGIC_SPELLCASTING;
            else if (hasFood) archetype = ModArchetype.AGRICULTURE_FOOD;

            List<String> features = new ArrayList<>();
            if (hasTech) features.add("Tech / Machine Processing");
            if (hasStorage) features.add("Storage / Inventory Systems");
            if (hasMagic) features.add("Magic / Special Mechanics");
            if (hasFood) features.add("Cooking / Agriculture");

            int avg = total > 0 ? sumScore / total : 0;
            return new ModFingerprintReport(
                modId,
                archetype,
                total,
                full,
                partial,
                visual,
                unsupported,
                avg,
                features
            );
        }

        @NotNull
        public static List<RankedAdapterCandidate> rankAdapterOpportunities(@NotNull List<CompiledCompatibilityPlan> plans) {
            List<RankedAdapterCandidate> candidates = new ArrayList<>();
            for (CompiledCompatibilityPlan plan : plans) {
                if (plan.overallLevel() == SupportLevel.VISUAL_ONLY || plan.overallLevel() == SupportLevel.UNSUPPORTED) {
                    if ("true".equals(plan.inventoryFacts().get("has_processing"))) {
                        candidates.add(new RankedAdapterCandidate(
                            plan.javaIdentifier(),
                            plan.modId(),
                            "MachineProcessingBridge",
                            85,
                            "Object declares processing recipes but lacks executable bridge binding"
                        ));
                    } else if (plan.hasMenuFallback()) {
                        candidates.add(new RankedAdapterCandidate(
                            plan.javaIdentifier(),
                            plan.modId(),
                            "MenuPatchTranslator",
                            70,
                            "Object has fallback menu layout but needs container synchronization"
                        ));
                    }
                }
            }

            candidates.sort(Comparator.comparingInt(RankedAdapterCandidate::estimatedImpactScore).reversed());
            return candidates;
        }
    }
}
