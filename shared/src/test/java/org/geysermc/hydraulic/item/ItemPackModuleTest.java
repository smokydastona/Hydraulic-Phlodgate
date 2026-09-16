package org.geysermc.hydraulic.item;

import com.google.gson.JsonParser;
import net.kyori.adventure.key.Key;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import org.geysermc.hydraulic.pack.PackLogListener;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import team.unnamed.creative.item.ReferenceItemModel;
import team.unnamed.creative.metadata.pack.PackFormat;
import team.unnamed.creative.model.Model;
import team.unnamed.creative.serialize.minecraft.model.ModelSerializer;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemPackModuleTest {
  private static final PackLogListener PACK_LOG_LISTENER = new PackLogListener(LoggerFactory.getLogger("ItemPackModuleTest"));

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void parsesSupportedModernItemDefinition() {
        ItemPackModule.ParsedIndexedItemDefinition parsed = ItemPackModule.parseIndexedItemDefinition(
            JsonParser.parseString("""
                {
                  \"model\": {
                    \"type\": \"minecraft:model\",
                    \"model\": \"example:item/test\"
                  }
                }
                """),
            Identifier.fromNamespaceAndPath("example", "test_item")
        );

        assertNotNull(parsed.itemDefinition());
        assertNull(parsed.failureReason());
    assertTrue(parsed.itemDefinition().model() instanceof ReferenceItemModel);
    assertEquals("example:item/test", ((ReferenceItemModel) parsed.itemDefinition().model()).model().toString());
    }

    @Test
    void toleratesUnsupportedCustomItemModelSchema() {
        ItemPackModule.ParsedIndexedItemDefinition parsed = ItemPackModule.parseIndexedItemDefinition(
            JsonParser.parseString("""
                {
                  \"model\": {
                    \"type\": \"citadel:custom_item_model\",
                    \"model\": \"citadel:item/icon_item\"
                  }
                }
                """),
            Identifier.fromNamespaceAndPath("citadel", "icon_item")
        );

        assertNull(parsed.itemDefinition());
        assertNotNull(parsed.failureReason());
        assertTrue(parsed.failureReason().contains("Unknown item model type: citadel:custom_item_model"));
    }

    @Test
    void extractsModelKeyFromModernItemJsonVariants() {
        // Special schema (Lootr chest)
        Key specialKey = ItemPackModule.extractModelKeyFromItemJson(
            JsonParser.parseString("""
                {
                  "model": {
                    "type": "minecraft:special",
                    "base": "minecraft:chest",
                    "model": {
                      "type": "lootr:chest"
                    }
                  }
                }
                """),
            "lootr"
        );
        assertEquals(Key.key("minecraft", "chest"), specialKey);

        // Select schema (Lootr barrel / hose)
        Key selectKey = ItemPackModule.extractModelKeyFromItemJson(
            JsonParser.parseString("""
                {
                  "model": {
                    "type": "minecraft:select",
                    "property": "lootr:config_type",
                    "fallback": {
                      "type": "minecraft:model",
                      "model": "lootr:item/barrel"
                    }
                  }
                }
                """),
            "lootr"
        );
        assertEquals(Key.key("lootr", "item/barrel"), selectKey);

        // Condition schema (Farmer's Delight skillet)
        Key conditionKey = ItemPackModule.extractModelKeyFromItemJson(
            JsonParser.parseString("""
                {
                  "model": {
                    "type": "minecraft:condition",
                    "property": "farmersdelight:skillet/is_cooking",
                    "on_true": { "type": "minecraft:model", "model": "farmersdelight:item/skillet_cooking" },
                    "on_false": { "type": "minecraft:model", "model": "farmersdelight:item/skillet" }
                  }
                }
                """),
            "farmersdelight"
        );
        assertEquals(Key.key("farmersdelight", "item/skillet"), conditionKey);
    }

      @Test
      void resolvesDirectItemTextureBindingFromIndexedModel() throws IOException {
        Identifier itemLocation = Identifier.fromNamespaceAndPath("example", "test_item");
        Model itemModel = model(
          Key.key("example", "item/test_item"),
          """
            {
              "textures": {
              "layer0": "example:item/test"
              }
            }
            """
        );

        ItemPackModule.ResolvedItemTextureBinding binding = ItemPackModule.resolveTextureBindingCandidate(
          provider(Map.of(itemModel.key(), itemModel)),
          PACK_LOG_LISTENER,
          Items.APPLE,
          itemLocation,
          true
        );

        assertNotNull(binding);
        assertEquals(itemLocation.toString(), binding.sourceIdentifier());
        assertEquals(Key.key("example", "item/test"), binding.textureKey());
        assertTrue(binding.directLayerTexture());
      }

      @Test
      void fallsBackToBlockModelTextureWhenItemModelIsMissing() throws IOException {
        Identifier itemLocation = Identifier.fromNamespaceAndPath("example", "stone_item");
        Model blockModel = model(
          Key.key("minecraft", "block/stone"),
          """
            {
              "textures": {
              "all": "minecraft:block/stone"
              }
            }
            """
        );

        ItemPackModule.ResolvedItemTextureBinding binding = ItemPackModule.resolveTextureBindingCandidate(
          provider(Map.of(blockModel.key(), blockModel)),
          PACK_LOG_LISTENER,
          (BlockItem) Items.STONE,
          itemLocation,
          true
        );

        assertNotNull(binding);
        assertEquals("minecraft:stone", binding.sourceIdentifier());
        assertEquals(Key.key("minecraft", "block/stone"), binding.textureKey());
        assertTrue(binding.derivedFromBlockModel());
      }

      private static Model model(Key key, String json) throws IOException {
        return ModelSerializer.INSTANCE.deserializeFromJson(JsonParser.parseString(json), key, PackFormat.UNKNOWN);
      }

      private static org.geysermc.pack.converter.type.model.ModelStitcher.Provider provider(Map<Key, Model> models) {
        return models::get;
      }
}