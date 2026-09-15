package org.geysermc.hydraulic.mixin.ext;

import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.item.type.Item;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(value = GeyserItemStack.class, remap = false)
public abstract class GeyserItemStackMixin {
    @Unique
    private static final Logger HYDRAULIC_LOGGER = LoggerFactory.getLogger("GeyserItemStackMixin");
    @Unique
    private static final Set<Integer> HYDRAULIC_UNMAPPED_JAVA_ITEM_IDS = ConcurrentHashMap.newKeySet();

    @Shadow
    @Final
    private int javaId;

    @Inject(method = "getItemData(Lorg/geysermc/geyser/session/GeyserSession;)Lorg/cloudburstmc/protocol/bedrock/data/inventory/ItemData;", at = @At("HEAD"), cancellable = true)
    private void hydraulic$fallbackUnmappedJavaItemData(GeyserSession session, CallbackInfoReturnable<ItemData> cir) {
        if (!this.hydraulic$isUnmappedJavaItem()) {
            return;
        }

        this.hydraulic$logUnmappedJavaItem();
        cir.setReturnValue(ItemData.AIR);
    }

    @Inject(method = "asItem()Lorg/geysermc/geyser/item/type/Item;", at = @At("HEAD"), cancellable = true)
    private void hydraulic$fallbackUnmappedJavaItem(CallbackInfoReturnable<Item> cir) {
        if (!this.hydraulic$isUnmappedJavaItem()) {
            return;
        }

        this.hydraulic$logUnmappedJavaItem();
        cir.setReturnValue(Items.AIR);
    }

    @Unique
    private boolean hydraulic$isUnmappedJavaItem() {
        return this.javaId < 0 || this.javaId >= Registries.JAVA_ITEMS.get().size();
    }

    @Unique
    private void hydraulic$logUnmappedJavaItem() {
        if (HYDRAULIC_UNMAPPED_JAVA_ITEM_IDS.add(this.javaId)) {
            HYDRAULIC_LOGGER.warn("Falling back unmapped Java item id {} to Bedrock air", this.javaId);
        }
    }
}