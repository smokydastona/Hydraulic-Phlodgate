package org.geysermc.hydraulic.block;

import com.google.auto.service.AutoService;
import net.kyori.adventure.key.Key;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TintedParticleLeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.geysermc.geyser.api.block.custom.CustomBlockData;
import org.geysermc.geyser.api.block.custom.CustomBlockPermutation;
import org.geysermc.geyser.api.block.custom.CustomBlockState;
import org.geysermc.geyser.api.block.custom.NonVanillaCustomBlockData;
import org.geysermc.geyser.api.block.custom.component.BoxComponent;
import org.geysermc.geyser.api.block.custom.component.CustomBlockComponents;
import org.geysermc.geyser.api.block.custom.component.GeometryComponent;
import org.geysermc.geyser.api.block.custom.component.MaterialInstance;
import org.geysermc.geyser.api.block.custom.component.TransformationComponent;
import org.geysermc.geyser.api.block.custom.nonvanilla.JavaBlockState;
import org.geysermc.geyser.api.block.custom.nonvanilla.JavaBoundingBox;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomBlocksEvent;
import org.geysermc.geyser.level.physics.PistonBehavior;
import org.geysermc.geyser.util.MathUtils;
import org.geysermc.hydraulic.HydraulicImpl;
import org.geysermc.hydraulic.compat.CompatibilityRegistry;
import org.geysermc.hydraulic.compat.MappingResolver;
import org.geysermc.hydraulic.compat.ir.CompiledCompatibilityPlan;
import org.geysermc.hydraulic.item.CreativeMappings;
import org.geysermc.hydraulic.metadata.BlockMapping;
import org.geysermc.hydraulic.metadata.BlockStateRule;
import org.geysermc.hydraulic.pack.ModResourceIndex;
import org.geysermc.hydraulic.pack.PackLogListener;
import org.geysermc.hydraulic.pack.PackModule;
import org.geysermc.hydraulic.pack.TextureAnimationMetadataReader;
import org.geysermc.hydraulic.pack.TexturePackModule;
import org.geysermc.hydraulic.pack.context.PackContext;
import org.geysermc.hydraulic.pack.context.PackEventContext;
import org.geysermc.hydraulic.pack.context.PackPostProcessContext;
import org.geysermc.hydraulic.pack.context.PackPreProcessContext;
import org.geysermc.hydraulic.util.PackUtil;
import org.geysermc.hydraulic.util.SingletonBlockGetter;
import org.geysermc.pack.bedrock.resource.BedrockResourcePack;
import org.geysermc.pack.converter.type.model.ModelStitcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import team.unnamed.creative.blockstate.Condition;
import team.unnamed.creative.blockstate.MultiVariant;
import team.unnamed.creative.blockstate.Selector;
import team.unnamed.creative.blockstate.Variant;
import team.unnamed.creative.metadata.pack.PackFormat;
import team.unnamed.creative.model.Model;
import team.unnamed.creative.model.ModelTexture;
import team.unnamed.creative.model.ModelTextures;
import team.unnamed.creative.serialize.minecraft.blockstate.BlockStateSerializer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

@AutoService(PackModule.class)
public class BlockPackModule extends TexturePackModule<BlockPackModule> {
    private static final String STATE_CONDITION = "query.block_property('%s') == %s";

    private final Map<String, StateDefinition> blockStates = new HashMap<>();
    private final Set<String> emptyModels = new HashSet<>();

    @SuppressWarnings("this-escape")
    public BlockPackModule() {
        this.listenOn(GeyserDefineCustomBlocksEvent.class, this::onDefineCustomBlocks);

        this.preProcess(this::preProcess);
        this.postProcess(this::postProcess);
    }

    private void preProcess(@NotNull PackPreProcessContext<BlockPackModule> context) {
        ModResourceIndex resourceIndex = context.hydraulic().getPackManager().modResourceIndex(context.mod().id());
        if (resourceIndex != null) {
            for (Block block : context.registryValues(BuiltInRegistries.BLOCK)) {
                Identifier blockLocation = BuiltInRegistries.BLOCK.getKey(block);
                loadBlockStateDefinition(context, resourceIndex, blockLocation);
            }
        }

        // Check for empty models
        List<Block> blocks = context.registryValues(BuiltInRegistries.BLOCK);
        DefaultedRegistry<Block> registry = BuiltInRegistries.BLOCK;
        for (Block block : blocks) {
            Identifier blockLocation = registry.getKey(block);
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                ModelDefinition definition = getModel(context, blockLocation, state);
                if (definition == null) {
                    continue;
                }

                Model model = definition.model();
                Key key = model.key();

                // Skip unit cube models
                if (isUnitCube(model.parent())) {
                    continue;
                }

                // Check if the model is empty
                Model stitchedModel = new ModelStitcher(context.modelProvider(), model, new PackLogListener(context.logger())).stitch();
                if (!stitchedModel.elements().isEmpty()) {
                    continue;
                }

                emptyModels.add(key.toString());
            }
        }
    }

    private void loadBlockStateDefinition(
        @NotNull PackPreProcessContext<BlockPackModule> context,
        @NotNull ModResourceIndex resourceIndex,
        @NotNull Identifier blockLocation
    ) {
        if (this.blockStates.containsKey(blockLocation.toString())) {
            return;
        }

        com.google.gson.JsonObject jsonObject = context.hydraulic().getPackManager().lazyBlockstateProvider().getBlockstate(blockLocation);
        if (jsonObject == null) {
            return;
        }

        try {
            team.unnamed.creative.blockstate.BlockState blockState = BlockStateSerializer.INSTANCE.deserializeFromJson(
                jsonObject,
                Key.key(blockLocation.getNamespace(), blockLocation.getPath()),
                PackFormat.UNKNOWN
            );
            if (blockState != null) {
                this.blockStates.put(blockLocation.toString(), new StateDefinition(blockState, context.modelProvider()));
            }
        } catch (Exception e) {
            context.logger().warn("Failed to deserialize indexed blockstate {}", blockLocation, e);
        }
    }

    private void postProcess(@NotNull PackPostProcessContext<BlockPackModule> context) {
        BedrockResourcePack bedrockPack = context.bedrockResourcePack();
        ModResourceIndex resourceIndex = context.hydraulic().getPackManager().modResourceIndex(context.mod().id());

        if (resourceIndex == null) {
            return;
        }

        Set<Key> selectedTextures = context.hydraulic().getPackManager().selectedTextures(context.mod().id());
        Iterable<Map.Entry<Key, Path>> textureEntries = selectedTextures == null
            ? resourceIndex.texturePaths().entrySet()
            : selectedTextureEntries(resourceIndex, selectedTextures);

        for (Map.Entry<Key, Path> textureEntry : textureEntries) {
            Key key = textureEntry.getKey();
            String value = key.value();

            if (value.startsWith("block/")) {
                String cleanPath = value.replace("block/", "").replace(".png", "");

                String outputLoc = getOutputFromBlockTexture(context, key).replace(".png", "");
                String id = key.namespace() + ":" + cleanPath;
                bedrockPack.addBlockTexture(id, outputLoc);

                Integer frameTime = TextureAnimationMetadataReader.frameTime(textureEntry.getValue());
                if (frameTime != null) {
                    bedrockPack.addFlipbookTexture(id, outputLoc, frameTime);
                }
            }
        }

        // Ensure all registered blocks for this mod have a mapping in terrain_texture so no missing/dirt texture occurs
        for (Block block : context.registryValues(BuiltInRegistries.BLOCK)) {
            Identifier blockLocation = BuiltInRegistries.BLOCK.getKey(block);
            String blockId = blockLocation.toString();
            String cleanPath = blockLocation.getPath();

            Path matchingTexture = resourceIndex.resolveTexturePath(Key.key(blockLocation.getNamespace(), "block/" + cleanPath));
            if (matchingTexture == null) {
                matchingTexture = resourceIndex.resolveTexturePath(Key.key(blockLocation.getNamespace(), cleanPath));
            }
            if (matchingTexture != null) {
                String outputLoc = getOutputFromBlockTexture(context, Key.key(blockLocation.getNamespace(), "block/" + cleanPath)).replace(".png", "");
                bedrockPack.addBlockTexture(blockId, outputLoc);
                bedrockPack.addBlockTexture(cleanPath, outputLoc);
            } else {
                String fallbackTexturePath = resolveFallbackTerrainTexturePath(cleanPath);
                bedrockPack.addBlockTexture(blockId, fallbackTexturePath);
                bedrockPack.addBlockTexture(cleanPath, fallbackTexturePath);
            }
        }
    }

    private static String resolveFallbackTerrainTexturePath(String blockPath) {
        String lower = blockPath.toLowerCase();
        if (lower.contains("chest")) {
            return "textures/blocks/chest_top";
        } else if (lower.contains("barrel")) {
            return "textures/blocks/barrel_top";
        } else if (lower.contains("shulker")) {
            return "textures/blocks/shulker_top_purple";
        } else if (lower.contains("pot")) {
            return "textures/blocks/terracotta";
        } else if (lower.contains("wood") || lower.contains("plank") || lower.contains("log")) {
            return "textures/blocks/oak_planks";
        } else if (lower.contains("metal") || lower.contains("iron") || lower.contains("copper")) {
            return "textures/blocks/iron_block";
        } else if (lower.contains("sand") || lower.contains("gravel")) {
            return "textures/blocks/sand";
        }
        return "textures/blocks/stone";
    }

    @NotNull
    private static List<Map.Entry<Key, Path>> selectedTextureEntries(@NotNull ModResourceIndex resourceIndex, @NotNull Set<Key> selectedTextures) {
        List<Map.Entry<Key, Path>> entries = new ArrayList<>(selectedTextures.size());
        for (Key selectedTexture : selectedTextures) {
            Path path = resourceIndex.resolveTexturePath(selectedTexture);
            if (path != null) {
                entries.add(Map.entry(selectedTexture, path));
            }
        }
        return List.copyOf(entries);
    }

    @Override
    public boolean test(@NotNull PackPostProcessContext<BlockPackModule> context) {
        return !context.registryValues(BuiltInRegistries.BLOCK).isEmpty();
    }

    @SuppressWarnings("deprecation")
    private void onDefineCustomBlocks(PackEventContext<GeyserDefineCustomBlocksEvent, BlockPackModule> context) {
        GeyserDefineCustomBlocksEvent event = context.event();
        List<Block> blocks = context.registryValues(BuiltInRegistries.BLOCK);
        Materials materials = context.storage().materials();
        boolean storageDirty = false;

        DefaultedRegistry<Block> registry = BuiltInRegistries.BLOCK;
        for (Block block : blocks) {
            Identifier blockLocation = registry.getKey(block);
            CompiledCompatibilityPlan blockPlan = compatibilityBlockPlan(context, blockLocation);
            MappingResolver mappingResolver = context.hydraulic().getPackManager().mappingResolver();
            BlockMapping blockMapping = mappingResolver.blockMapping(blockLocation);
            Map<String, StatePropertyDefinition> stateDefinitions = stateDefinitions(context, blockLocation, block.getStateDefinition().getProperties(), blockMapping);
            List<MappingResolver.ResolvedBlockDefinition> resolvedDefinitions = context.hydraulic()
                .getPackManager()
                .compatibilityRegistry()
                .dispatchTable()
                .blockDefinitions(blockLocation);
            if (resolvedDefinitions.isEmpty()) {
                resolvedDefinitions = mappingResolver.resolveBlockDefinitions(blockLocation, block.getStateDefinition().getPossibleStates());
            }
            if (resolvedDefinitions.size() > 1) {
                context.logger().info("Resolved {} state-aware compatibility variants for {}", resolvedDefinitions.size(), blockLocation);
            }

            int blockId = registry.getId(block);
            for (MappingResolver.ResolvedBlockDefinition resolvedDefinition : resolvedDefinitions) {
                Identifier customBlockIdentifier = resolvedDefinition.identifier();
                CustomBlockData.Builder builder = NonVanillaCustomBlockData.builder()
                        .name(customBlockIdentifier.getPath())
                        .namespace(customBlockIdentifier.getNamespace())
                    .includedInCreativeInventory(blockPlan == null || blockPlan.allowsCreativeExposure());

                String creativeSuppressionReason = blockPlan != null ? blockPlan.creativeExposureReason() : null;
                if (creativeSuppressionReason != null) {
                    context.logger().info(
                        "Registering block {} as runtime-only for Bedrock because {} (runtime bridges: {})",
                        blockLocation,
                        creativeSuppressionReason,
                        blockPlan.runtimeBridgeRequirementIds().isEmpty() ? "none" : String.join(", ", blockPlan.runtimeBridgeRequirementIds())
                    );
                }

                CreativeMappings.setupBlock(block, builder);

                for (StatePropertyDefinition definition : stateDefinitions.values()) {
                    addStateProperty(builder, definition);
                }

                List<CustomBlockPermutation> permutations = new ArrayList<>();
                CustomBlockComponents.Builder baseComponentBuilder = CustomBlockComponents.builder();
                for (BlockState state : resolvedDefinition.states()) {
                ModelDefinition definition = getModel(context, blockLocation, state);
                if (definition == null) {
                    continue;
                }

                Model model = definition.model();
                Key key = model.key();
                MappingResolver.ResolvedBlockState resolvedState = context.hydraulic()
                    .getPackManager()
                    .compatibilityRegistry()
                    .dispatchTable()
                    .blockState(blockLocation, state);
                if (resolvedState == null) {
                    resolvedState = mappingResolver.resolveBlockState(blockLocation, state);
                }
                BlockMapping.RuntimeMetadata resolvedMetadata = resolvedState.metadata();
                Map<String, String> stateValues = customStateValues(context, blockLocation, stateDefinitions, state, resolvedMetadata);

                CustomBlockComponents.Builder componentsBuilder = CustomBlockComponents.builder()
                        .transformation(new TransformationComponent(
                            (360 - definition.variant().x()) % 360, // Rotation X
                            (360 - definition.variant().y()) % 360, // Rotation Y
                            0, // Rotation Z
                            1, // Scale X
                            1, // Scale Y
                            1, // Scale Z
                            0, // Translation X
                            0, // Translation Y
                            0 // Translation Z
                        ));

                if (!isUnitCube(model.parent())) {
                    String namespace = key.namespace();
                    String value = key.value();

                    String geoKey = value.substring(value.lastIndexOf('/') + 1);
                    String geoName = "geometry." + (namespace.equals(Key.MINECRAFT_NAMESPACE) ? "" : namespace + ".") + geoKey;

                    if (emptyModels.contains(key.toString())) {
                        context.logger().info("Using fallback full block geometry for block {} with dynamic/empty model", blockLocation);
                        geoName = "minecraft:geometry.full_block";
                    }

                    if (resolvedMetadata.geometryId() != null) {
                        geoName = resolvedMetadata.geometryId();
                    }

                    componentsBuilder.geometry(GeometryComponent.builder()
                            .identifier(geoName)
                            .build());

                    // TODO: This is not fully correct. On Bedrock, the shape rotates with
                    //       the block, so the collision box will need to be rotated back here
                    VoxelShape shape = state.getShape(new SingletonBlockGetter(state), BlockPos.ZERO);
                    VoxelShape collisionShape = state.getCollisionShape(new SingletonBlockGetter(state), BlockPos.ZERO);

                    componentsBuilder.selectionBox(createBoxComponent(shape));
                    componentsBuilder.collisionBox(createBoxComponent(collisionShape));
                } else {
                    componentsBuilder.geometry(GeometryComponent.builder()
                            .identifier("minecraft:geometry.full_block")
                            .build());
                }

                // TODO: Work this out based on block state/texture? as this isn't perfect
                // https://wiki.bedrock.dev/blocks/block-components.html#render-methods
                String renderMethod = state.canOcclude() ? "opaque" : "blend";

                // If the model is a cross block (EG a flower), we need to use alpha_test_single_sided
                if (model.parent() != null && model.parent().value().equals("block/cross")) {
                    renderMethod = "alpha_test_single_sided";
                }

                String tintMethod = null;
                // TODO Read this from the model data
                if (block instanceof TintedParticleLeavesBlock) {
                    tintMethod = "default_foliage";
                }

                String materialKey = key.toString();
                if (resolvedMetadata.materialId() != null) {
                    materialKey = resolvedMetadata.materialId();
                }

                Materials.Material material = materials.material(materialKey);
                if (material == null) {
                    material = buildMaterial(context, materialKey, model);
                    if (material != null) {
                        materials.addMaterial(materialKey, material);
                        storageDirty = true;
                    }
                }
                if (material != null) {
                    // Add a default texture, can be replaced by the below (I think)
                    Map.Entry<String, String> firstEntry = material.textures().entrySet().iterator().next();

                    String name = PackUtil.getTextureName(firstEntry.getValue());

                    componentsBuilder.materialInstance("*", MaterialInstance.builder()
                            .texture(name)
                            .renderMethod(renderMethod)
                            .faceDimming(true)
                            .ambientOcclusion(model.ambientOcclusion())
                            .tintMethod(tintMethod)
                            .build());

                    Map<String, String> faceMapping = getFaceMapping(model.parent());
                    if (!faceMapping.isEmpty()) {
                        for (Map.Entry<String, String> face : faceMapping.entrySet()) {
                            if (!material.textures().containsKey(face.getValue())) continue;

                            String textureName = PackUtil.getTextureName(material.textures().get(face.getValue()));

                            componentsBuilder.materialInstance(face.getKey(), MaterialInstance.builder()
                                    .texture(textureName)
                                    .renderMethod(renderMethod)
                                    .faceDimming(true)
                                    .ambientOcclusion(model.ambientOcclusion())
                                    .tintMethod(tintMethod)
                                    .build());
                        }
                    } else {
                        for (Map.Entry<String, String> entry : material.textures().entrySet()) {
                            String materialInstanceKey = entry.getKey();

                            // Bedrock uses "*" for the particle texture
                            if ("particle".equals(materialInstanceKey)) {
                                materialInstanceKey = "*";
                            }

                            componentsBuilder.materialInstance(materialInstanceKey, MaterialInstance.builder()
                                    .texture(PackUtil.getTextureName(entry.getValue()))
                                    .renderMethod(renderMethod)
                                    .faceDimming(true)
                                    .ambientOcclusion(model.ambientOcclusion())
                                    .tintMethod(tintMethod)
                                    .build());
                        }
                    }
                } else {
                    String fallbackTextureName = blockLocation.toString();
                    componentsBuilder.materialInstance("*", MaterialInstance.builder()
                            .texture(fallbackTextureName)
                            .renderMethod(renderMethod)
                            .faceDimming(true)
                            .ambientOcclusion(model.ambientOcclusion())
                            .tintMethod(tintMethod)
                            .build());
                    context.logger().info("Applied mapped fallback texture {} for block {}", fallbackTextureName, blockLocation);
                }

                // No properties exist on this state, so there's only one
                // blockstate that can exist. Update the base builder so that
                // the code that creates the component for the base block
                // persists everything we did above
                if (state.getProperties().isEmpty()) {
                    baseComponentBuilder = componentsBuilder;
                    continue;
                }

                List<String> conditions = new ArrayList<>();
                for (StatePropertyDefinition property : stateDefinitions.values()) {
                    String propValue = stateValues.get(property.name());
                    if (propValue == null) {
                        continue;
                    }

                    conditions.add(String.format(STATE_CONDITION, property.name(), conditionValue(property, propValue)));
                }

                String condition = String.join(" && ", conditions);
                permutations.add(new CustomBlockPermutation(componentsBuilder.build(), condition));
                }

                builder.permutations(permutations);

                BlockState defaultState = resolvedDefinition.representativeState(block.defaultBlockState());
                VoxelShape shape = defaultState.getShape(new SingletonBlockGetter(defaultState), BlockPos.ZERO);
                VoxelShape collisionShape = defaultState.getCollisionShape(new SingletonBlockGetter(defaultState), BlockPos.ZERO);

                CustomBlockComponents.Builder componentsBuilder = baseComponentBuilder
                    .displayName("%" + block.getDescriptionId())
                    .friction(Math.min(1 - block.getFriction(), 0.9f));
                float destroyTime = block.defaultDestroyTime();
                if (destroyTime >= 0) {
                    componentsBuilder.destructibleByMining(destroyTime); // TODO: Check
                }
                componentsBuilder
                    // .unitCube(true) // TODO: Geometry conversion
                    .selectionBox(createBoxComponent(shape))
                    .collisionBox(createBoxComponent(collisionShape));

                builder.components(componentsBuilder.build());

                CustomBlockData blockData = builder.build();
                try {
                    event.register(blockData);
                } catch (IllegalArgumentException e) {
                    context.logger().error("Failed to register block {} variant {}: {}", blockLocation, customBlockIdentifier, e.getMessage());
                    continue;
                }

                for (BlockState state : resolvedDefinition.states()) {
                    MappingResolver.ResolvedBlockState resolvedState = context.hydraulic()
                        .getPackManager()
                        .compatibilityRegistry()
                        .dispatchTable()
                        .blockState(blockLocation, state);
                    if (resolvedState == null) {
                        resolvedState = mappingResolver.resolveBlockState(blockLocation, state);
                    }
                    Map<String, String> stateValues = customStateValues(context, blockLocation, stateDefinitions, state, resolvedState.metadata());
                CustomBlockState.Builder stateBuilder = blockData.blockStateBuilder();
                for (StatePropertyDefinition property : stateDefinitions.values()) {
                    String value = stateValues.get(property.name());
                    if (value == null) {
                        continue;
                    }

                    applyStateProperty(stateBuilder, property, value);
                }

                PistonBehavior pistonBehavior = switch (state.getPistonPushReaction()) {
                    case BLOCK -> PistonBehavior.BLOCK;
                    case DESTROY -> PistonBehavior.DESTROY;
                    case PUSH_ONLY -> PistonBehavior.PUSH_ONLY;
                    default -> PistonBehavior.NORMAL;
                };

                CustomBlockState customBlockState = stateBuilder.build();
                JavaBlockState.Builder javaBlockStateBuilder = JavaBlockState.builder()
                        .identifier(BlockStateParser.serialize(state))
                        .javaId(Block.getId(state))
                    .blockHardness(Math.max(block.defaultDestroyTime(), 0)) // TODO: Check
                        .canBreakWithHand(!state.requiresCorrectToolForDrops())
                        .waterlogged(state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED))
                        .stateGroupId(blockId)
                        .pistonBehavior(pistonBehavior.name());

                // TODO Work out if we need to prefix with _item so we can remove InventoryUtilsMixin
                try {
                    ItemStack pickItem = state.getCloneItemStack(HydraulicImpl.instance().server().overworld(), BlockPos.ZERO, false);
                    String itemId = BuiltInRegistries.ITEM.getKey(pickItem.getItem()).toString();

                    // If the method is annotated with `@Environment(EnvType.CLIENT)` then we get air back, so lets ignore that
                    if (!itemId.equals("minecraft:air")) {
                        javaBlockStateBuilder.pickItem(itemId);
                    }
                } catch (Exception e) {
                    context.logger().warn("Failed to get pick item for block {}: {}", blockLocation, e.getMessage());
                }

                    VoxelShape stateCollisionShape = state.getCollisionShape(new SingletonBlockGetter(state), BlockPos.ZERO);
                    javaBlockStateBuilder.collision(createJavaBoundingBoxes(stateCollisionShape));

                    event.registerOverride(javaBlockStateBuilder.build(), customBlockState);
                }
            }
        }

        if (storageDirty) {
            context.storage().save();
        }
    }

    @Nullable
    private Materials.Material buildMaterial(
        @NotNull PackEventContext<GeyserDefineCustomBlocksEvent, BlockPackModule> context,
        @NotNull String materialKey,
        @NotNull Model fallbackModel
    ) {
        Model sourceModel = fallbackModel;
        if (fallbackModel.key() == null || !materialKey.equals(fallbackModel.key().toString())) {
            sourceModel = context.modelProvider().model(Key.key(materialKey));
            if (sourceModel == null) {
                context.logger().warn("Missing material model {} for mod {}", materialKey, context.mod().id());
                return null;
            }
        }

        Model stitchedModel = new ModelStitcher(context.modelProvider(), sourceModel, new PackLogListener(context.logger())).stitch();
        if (stitchedModel == null) {
            context.logger().warn("Could not stitch material model {} for mod {}", materialKey, context.mod().id());
            return null;
        }

        Map<String, String> textures = new HashMap<>();
        Map<String, ModelTexture> modelTextures = getTextures(stitchedModel.textures());
        for (Map.Entry<String, ModelTexture> entry : modelTextures.entrySet()) {
            ModelTexture modelTexture = getModelTexture(modelTextures, entry.getKey());
            if (modelTexture == null || modelTexture.key() == null) {
                continue;
            }

            textures.put(entry.getKey(), modelTexture.key().toString());
        }

        if (textures.isEmpty()) {
            return null;
        }
        return new Materials.Material(textures);
    }

    @NotNull
    private Map<String, StatePropertyDefinition> stateDefinitions(
        @NotNull PackContext<?> context,
        @NotNull Identifier blockLocation,
        @NotNull Collection<Property<?>> properties,
        @Nullable BlockMapping blockMapping
    ) {
        Map<String, StatePropertyDefinition> definitions = new LinkedHashMap<>();
        for (Property<?> property : properties) {
            StatePropertyDefinition definition = StatePropertyDefinition.of(property);
            definitions.put(definition.name(), definition);
        }

        if (blockMapping == null) {
            return definitions;
        }

        for (BlockStateRule rule : blockMapping.rules()) {
            if (rule.bedrockState() == null) {
                continue;
            }

            for (Map.Entry<String, String> entry : rule.bedrockState().entrySet()) {
                StatePropertyDefinition overrideDefinition = StatePropertyDefinition.of(entry.getKey(), entry.getValue());
                StatePropertyDefinition definition = definitions.get(entry.getKey());
                if (definition == null) {
                    definitions.put(entry.getKey(), overrideDefinition);
                    continue;
                }

                if (definition.type() != overrideDefinition.type()) {
                    context.logger().warn("Ignoring metadata state override with incompatible type for {} property {}", blockLocation, entry.getKey());
                    continue;
                }

                definition.values().addAll(overrideDefinition.values());
            }
        }

        return definitions;
    }

    @NotNull
    private Map<String, String> customStateValues(
        @NotNull PackContext<?> context,
        @NotNull Identifier blockLocation,
        @NotNull Map<String, StatePropertyDefinition> stateDefinitions,
        @NotNull BlockState state,
        @NotNull BlockMapping.RuntimeMetadata resolvedMetadata
    ) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Property<?> property : state.getProperties()) {
            values.put(property.getName(), propertyValue(property, state));
        }

        if (resolvedMetadata.bedrockState().isEmpty()) {
            return values;
        }

        for (Map.Entry<String, String> entry : resolvedMetadata.bedrockState().entrySet()) {
            StatePropertyDefinition definition = stateDefinitions.get(entry.getKey());
            if (definition == null) {
                continue;
            }

            if (!definition.accepts(entry.getValue())) {
                context.logger().warn("Ignoring metadata state override for {} property {} with invalid value {}", blockLocation, entry.getKey(), entry.getValue());
                continue;
            }

            values.put(entry.getKey(), entry.getValue());
        }

        return values;
    }

    private void addStateProperty(@NotNull CustomBlockData.Builder builder, @NotNull StatePropertyDefinition property) {
        switch (property.type()) {
            case INTEGER -> builder.intProperty(property.name(), property.values().stream().map(Integer::parseInt).toList());
            case BOOLEAN -> builder.booleanProperty(property.name());
            case STRING -> builder.stringProperty(property.name(), List.copyOf(property.values()));
        }
    }

    private void applyStateProperty(@NotNull CustomBlockState.Builder builder, @NotNull StatePropertyDefinition property, @NotNull String value) {
        switch (property.type()) {
            case INTEGER -> builder.intProperty(property.name(), Integer.parseInt(value));
            case BOOLEAN -> builder.booleanProperty(property.name(), Boolean.parseBoolean(value));
            case STRING -> builder.stringProperty(property.name(), value);
        }
    }

    @NotNull
    private String propertyValue(@NotNull Property<?> property, @NotNull BlockState state) {
        if (property instanceof EnumProperty<?> enumProperty) {
            return state.getValue(enumProperty).getSerializedName();
        }
        return state.getValue(property).toString();
    }

    @NotNull
    private String conditionValue(@NotNull StatePropertyDefinition property, @NotNull String value) {
        return switch (property.type()) {
            case STRING -> "'" + value + "'";
            case BOOLEAN, INTEGER -> value;
        };
    }

    @NotNull
    private JavaBoundingBox[] createJavaBoundingBoxes(@NotNull VoxelShape shape) {
        List<AABB> aabbs = shape.toAabbs();
        JavaBoundingBox[] boxes = new JavaBoundingBox[aabbs.size()];
        for (int index = 0; index < aabbs.size(); index++) {
            AABB aabb = aabbs.get(index);
            boxes[index] = new JavaBoundingBox(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ);
        }
        return boxes;
    }

    private enum PropertyType {
        INTEGER,
        BOOLEAN,
        STRING
    }

    private static final class StatePropertyDefinition {
        private final String name;
        private final PropertyType type;
        private final LinkedHashSet<String> values;

        private StatePropertyDefinition(@NotNull String name, @NotNull PropertyType type, @NotNull LinkedHashSet<String> values) {
            this.name = name;
            this.type = type;
            this.values = values;
        }

        @NotNull
        public static StatePropertyDefinition of(@NotNull Property<?> property) {
            if (property instanceof IntegerProperty intProperty) {
                LinkedHashSet<String> values = new LinkedHashSet<>();
                for (Integer value : intProperty.getPossibleValues()) {
                    values.add(value.toString());
                }
                return new StatePropertyDefinition(property.getName(), PropertyType.INTEGER, values);
            }

            if (property instanceof BooleanProperty) {
                return new StatePropertyDefinition(property.getName(), PropertyType.BOOLEAN, new LinkedHashSet<>(List.of("false", "true")));
            }

            if (property instanceof EnumProperty<?> enumProperty) {
                LinkedHashSet<String> values = new LinkedHashSet<>();
                for (StringRepresentable value : enumProperty.getPossibleValues()) {
                    values.add(value.getSerializedName());
                }
                return new StatePropertyDefinition(property.getName(), PropertyType.STRING, values);
            }

            throw new IllegalArgumentException("Unknown property type: " + property.getClass().getName());
        }

        @NotNull
        public static StatePropertyDefinition of(@NotNull String name, @NotNull String value) {
            if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                return new StatePropertyDefinition(name, PropertyType.BOOLEAN, new LinkedHashSet<>(List.of("false", "true")));
            }

            try {
                Integer.parseInt(value);
                return new StatePropertyDefinition(name, PropertyType.INTEGER, new LinkedHashSet<>(List.of(value)));
            } catch (NumberFormatException ignored) {
                return new StatePropertyDefinition(name, PropertyType.STRING, new LinkedHashSet<>(List.of(value)));
            }
        }

        public boolean accepts(@NotNull String value) {
            return switch (this.type) {
                case INTEGER -> {
                    try {
                        Integer.parseInt(value);
                        yield true;
                    } catch (NumberFormatException ignored) {
                        yield false;
                    }
                }
                case BOOLEAN -> "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
                case STRING -> true;
            };
        }

        @NotNull
        public String name() {
            return this.name;
        }

        @NotNull
        public PropertyType type() {
            return this.type;
        }

        @NotNull
        public LinkedHashSet<String> values() {
            return this.values;
        }
    }

    @Nullable
    private ModelDefinition getModel(@NotNull PackContext<?> context, @NotNull Identifier blockLocation, @NotNull BlockState state) {
        StateDefinition definition = this.blockStates.get(blockLocation.toString());
        if (definition == null) {
            context.logger().warn("Missing blockstate for block {}", blockLocation);
            return null;
        }

        return definition.resolveModel(state, ignored -> this.resolveModelDefinition(context, blockLocation, ignored, definition));
    }

    @Nullable
    private ModelDefinition resolveModelDefinition(@NotNull PackContext<?> context, @NotNull Identifier blockLocation, @NotNull BlockState state, @NotNull StateDefinition definition) {

        team.unnamed.creative.blockstate.BlockState packState = definition.state();

        // Check if we have a variant match
        MultiVariant multiVariant = matchState(state, packState.variants());
        if (multiVariant == null || multiVariant.variants().isEmpty()) {
            // No variant, check if we have a default
            multiVariant = packState.variants().get("");
        }

        // Try and match the state
        // TODO Handle multiple variants since we only take the first match
        //      Will likely need to generate more geometry files and then alter bone visibility for each part
        if (multiVariant == null) {
            for (Selector selector : packState.multipart()) {
                // Ignore none conditions
                if (selector.condition() == Condition.NONE) {
                    continue;
                }

                List<Condition> conditions = new ArrayList<>();
                BiFunction<Boolean, Boolean, Boolean> comparator = (a, b) -> false;
                if (selector.condition() instanceof Condition.And andCondition) {
                    conditions.addAll(andCondition.conditions());
                    comparator = Boolean::logicalAnd;
                } else if (selector.condition() instanceof Condition.Or orCondition) {
                    conditions.addAll(orCondition.conditions());
                    comparator = Boolean::logicalOr;
                } else if (selector.condition() instanceof Condition.Match) {
                    conditions.add(selector.condition());
                }

                boolean first = true;
                boolean result = true;
                for (Condition condition : conditions) {
                    if (!(condition instanceof Condition.Match match)) {
                        context.logger().warn("Non match condition found in {}", blockLocation);
                        continue;
                    }

                    Property<?> foundProperty = null;
                    for (Property<?> property : state.getProperties()) {
                        if (property.getName().equals(match.key())) {
                            foundProperty = property;
                            break;
                        }
                    }

                    if (foundProperty == null) {
                        result = false;
                        continue;
                    }

                    boolean test = state.getValue(foundProperty).toString().equals(match.value().toString());
                    if (!first) {
                        result = comparator.apply(result, test);
                    } else {
                        result = test;
                        first = false;
                    }
                }

                if (result) {
                    multiVariant = selector.variant();
                    break;
                }
            }
        }

        // Get the default multipart variant if we have no match
        if (multiVariant == null) {
            Optional<Selector> selector = packState.multipart().stream().filter(multipart -> multipart.condition() == Condition.NONE).findFirst();
            if (selector.isPresent()) {
                multiVariant = selector.get().variant();
            }

            // LOGGER.warn("Missing multipart state conversion for block {} {}", blockLocation, state);
        }

        // We have a match! Now we need to find the model
        if (multiVariant != null && !multiVariant.variants().isEmpty()) {
            // TODO: Handle multiple variants?
            Variant variant = multiVariant.variants().get(0);
            Key modelKey = variant.model();

            Model model = definition.modelProvider().model(modelKey);
            if (model == null) {
                context.logger().warn("Missing model {} for block {}", modelKey, blockLocation);
            } else {
                return new ModelDefinition(model, variant);
            }
        }

        return null;
    }

    private static MultiVariant matchState(@NotNull BlockState state, @NotNull Map<String, MultiVariant> variants) {
        List<String> properties = new ArrayList<>();
        for (Property<?> property : state.getProperties()) {
            properties.add(property.getName() + "=" + state.getValue(property).toString().toLowerCase());
        }

        for (Map.Entry<String, MultiVariant> entry : variants.entrySet()) {
            String variant = entry.getKey();

            String[] property = variant.split(",");
            boolean match = true;
            for (String prop : property) {
                if (!properties.contains(prop)) {
                    match = false;
                    break;
                }
            }

            if (match) {
                return entry.getValue();
            }
        }

        return null;
    }

    @Nullable
    private static ModelTexture getModelTexture(@NotNull Map<String, ModelTexture> textures, @NotNull String key) {
        return getModelTexture(textures, key, new HashSet<>());
    }

    @Nullable
    private static ModelTexture getModelTexture(@NotNull Map<String, ModelTexture> textures, @NotNull String key, @NotNull Set<String> visited) {
        if (!visited.add(key)) {
            return null;
        }

        // Texture references the value of another texture
        ModelTexture value = textures.get(key);
        if (value != null && value.reference() != null) {
            return getModelTexture(textures, value.reference(), visited);
        }

        return value;
    }

    private static Map<String, ModelTexture> getTextures(@NotNull ModelTextures modelTextures) {
        Map<String, ModelTexture> textures = new HashMap<>(modelTextures.variables());
        textures.put("particle", modelTextures.particle());
        for (int i = 0; i < modelTextures.layers().size(); i++) {
            textures.put("layer" + i, modelTextures.layers().get(i));
        }

        return textures;
    }

    @Nullable
    private static CompiledCompatibilityPlan compatibilityBlockPlan(@NotNull PackContext<?> context, @NotNull Identifier blockLocation) {
        CompatibilityRegistry compatibilityRegistry = context.hydraulic().getPackManager().compatibilityRegistry();
        return compatibilityRegistry.dispatchTable().block(blockLocation);
    }

    private boolean isUnitCube(Key parent) {
        if (parent == null) {
            return false;
        }
        return parent.namespace().equals("minecraft") && (parent.value().startsWith("block/cube") || parent.value().startsWith("block/orientable"));
    }

    /**
     * Get the face mapping for the given parent model.
     * This is due to some cube models having texture names bedrock doesn't understand.
     *
     * @param parent The parent model
     * @return The face mapping if any
     */
    private Map<String, String> getFaceMapping(Key parent) {
        // Destination <- Source
        Map<String, String> mapping = new HashMap<>();
//        {{
//            put("*", "particle");
//            put("up", "up");
//            put("down", "down");
//            put("north", "north");
//            put("south", "south");
//            put("west", "west");
//            put("east", "east");
//        }};

        // No parent, so return empty
        if (parent == null) {
            return mapping;
        }

        if ("block/cube_all".equals(parent.value())) {
            mapping.put("*", "all");
        } else if ("block/cube_bottom_top".equals(parent.value())) {
            mapping.put("*", "side");
            mapping.put("up", "top");
            mapping.put("down", "bottom");
            mapping.put("north", "side");
            mapping.put("south", "side");
            mapping.put("west", "side");
            mapping.put("east", "side");
        } else if ("block/cube_column".equals(parent.value())) {
            mapping.put("*", "side");
            mapping.put("up", "end");
            mapping.put("down", "end");
            mapping.put("north", "side");
            mapping.put("south", "side");
            mapping.put("west", "side");
            mapping.put("east", "side");
        }

        return mapping;
    }

    private static BoxComponent createBoxComponent(VoxelShape shape) {
        if (shape.isEmpty()) {
            return BoxComponent.emptyBox();
        }

        float minX = 5;
        float minY = 5;
        float minZ = 5;
        float maxX = -5;
        float maxY = -5;
        float maxZ = -5;
        for (AABB boundingBox : shape.toAabbs()) {
            double offsetX = boundingBox.getXsize() * 0.5;
            double offsetY = boundingBox.getYsize() * 0.5;
            double offsetZ = boundingBox.getZsize() * 0.5;

            Vec3 center = boundingBox.getCenter();

            minX = Math.min(minX, (float) (center.x() - offsetX));
            minY = Math.min(minY, (float) (center.y() - offsetY));
            minZ = Math.min(minZ, (float) (center.z() - offsetZ));

            maxX = Math.max(maxX, (float) (center.x() + offsetX));
            maxY = Math.max(maxY, (float) (center.y() + offsetY));
            maxZ = Math.max(maxZ, (float) (center.z() + offsetZ));
        }
        minX = MathUtils.clamp(minX, 0, 1);
        minY = MathUtils.clamp(minY, 0, 1);
        minZ = MathUtils.clamp(minZ, 0, 1);
        maxX = MathUtils.clamp(maxX, 0, 1);
        maxY = MathUtils.clamp(maxY, 0, 1);
        maxZ = MathUtils.clamp(maxZ, 0, 1);

        return new BoxComponent(
                16 * (1 - maxX) - 8, // For some odd reason X is mirrored on Bedrock
                16 * minY,
                16 * minZ - 8,
                16 * (maxX - minX),
                16 * (maxY - minY),
                16 * (maxZ - minZ)
        );
    }
}
