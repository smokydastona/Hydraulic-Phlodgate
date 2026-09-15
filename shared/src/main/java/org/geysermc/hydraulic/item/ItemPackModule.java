package org.geysermc.hydraulic.item;

import com.google.auto.service.AutoService;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.kyori.adventure.key.Key;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomItemsEvent;
import org.geysermc.geyser.api.item.custom.v2.CustomItemBedrockOptions;
import org.geysermc.geyser.api.item.custom.v2.NonVanillaCustomItemDefinition;
import org.geysermc.geyser.api.item.custom.v2.component.geyser.GeyserBlockPlacer;
import org.geysermc.geyser.api.item.custom.v2.component.geyser.GeyserChargeable;
import org.geysermc.geyser.api.item.custom.v2.component.geyser.GeyserItemDataComponents;
import org.geysermc.hydraulic.compat.CompatibilityRegistry;
import org.geysermc.hydraulic.compat.MappingResolver;
import org.geysermc.hydraulic.compat.ir.CompiledCompatibilityPlan;
import org.geysermc.hydraulic.compat.runtime.FluidBucketTextureResolver;
import org.geysermc.hydraulic.pack.ModResourceIndex;
import org.geysermc.hydraulic.pack.PackLogListener;
import org.geysermc.hydraulic.pack.PackModule;
import org.geysermc.hydraulic.pack.TexturePackModule;
import org.geysermc.hydraulic.pack.context.PackContext;
import org.geysermc.hydraulic.pack.context.PackEventContext;
import org.geysermc.hydraulic.pack.context.PackPostProcessContext;
import org.geysermc.hydraulic.pack.context.PackPreProcessContext;
import org.geysermc.hydraulic.component.ComponentConverter;
import org.geysermc.hydraulic.util.HydraulicKey;
import org.geysermc.hydraulic.util.PackUtil;
import org.geysermc.pack.bedrock.resource.BedrockResourcePack;
import org.geysermc.pack.converter.type.model.ModelStitcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import team.unnamed.creative.item.*;
import team.unnamed.creative.metadata.pack.PackFormat;
import team.unnamed.creative.model.Model;
import team.unnamed.creative.model.ModelTexture;
import team.unnamed.creative.model.ModelTextures;
import team.unnamed.creative.serialize.minecraft.item.ItemSerializer;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@SuppressWarnings("this-escape")
@AutoService(PackModule.class)
public class ItemPackModule extends TexturePackModule<ItemPackModule> {
    private final Set<Identifier> itemsWith2dIcon = new LinkedHashSet<>();
    private final Set<Identifier> handheldItems = new LinkedHashSet<>();
    private final Map<String, String> itemBuiltinTexture = new HashMap<>();
    private final Map<Identifier, ResolvedItemTextureBinding> resolvedTextureBindings = new LinkedHashMap<>();

    public ItemPackModule() {
        this.listenOn(GeyserDefineCustomItemsEvent.class, this::onDefineCustomItems);

        this.preProcess(this::preProcess);
        this.postProcess(this::postProcess);
    }

    private void handleModel(@NotNull PackPreProcessContext<ItemPackModule> context, ItemModel itemModel, Identifier itemLocation) {
        if (itemModel instanceof ReferenceItemModel referenceModel) {
            Key modelKey = referenceModel.model();
            Model model = context.modelProvider().model(modelKey);
            if (model == null) {
                context.logger().debug("Could not resolve model {} for item {}", modelKey, itemLocation);
                return;
            }

            // Build the list of all parents in the model chain
            List<Key> parents = PackUtil.modelParents(context.modelProvider(), model);

            if (parents.contains(Model.ITEM_HANDHELD)) {
                itemsWith2dIcon.add(itemLocation); // item/handheld has the parent item/generated, so lets assume it's 2D
                handheldItems.add(itemLocation);
            } else if (parents.contains(Model.ITEM_GENERATED) || parents.contains(Model.BUILT_IN_GENERATED)) {
                itemsWith2dIcon.add(itemLocation);
            }
        } else if (itemModel instanceof SelectItemModel selectModel) { // See if we can actually do select models here
            handleModel(context, selectModel.fallback(), itemLocation);
        } else if (itemModel instanceof ConditionItemModel conditionModel) {
            handleModel(context, conditionModel.onTrue(), itemLocation);
        } else if (itemModel instanceof CompositeItemModel compositeModel) {
            List<ItemModel> models = compositeModel.models();
            if (!models.isEmpty()) {
                handleModel(context, models.getFirst(), itemLocation);
            }
        } else if (itemModel instanceof RangeDispatchItemModel rangeDispatchModel) {
            handleModel(context, rangeDispatchModel.fallback(), itemLocation);
        }
    }

    private void preProcess(@NotNull PackPreProcessContext<ItemPackModule> context) {
        this.itemsWith2dIcon.clear();
        this.handheldItems.clear();
        this.itemBuiltinTexture.clear();
        this.resolvedTextureBindings.clear();

        ModResourceIndex resourceIndex = context.hydraulic().getPackManager().modResourceIndex(context.mod().id());
        List<Item> items = context.registryValues(BuiltInRegistries.ITEM);
        ModelStitcher.Provider modelProvider = context.modelProvider();
        PackLogListener packLogListener = new PackLogListener(context.logger());
        for (Item item : items) {
            Identifier itemLocation = BuiltInRegistries.ITEM.getKey(item);

            classifyIndexedItem(context, resourceIndex, itemLocation);

            ResolvedItemTextureBinding resolvedTextureBinding = this.resolveTextureBinding(context, modelProvider, item, itemLocation, packLogListener);
            if (resolvedTextureBinding == null) {
                continue;
            }

            this.resolvedTextureBindings.put(itemLocation, resolvedTextureBinding);
            if (!resolvedTextureBinding.derivedFromBlockModel()
                && resolvedTextureBinding.directLayerTexture()
                && resolvedTextureBinding.textureKey().namespace().equals(Key.MINECRAFT_NAMESPACE)) {
                this.itemBuiltinTexture.put(itemLocation.toString(), PackUtil.getTextureName(resolvedTextureBinding.textureKey().toString()));
            }
        }
    }

    private void classifyIndexedItem(
        @NotNull PackPreProcessContext<ItemPackModule> context,
        @Nullable ModResourceIndex resourceIndex,
        @NotNull Identifier itemLocation
    ) {
        if (resourceIndex == null) {
            return;
        }

        Path itemAssetPath = resourceIndex.resolveItemAssetPath(itemLocation);
        if (itemAssetPath == null) {
            return;
        }

        if (isModernItemDefinitionPath(itemAssetPath, itemLocation)) {
            team.unnamed.creative.item.Item itemDefinition = parseIndexedItemDefinition(itemLocation, itemAssetPath, context);
            if (itemDefinition != null) {
                handleModel(context, itemDefinition.model(), itemLocation);
                return;
            } else {
                try (Reader reader = Files.newBufferedReader(itemAssetPath, StandardCharsets.UTF_8)) {
                    JsonElement json = JsonParser.parseReader(reader);
                    Key fallbackModelKey = extractModelKeyFromItemJson(json, itemLocation.getNamespace());
                    if (fallbackModelKey != null) {
                        Model fallbackModel = context.modelProvider().model(fallbackModelKey);
                        if (fallbackModel != null) {
                            List<Key> parents = PackUtil.modelParents(context.modelProvider(), fallbackModel);
                            if (parents.contains(Model.ITEM_HANDHELD)) {
                                itemsWith2dIcon.add(itemLocation);
                                handheldItems.add(itemLocation);
                            } else if (parents.contains(Model.ITEM_GENERATED) || parents.contains(Model.BUILT_IN_GENERATED)) {
                                itemsWith2dIcon.add(itemLocation);
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }

        classifyLegacyModel(context, itemLocation);
    }

    private void classifyLegacyModel(@NotNull PackPreProcessContext<ItemPackModule> context, @NotNull Identifier itemLocation) {
        Model model = context.modelProvider().model(Key.key(itemLocation.getNamespace(), "item/" + itemLocation.getPath()));
        if (model == null) {
            return;
        }

        List<Key> parents = PackUtil.modelParents(context.modelProvider(), model);
        if (parents.contains(Model.ITEM_HANDHELD)) {
            itemsWith2dIcon.add(itemLocation);
            handheldItems.add(itemLocation);
        } else if (parents.contains(Model.ITEM_GENERATED) || parents.contains(Model.BUILT_IN_GENERATED)) {
            itemsWith2dIcon.add(itemLocation);
        }
    }

    private @Nullable team.unnamed.creative.item.Item parseIndexedItemDefinition(
        @NotNull Identifier itemLocation,
        @NotNull Path itemAssetPath,
        @NotNull PackPreProcessContext<ItemPackModule> context
    ) {
        try (Reader reader = Files.newBufferedReader(itemAssetPath, StandardCharsets.UTF_8)) {
            JsonElement json = JsonParser.parseReader(reader);
            ParsedIndexedItemDefinition parsed = parseIndexedItemDefinition(json, itemLocation);
            if (parsed.failureReason() != null) {
                context.logger().warn(
                    "Skipping indexed modern item definition {} from {} because Hydraulic does not yet support its item model schema: {}",
                    itemLocation,
                    itemAssetPath,
                    parsed.failureReason()
                );
            }
            return parsed.itemDefinition();
        } catch (IOException e) {
            context.logger().warn("Failed to load indexed item definition {} from {}", itemLocation, itemAssetPath, e);
            return null;
        }
    }

    static @NotNull ParsedIndexedItemDefinition parseIndexedItemDefinition(@NotNull JsonElement json, @NotNull Identifier itemLocation) {
        try {
            return new ParsedIndexedItemDefinition(
                ItemSerializer.INSTANCE.deserializeFromJson(
                    json,
                    Key.key(itemLocation.getNamespace(), itemLocation.getPath()),
                    PackFormat.UNKNOWN
                ),
                null
            );
        } catch (IOException | IllegalArgumentException e) {
            return new ParsedIndexedItemDefinition(null, e.getMessage());
        }
    }

    public static @Nullable Key extractModelKeyFromItemJson(@Nullable JsonElement json, @NotNull String defaultNamespace) {
        if (json == null || json.isJsonNull()) {
            return null;
        }
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            String str = json.getAsString();
            return str.contains(":") ? Key.key(str) : Key.key(defaultNamespace, str);
        }
        if (json.isJsonObject()) {
            com.google.gson.JsonObject obj = json.getAsJsonObject();
            if (obj.has("model")) {
                Key modelKey = extractModelKeyFromItemJson(obj.get("model"), defaultNamespace);
                if (modelKey != null) {
                    return modelKey;
                }
            }
            if (obj.has("base")) {
                Key baseKey = extractModelKeyFromItemJson(obj.get("base"), defaultNamespace);
                if (baseKey != null) {
                    return baseKey;
                }
            }
            if (obj.has("fallback")) {
                Key fallbackKey = extractModelKeyFromItemJson(obj.get("fallback"), defaultNamespace);
                if (fallbackKey != null) {
                    return fallbackKey;
                }
            }
            if (obj.has("on_false")) {
                Key onFalseKey = extractModelKeyFromItemJson(obj.get("on_false"), defaultNamespace);
                if (onFalseKey != null) {
                    return onFalseKey;
                }
            }
            if (obj.has("on_true")) {
                Key onTrueKey = extractModelKeyFromItemJson(obj.get("on_true"), defaultNamespace);
                if (onTrueKey != null) {
                    return onTrueKey;
                }
            }
            if (obj.has("cases") && obj.get("cases").isJsonArray()) {
                com.google.gson.JsonArray cases = obj.getAsJsonArray("cases");
                for (JsonElement c : cases) {
                    Key caseKey = extractModelKeyFromItemJson(c, defaultNamespace);
                    if (caseKey != null) {
                        return caseKey;
                    }
                }
            }
            if (obj.has("entries") && obj.get("entries").isJsonArray()) {
                com.google.gson.JsonArray entries = obj.getAsJsonArray("entries");
                for (JsonElement entry : entries) {
                    Key entryKey = extractModelKeyFromItemJson(entry, defaultNamespace);
                    if (entryKey != null) {
                        return entryKey;
                    }
                }
            }
            if (obj.has("models") && obj.get("models").isJsonArray()) {
                com.google.gson.JsonArray models = obj.getAsJsonArray("models");
                for (JsonElement m : models) {
                    Key mKey = extractModelKeyFromItemJson(m, defaultNamespace);
                    if (mKey != null) {
                        return mKey;
                    }
                }
            }
            if (obj.has("parent") && obj.get("parent").isJsonPrimitive()) {
                String parentStr = obj.get("parent").getAsString();
                return parentStr.contains(":") ? Key.key(parentStr) : Key.key(defaultNamespace, parentStr);
            }
        }
        return null;
    }

    private static boolean isModernItemDefinitionPath(@NotNull Path itemAssetPath, @NotNull Identifier itemLocation) {
        String normalizedPath = itemAssetPath.toString().replace('\\', '/');
        return normalizedPath.contains("/assets/" + itemLocation.getNamespace() + "/items/");
    }

    record ParsedIndexedItemDefinition(
        @Nullable team.unnamed.creative.item.Item itemDefinition,
        @Nullable String failureReason
    ) {
    }

    private void postProcess(@NotNull PackPostProcessContext<ItemPackModule> context) {
        BedrockResourcePack bedrockPack = context.bedrockResourcePack();

        List<Item> items = context.registryValues(BuiltInRegistries.ITEM);
        ModelStitcher.Provider modelProvider = context.modelProvider();

        context.logger().info("Items to convert: {} in mod {}", items.size(), context.mod().id());

        PackLogListener packLogListener = new PackLogListener(context.logger());
        for (Item item : items) {
            Identifier itemLocation = BuiltInRegistries.ITEM.getKey(item);

            ResolvedItemTextureBinding resolvedTextureBinding = this.resolvedTextureBindings.get(itemLocation);
            if (resolvedTextureBinding == null) {
                resolvedTextureBinding = this.resolveTextureBinding(context, modelProvider, item, itemLocation, packLogListener);
            }
            if (resolvedTextureBinding != null) {
                ItemTextureBinding binding = new ItemTextureBinding(
                    resolvedTextureBinding.sourceIdentifier(),
                    getOutputFromModel(context, resolvedTextureBinding.textureKey()),
                    resolvedTextureBinding.derivedFromBlockModel()
                );

                if (binding.derivedFromBlockModel()) {
                    context.logger().info("Using compatibility-backed block item texture fallback for {} via {}", itemLocation, binding.sourceIdentifier());
                }

                bedrockPack.addItemTexture(itemLocation.toString(), binding.outputLocation().replace(".png", ""));
            } else {
                String fallbackTexture = resolveFallbackItemTexturePath(itemLocation.getPath());
                bedrockPack.addItemTexture(itemLocation.toString(), fallbackTexture);
            }
        }
    }

    private static String resolveFallbackItemTexturePath(String itemPath) {
        String lower = itemPath.toLowerCase();
        if (lower.contains("chest")) {
            return "textures/items/chest";
        } else if (lower.contains("barrel")) {
            return "textures/items/barrel";
        } else if (lower.contains("shulker")) {
            return "textures/items/shulker_box";
        } else if (lower.contains("bucket")) {
            return "textures/items/bucket";
        } else if (lower.contains("sword")) {
            return "textures/items/iron_sword";
        } else if (lower.contains("pickaxe")) {
            return "textures/items/iron_pickaxe";
        } else if (lower.contains("axe")) {
            return "textures/items/iron_axe";
        } else if (lower.contains("shovel")) {
            return "textures/items/iron_shovel";
        } else if (lower.contains("hoe")) {
            return "textures/items/iron_hoe";
        } else if (lower.contains("helmet")) {
            return "textures/items/iron_helmet";
        } else if (lower.contains("chestplate")) {
            return "textures/items/iron_chestplate";
        } else if (lower.contains("leggings")) {
            return "textures/items/iron_leggings";
        } else if (lower.contains("boots")) {
            return "textures/items/iron_boots";
        } else if (lower.contains("ingot")) {
            return "textures/items/iron_ingot";
        }
        return "textures/items/apple";
    }

    @Override
    public boolean test(@NotNull PackPostProcessContext<ItemPackModule> context) {
        return !context.registryValues(BuiltInRegistries.ITEM).isEmpty();
    }

    private void onDefineCustomItems(PackEventContext<GeyserDefineCustomItemsEvent, ItemPackModule> context) {
        GeyserDefineCustomItemsEvent event = context.event();
        List<Item> items = context.registryValues(BuiltInRegistries.ITEM);

        DefaultedRegistry<Item> registry = BuiltInRegistries.ITEM;
        for (Item item : items) {
            Identifier itemLocation = registry.getKey(item);
            CompiledCompatibilityPlan itemPlan = this.compatibilityItemPlan(context, itemLocation);

            try {
                if (itemPlan != null && !itemPlan.allowsCustomRegistration()) {
                    context.logger().info("Skipping custom item registration for {} because compatibility analysis does not support content/presentation", itemLocation);
                    continue;
                }

                NonVanillaCustomItemDefinition.Builder customItemDefinition = NonVanillaCustomItemDefinition.builder(
                        org.geysermc.geyser.api.util.Identifier.of(itemLocation.toString()),
                        org.geysermc.geyser.api.util.Identifier.of(itemLocation.toString()),
                        registry.getId(item)
                )
                        .displayName("%" + item.getDescriptionId());

                CustomItemBedrockOptions.Builder customItemOptions = CustomItemBedrockOptions.builder()
                        .allowOffhand(true);

                // Allow minecraft namespace texture to be used (remapped as hydraulic)
                if (itemBuiltinTexture.containsKey(itemLocation.toString())) {
                    customItemOptions.icon(itemBuiltinTexture.get(itemLocation.toString()));
                }

                // Add the icon if it should have an icon
                boolean is2d = itemsWith2dIcon.contains(itemLocation);
                if (is2d) {
                    customItemOptions.icon(itemLocation.toString());
                }

                if (item instanceof BlockItem blockItem && this.shouldUseBlockItemTextureBridge(context, blockItem)) {
                    customItemOptions.icon(itemLocation.toString());
                }

                if (!itemBuiltinTexture.containsKey(itemLocation.toString())
                    && !is2d
                    && !(item instanceof BlockItem && this.shouldUseBlockItemTextureBridge(context, (BlockItem) item))
                    && item instanceof BucketItem bucketItem
                ) {
                    String bucketTexture = FluidBucketTextureResolver.resolve(context.hydraulic().getPackManager().compatibilityRegistry(), bucketItem);
                    if (bucketTexture != null) {
                        customItemOptions.icon(bucketTexture);
                        context.logger().info("Using compatibility-backed fluid bucket icon fallback for {} via {}", itemLocation, BuiltInRegistries.FLUID.getKey(bucketItem.getContent()));
                    }
                }

                // Make it handheld if need be
                if (handheldItems.contains(itemLocation)) {
                    customItemOptions.displayHandheld(true);
                }

                CompiledCompatibilityPlan blockPlan = item instanceof BlockItem blockItem ? this.compatibilityBlockPlan(context, blockItem) : null;

                // Set the creative mappings
                if (item instanceof BlockItem) {
                    if (blockPlan == null || blockPlan.allowsCreativeExposure()) {
                        CreativeMappings.setup(item, customItemOptions);
                    }
                } else if (itemPlan == null || itemPlan.allowsCreativeExposure()) {
                    CreativeMappings.setup(item, customItemOptions);
                } else {
                    context.logger().info(
                        "Skipping creative exposure for {} because {} (runtime bridges: {})",
                        itemLocation,
                        itemPlan.creativeExposureReason(),
                        itemPlan.runtimeBridgeRequirementIds().isEmpty() ? "none" : String.join(", ", itemPlan.runtimeBridgeRequirementIds())
                    );
                }

                // Set all bedrock components using what java components we have
                ComponentConverter.setGeyserComponents(
                        item.components(),
                        customItemDefinition,
                        customItemOptions
                );

                // Set the needed component for bows to work correctly
                if (item instanceof BowItem) {
                    customItemDefinition.component(
                            GeyserItemDataComponents.CHARGEABLE,
                            GeyserChargeable.builder()
                                    .maxDrawDuration(1f)
                                    .chargeOnDraw(false)
                    );

                    // Include the default icon, this won't change in the hotbar when used but this works the best for now
                    customItemOptions.icon(itemLocation.toString());
                }

                // Set the needed component for crossbows to work correctly
                if (item instanceof CrossbowItem) {
                    customItemDefinition.component(
                            GeyserItemDataComponents.CHARGEABLE,
                            GeyserChargeable.builder()
                                    .maxDrawDuration(0f)
                                    .chargeOnDraw(true)
                    );

                    // Include the default icon, this won't change in the hotbar when used but this works the best for now
                    customItemOptions.icon(itemLocation.toString());
                }

                if (item instanceof BlockItem blockItem) {
                    // Set the block_placer component to the correct block
                    // This fixes animations sometimes not showing
                    Block block = blockItem.getBlock();
                    Identifier javaBlockIdentifier = BuiltInRegistries.BLOCK.getKey(block);
                    MappingResolver.ResolvedBlockState resolvedPlacement = context.hydraulic()
                        .getPackManager()
                        .compatibilityRegistry()
                        .dispatchTable()
                        .blockState(javaBlockIdentifier, block.defaultBlockState());
                    if (resolvedPlacement == null) {
                        resolvedPlacement = context.hydraulic()
                            .getPackManager()
                            .mappingResolver()
                            .resolveBlockState(javaBlockIdentifier, block.defaultBlockState());
                    }

                    if (blockPlan == null || blockPlan.supportsBlockPlacement()) {
                        customItemDefinition.component(
                                GeyserItemDataComponents.BLOCK_PLACER,
                            GeyserBlockPlacer.of(HydraulicKey.of(resolvedPlacement.identifier()), !is2d)
                        );
                    } else {
                        context.logger().info(
                            "Skipping block placement bridge for {} because compatibility analysis does not support placement (runtime bridges: {})",
                            itemLocation,
                            blockPlan.runtimeBridgeRequirementIds().isEmpty() ? "none" : String.join(", ", blockPlan.runtimeBridgeRequirementIds())
                        );
                    }

                    if (blockPlan == null || blockPlan.allowsCreativeExposure()) {
                        CreativeMappings.setupBlock(block, customItemOptions);
                    }
                }

                customItemDefinition.bedrockOptions(customItemOptions);

                event.register(customItemDefinition.build());
            } catch (Exception e) {
                context.logger().error("Unable to register {}:", itemLocation, e);
            }
        }
    }

    @Nullable
    private ResolvedItemTextureBinding resolveTextureBinding(
        @NotNull PackContext<ItemPackModule> context,
        @NotNull ModelStitcher.Provider modelProvider,
        @NotNull Item item,
        @NotNull Identifier itemLocation,
        @NotNull PackLogListener packLogListener
    ) {
        boolean allowBlockItemTextureFallback = !(item instanceof BlockItem blockItem)
            || Optional.ofNullable(this.compatibilityBlockPlan(context, blockItem)).map(CompiledCompatibilityPlan::supportsBlockItemTextureFallback).orElse(true);

        ResolvedItemTextureBinding resolvedBinding = resolveTextureBindingCandidate(modelProvider, packLogListener, item, itemLocation, allowBlockItemTextureFallback);
        if (resolvedBinding != null) {
            return resolvedBinding;
        }

        ModResourceIndex resourceIndex = context.hydraulic().getPackManager().modResourceIndex(context.mod().id());
        if (resourceIndex != null) {
            Path itemAssetPath = resourceIndex.resolveItemAssetPath(itemLocation);
            if (itemAssetPath != null && Files.isRegularFile(itemAssetPath)) {
                try (Reader reader = Files.newBufferedReader(itemAssetPath, StandardCharsets.UTF_8)) {
                    JsonElement json = JsonParser.parseReader(reader);
                    Key extractedModelKey = extractModelKeyFromItemJson(json, itemLocation.getNamespace());
                    if (extractedModelKey != null) {
                        Model extractedModel = modelProvider.model(extractedModelKey);
                        if (extractedModel != null) {
                            ResolvedItemTextureBinding binding = resolveModelTextureBinding(modelProvider, extractedModel, itemLocation.toString(), false, packLogListener);
                            if (binding != null) {
                                return binding;
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            Path directTexture = resourceIndex.resolveTexturePath(Key.key(itemLocation.getNamespace(), "item/" + itemLocation.getPath()));
            if (directTexture == null) {
                directTexture = resourceIndex.resolveTexturePath(Key.key(itemLocation.getNamespace(), "block/" + itemLocation.getPath()));
            }
            if (directTexture == null) {
                directTexture = resourceIndex.resolveTexturePath(Key.key(itemLocation.getNamespace(), itemLocation.getPath()));
            }
            if (directTexture != null) {
                return new ResolvedItemTextureBinding(itemLocation.toString(), Key.key(itemLocation.getNamespace(), "item/" + itemLocation.getPath()), false, true);
            }
        }

        Model baseModel = modelProvider.model(Key.key(itemLocation.getNamespace(), "item/" + itemLocation.getPath()));
        if (baseModel != null) {
            if (!(item instanceof BlockItem)) {
                context.logger().warn("Item {} has no layer0 texture, skipping", itemLocation);
                return null;
            }
        }

        if (!(item instanceof BlockItem blockItem)) {
            context.logger().warn("Item {} has no item model, skipping", itemLocation);
            return null;
        }

        if (!allowBlockItemTextureFallback) {
            context.logger().warn("Item {} has no item model and no compatibility-backed block fallback, skipping", itemLocation);
            return null;
        }

        Identifier blockLocation = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock());
        Model blockModel = modelProvider.model(Key.key(blockLocation.getNamespace(), "block/" + blockLocation.getPath()));
        if (blockModel == null) {
            context.logger().warn("Item {} has no item model and block model {} is missing, skipping", itemLocation, blockLocation);
            return null;
        }

        ResolvedItemTextureBinding blockBinding = resolveModelTextureBinding(modelProvider, blockModel, blockLocation.toString(), true, packLogListener);
        if (blockBinding == null) {
            context.logger().warn("Item {} block model {} has no resolvable texture, skipping", itemLocation, blockLocation);
            return null;
        }

        return blockBinding;
    }

    static @Nullable ResolvedItemTextureBinding resolveTextureBindingCandidate(
        @NotNull ModelStitcher.Provider modelProvider,
        @NotNull PackLogListener packLogListener,
        @NotNull Item item,
        @NotNull Identifier itemLocation,
        boolean allowBlockItemTextureFallback
    ) {
        Model baseModel = modelProvider.model(Key.key(itemLocation.getNamespace(), "item/" + itemLocation.getPath()));
        if (baseModel != null) {
            ResolvedItemTextureBinding itemBinding = resolveModelTextureBinding(modelProvider, baseModel, itemLocation.toString(), false, packLogListener);
            if (itemBinding != null) {
                return itemBinding;
            }
        }

        if (!(item instanceof BlockItem blockItem) || !allowBlockItemTextureFallback) {
            return null;
        }

        Identifier blockLocation = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock());
        Model blockModel = modelProvider.model(Key.key(blockLocation.getNamespace(), "block/" + blockLocation.getPath()));
        if (blockModel == null) {
            return null;
        }

        return resolveModelTextureBinding(modelProvider, blockModel, blockLocation.toString(), true, packLogListener);
    }

    static @Nullable ResolvedItemTextureBinding resolveModelTextureBinding(
        @NotNull ModelStitcher.Provider modelProvider,
        @NotNull Model baseModel,
        @NotNull String sourceIdentifier,
        boolean derivedFromBlockModel,
        @NotNull PackLogListener packLogListener
    ) {
        Model stitchedModel = new ModelStitcher(modelProvider, baseModel, packLogListener).stitch();
        Key textureKey = primaryTexture(stitchedModel);
        if (textureKey == null) {
            return null;
        }

        List<ModelTexture> layers = stitchedModel.textures().layers();
        boolean directLayerTexture = layers != null
            && !layers.isEmpty()
            && layers.getFirst().key() != null
            && layers.getFirst().key().equals(textureKey);
        return new ResolvedItemTextureBinding(sourceIdentifier, textureKey, derivedFromBlockModel, directLayerTexture);
    }

    private boolean shouldUseBlockItemTextureBridge(@NotNull PackEventContext<GeyserDefineCustomItemsEvent, ItemPackModule> context, @NotNull BlockItem blockItem) {
        CompiledCompatibilityPlan blockPlan = this.compatibilityBlockPlan(context, blockItem);
        return blockPlan == null || blockPlan.supportsBlockItemTextureFallback();
    }

    @Nullable
    private CompiledCompatibilityPlan compatibilityItemPlan(@NotNull PackContext<ItemPackModule> context, @NotNull Identifier itemLocation) {
        CompatibilityRegistry compatibilityRegistry = context.hydraulic().getPackManager().compatibilityRegistry();
        return compatibilityRegistry.dispatchTable().item(itemLocation);
    }

    @Nullable
    private CompiledCompatibilityPlan compatibilityBlockPlan(@NotNull PackContext<ItemPackModule> context, @NotNull BlockItem blockItem) {
        CompatibilityRegistry compatibilityRegistry = context.hydraulic().getPackManager().compatibilityRegistry();
        Identifier blockLocation = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock());
        return compatibilityRegistry.dispatchTable().block(blockLocation);
    }

    @Nullable
    private static Key primaryTexture(@Nullable Model model) {
        if (model == null) {
            return null;
        }

        List<ModelTexture> layers = model.textures().layers();
        if (layers != null && !layers.isEmpty() && layers.getFirst().key() != null) {
            return layers.getFirst().key();
        }

        Map<String, ModelTexture> textures = textures(model.textures());
        for (String candidate : List.of("particle", "all", "side", "top", "bottom", "front", "back", "north", "south", "east", "west")) {
            ModelTexture texture = texture(textures, candidate, new HashSet<>());
            if (texture != null && texture.key() != null) {
                return texture.key();
            }
        }

        for (ModelTexture texture : textures.values()) {
            if (texture != null && texture.key() != null) {
                return texture.key();
            }
        }
        return null;
    }

    @NotNull
    private static Map<String, ModelTexture> textures(@NotNull ModelTextures modelTextures) {
        Map<String, ModelTexture> textures = new LinkedHashMap<>(modelTextures.variables());
        textures.put("particle", modelTextures.particle());
        for (int index = 0; index < modelTextures.layers().size(); index++) {
            textures.put("layer" + index, modelTextures.layers().get(index));
        }
        return textures;
    }

    @Nullable
    private static ModelTexture texture(@NotNull Map<String, ModelTexture> textures, @NotNull String key, @NotNull Set<String> visited) {
        if (!visited.add(key)) {
            return null;
        }

        ModelTexture value = textures.get(key);
        if (value != null && value.reference() != null) {
            return texture(textures, value.reference(), visited);
        }

        return value;
    }

    private record ItemTextureBinding(@NotNull String sourceIdentifier, @NotNull String outputLocation, boolean derivedFromBlockModel) {
    }

    record ResolvedItemTextureBinding(
        @NotNull String sourceIdentifier,
        @NotNull Key textureKey,
        boolean derivedFromBlockModel,
        boolean directLayerTexture
    ) {
    }
}
