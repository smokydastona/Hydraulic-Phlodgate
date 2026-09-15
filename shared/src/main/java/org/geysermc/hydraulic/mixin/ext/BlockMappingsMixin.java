package org.geysermc.hydraulic.mixin.ext;

import org.geysermc.geyser.registry.type.BlockMappings;
import org.geysermc.geyser.registry.type.GeyserBedrockBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(value = BlockMappings.class, remap = false)
public abstract class BlockMappingsMixin {
    @Unique
    private static final Logger HYDRAULIC_LOGGER = LoggerFactory.getLogger("BlockMappingsMixin");
    @Unique
    private static final Set<Integer> HYDRAULIC_UNMAPPED_JAVA_IDS = ConcurrentHashMap.newKeySet();

    @Inject(method = "getBedrockBlockId(I)I", at = @At("HEAD"), cancellable = true)
    private void hydraulic$fallbackUnmappedJavaBlock(int javaId, CallbackInfoReturnable<Integer> cir) {
        BlockMappings mappings = (BlockMappings) (Object) this;
        GeyserBedrockBlock bedrockBlock = mappings.getBedrockBlock(javaId);
        if (bedrockBlock != null) {
            return;
        }

        if (HYDRAULIC_UNMAPPED_JAVA_IDS.add(javaId)) {
            HYDRAULIC_LOGGER.warn("Falling back unmapped Java block state {} to Bedrock air", javaId);
        }
        cir.setReturnValue(mappings.getBedrockAir().getRuntimeId());
    }
}