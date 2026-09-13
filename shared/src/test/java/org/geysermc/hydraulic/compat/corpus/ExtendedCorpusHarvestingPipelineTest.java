package org.geysermc.hydraulic.compat.corpus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExtendedCorpusHarvestingPipelineTest {

    @Test
    @DisplayName("Harvest inspectable open source Bedrock addon candidate into corpus")
    void harvestAdmissibleCandidate(@TempDir Path tempDir) {
        AddonCorpusLoader loader = new AddonCorpusLoader(LoggerFactory.getLogger("TestCorpusLoader"), tempDir);
        ExtendedCorpusHarvestingPipeline pipeline = new ExtendedCorpusHarvestingPipeline(loader);

        ExtendedCorpusHarvestingPipeline.HarvestCandidate candidate =
            new ExtendedCorpusHarvestingPipeline.HarvestCandidate(
                "open-machinery-bedrock",
                "Open Machinery Bedrock",
                "https://github.com/example/open-machinery",
                "MIT",
                "1.21.0",
                "1.14.0",
                List.of("machine", "energy", "transfer"),
                Map.of("machinery:press", "CustomComponent"),
                Map.of("has_energy", "true", "has_processing", "true")
            );

        ExtendedCorpusHarvestingPipeline.HarvestResult result = pipeline.harvestCandidate(candidate);

        assertTrue(result.accepted());
        assertTrue(result.admissibility().isAdmissible());
        assertNotNull(result.generatedEntry());
        assertEquals("open-machinery-bedrock", result.generatedEntry().identity().corpusId());
        assertEquals("MIT", result.generatedEntry().license().licenseType());

        // Confirm indexed in loader
        AddonCorpusIndex index = loader.loadIndex();
        assertNotNull(index);
        assertTrue(index.entries().values().stream().anyMatch(e -> e.corpusId().equals("open-machinery-bedrock")));
    }

    @Test
    @DisplayName("Reject candidate with inadmissible proprietary license")
    void rejectInadmissibleCandidate(@TempDir Path tempDir) {
        AddonCorpusLoader loader = new AddonCorpusLoader(LoggerFactory.getLogger("TestCorpusLoader"), tempDir);
        ExtendedCorpusHarvestingPipeline pipeline = new ExtendedCorpusHarvestingPipeline(loader);

        ExtendedCorpusHarvestingPipeline.HarvestCandidate candidate =
            new ExtendedCorpusHarvestingPipeline.HarvestCandidate(
                "closed-mod",
                "Closed Mod",
                "https://example.com/closed-mod",
                "All Rights Reserved",
                "1.21.0",
                "1.14.0",
                List.of(),
                Map.of(),
                Map.of()
            );

        ExtendedCorpusHarvestingPipeline.HarvestResult result = pipeline.harvestCandidate(candidate);

        assertFalse(result.accepted());
        assertFalse(result.admissibility().isAdmissible());
        assertNull(result.generatedEntry());
    }
}
