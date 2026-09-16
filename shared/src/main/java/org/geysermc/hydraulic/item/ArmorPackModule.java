package org.geysermc.hydraulic.item;

import com.google.auto.service.AutoService;
import net.kyori.adventure.key.Key;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.Equippable;
import org.geysermc.hydraulic.compat.CompatibilityRegistry;
import org.geysermc.hydraulic.compat.ir.CompiledCompatibilityPlan;
import org.geysermc.hydraulic.compat.model.CompatibilityObject;
import org.geysermc.hydraulic.compat.runtime.CompatibilityDecisions;
import org.geysermc.hydraulic.pack.PackModule;
import org.geysermc.hydraulic.pack.TexturePackModule;
import org.geysermc.hydraulic.pack.context.PackPostProcessContext;
import org.geysermc.pack.bedrock.resource.attachables.Attachable;
import org.geysermc.pack.bedrock.resource.attachables.Attachables;
import org.geysermc.pack.bedrock.resource.attachables.attachable.Description;
import org.geysermc.pack.bedrock.resource.attachables.attachable.description.Scripts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import team.unnamed.creative.equipment.EquipmentLayerType;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@SuppressWarnings({"rawtypes", "this-escape"})
@AutoService(PackModule.class)
public class ArmorPackModule extends TexturePackModule<ArmorPackModule> {

    private static final Map<String, String> ATTACHABLE_MATERIALS = new HashMap<>() {
        {
            put("default", "armor");
            put("enchanted", "armor_enchanted");
        }
    };
    private static final Scripts ATTACHABLE_SCRIPTS = new Scripts();

    static {
        ATTACHABLE_SCRIPTS.parentSetup("variable.chest_layer_visible = 0.0;");
    }

    public ArmorPackModule() {
        this.postProcess(this::postProcess);
    }

    private void postProcess(@NotNull PackPostProcessContext<ArmorPackModule> context) {
        List<Item> armorItems = context.registryValues(BuiltInRegistries.ITEM).stream()
                .filter(item -> item.components().has(DataComponents.EQUIPPABLE) && item.components().get(DataComponents.EQUIPPABLE).assetId().isPresent())
                .toList();

        context.logger().info("Armor to convert: {} in mod {}", armorItems.size(), context.mod().id());

        for (Item armorItem : armorItems) {
            Equippable equippable = armorItem.components().get(DataComponents.EQUIPPABLE);
            Identifier armorItemLocation = BuiltInRegistries.ITEM.getKey(armorItem);
            CompiledCompatibilityPlan compatibilityPlan = compatibilityItemPlan(context, armorItemLocation);
            if (compatibilityPlan != null && !compatibilityPlan.supportsWearablePresentation()) {
                context.logger().info("Skipping armor attachable generation for {} because item presentation support is insufficient", armorItemLocation);
                continue;
            }

            EquipmentLayerType layerType = getEquipmentLayer(equippable.slot());
            if (layerType == null) {
                Optional<HolderSet<EntityType<?>>> optionalEntityType = equippable.allowedEntities();
                if (optionalEntityType.isPresent()) {
                    HolderSet<EntityType<?>> entityTypeHolderSet = optionalEntityType.get();

                    if (entityTypeHolderSet.contains(BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(EntityTypes.HORSE))) {
                        layerType = EquipmentLayerType.HORSE_BODY;
                    } else if (entityTypeHolderSet.contains(BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(EntityTypes.WOLF))) {
                        layerType = EquipmentLayerType.WOLF_BODY;
                    } else if (entityTypeHolderSet.contains(BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(EntityTypes.LLAMA))) {
                        layerType = EquipmentLayerType.LLAMA_BODY;
                    }
                }

                if (layerType == null) {
                    context.logger().info("Skipping armor attachable generation for {} because no Bedrock equipment layer could be inferred", armorItemLocation);
                    continue;
                }
            }

            Identifier armorTextureLocation = equippable.assetId().map(ResourceKey::identifier).orElseThrow();

            EquipmentAssetLoader.EquipmentAsset equipment = EquipmentAssetLoader.load(context.mod(), armorTextureLocation, context.logger());
            if (equipment == null) {
                context.logger().warn("Skipping armor attachable generation for {} because equipment asset {} could not be loaded", armorItemLocation, armorTextureLocation);
                continue;
            }
            List<Key> layers = equipment.layers(layerType);
            if (layers == null || layers.isEmpty()) {
                context.logger().warn("Skipping armor attachable generation for {} because equipment asset {} has no {} layer", armorItemLocation, armorTextureLocation, layerType.name().toLowerCase());
                continue;
            }
            Key layerTexture = layers.getFirst();

            Attachables armorAttachable = new Attachables();
            armorAttachable.formatVersion("1.10.0");

            Description description = new Description();
            description.identifier(armorItemLocation.toString());
            description.materials(ATTACHABLE_MATERIALS);
            description.scripts(ATTACHABLE_SCRIPTS);
            description.renderControllers(new String[] { "controller.render.armor" });

            Map<String, String> textures = new LinkedHashMap<>();
            Key resolvedLayerTexture = EquipmentAssetLoader.sourceTextureKey(layerType, layerTexture);
            textures.put("default", getOutputFromModel(context, resolvedLayerTexture).replace("textures/", "").replace(".png", ""));
            textures.put("enchanted", "textures/misc/enchanted_actor_glint");
            description.textures(textures);

            String geometryType = geometryType(layerType, equippable.slot());
            if (geometryType == null) {
                context.logger().info("Skipping armor attachable generation for {} because {} does not have a verified Bedrock attachable geometry", armorItemLocation, layerType.name().toLowerCase());
                continue;
            }

            description.geometry(Map.of("default", geometryType));

            Attachable attachable = new Attachable();
            attachable.description(description);
            armorAttachable.attachable(attachable);

            context.bedrockResourcePack().addAttachable(armorAttachable, "attachables/" + armorItemLocation.getPath() + ".json");
            context.logger().info("Generated armor attachable for {} using equipment asset {}", armorItemLocation, armorTextureLocation);
        }
    }

    @Override
    public boolean test(@NotNull PackPostProcessContext<ArmorPackModule> context) {
        return context.registryValues(BuiltInRegistries.ITEM).stream().anyMatch(item -> item.components().has(DataComponents.EQUIPPABLE) && item.components().get(DataComponents.EQUIPPABLE).assetId().isPresent());
    }

    private static @Nullable EquipmentLayerType getEquipmentLayer(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD, CHEST, FEET -> EquipmentLayerType.HUMANOID;
            case LEGS -> EquipmentLayerType.HUMANOID_LEGGINGS;
            default -> null;
        };
    }

    @Nullable
    private static String geometryType(@NotNull EquipmentLayerType layerType, @NotNull EquipmentSlot slot) {
        return switch (layerType) {
            case HUMANOID -> switch (slot) {
                case HEAD -> "geometry.player.armor.helmet";
                case CHEST -> "geometry.player.armor.chestplate";
                case FEET -> "geometry.player.armor.boots";
                default -> null;
            };
            case HUMANOID_LEGGINGS -> "geometry.player.armor.leggings";
            default -> null;
        };
    }

    @Nullable
    private static CompiledCompatibilityPlan compatibilityItemPlan(@NotNull PackPostProcessContext<ArmorPackModule> context, @NotNull Identifier itemLocation) {
        CompatibilityRegistry compatibilityRegistry = context.hydraulic().getPackManager().compatibilityRegistry();
        return compatibilityRegistry.dispatchTable().item(itemLocation);
    }
}
