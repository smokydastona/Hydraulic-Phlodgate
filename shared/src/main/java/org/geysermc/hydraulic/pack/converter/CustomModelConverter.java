package org.geysermc.hydraulic.pack.converter;

import org.geysermc.hydraulic.pack.ModResourceIndex;
import org.geysermc.pack.converter.pipeline.AssetExtractor;
import org.geysermc.pack.converter.pipeline.ExtractionContext;
import org.geysermc.hydraulic.pack.TextureDependencyGraph;
import org.geysermc.pack.converter.type.model.ModelStitcher;
import team.unnamed.creative.ResourcePack;
import team.unnamed.creative.model.Model;

import java.util.Collection;
import java.util.Objects;

public class CustomModelConverter implements AssetExtractor<Model> {
    private final ModResourceIndex resourceIndex;
    private final ModelStitcher.Provider modelProvider;
    private final TextureDependencyGraph textureDependencies;

    public CustomModelConverter(ModResourceIndex resourceIndex, ModelStitcher.Provider modelProvider, TextureDependencyGraph textureDependencies) {
        this.resourceIndex = resourceIndex;
        this.modelProvider = modelProvider;
        this.textureDependencies = textureDependencies;
    }

    @Override
    public Collection<Model> extract(ResourcePack pack, ExtractionContext context) {
        return this.resourceIndex.modelPaths().keySet().stream()
            .map(this.modelProvider::model)
            .filter(Objects::nonNull)
            .map(model -> this.recordAndStitch(model, context))
            .toList();
    }

    private Model recordAndStitch(Model model, ExtractionContext context) {
        this.textureDependencies.recordModel(model);
        Model stitched = new ModelStitcher(this.modelProvider, model, context.logListener()).stitch();
        this.textureDependencies.recordModel(stitched);
        return stitched;
    }
}
