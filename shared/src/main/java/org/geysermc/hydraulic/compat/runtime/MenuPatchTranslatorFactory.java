package org.geysermc.hydraulic.compat.runtime;

import net.minecraft.resources.Identifier;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.inventory.InventoryTranslator;
import org.geysermc.hydraulic.compat.CompatibilityRegistry;
import org.geysermc.hydraulic.compat.ir.CompiledCompatibilityPlan;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundOpenScreenPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MenuPatchTranslatorFactory {
    private MenuPatchTranslatorFactory() {
    }

    @Nullable
    public static InventoryTranslator<?> create(
        @NotNull GeyserSession session,
        @NotNull ClientboundOpenScreenPacket packet,
        @NotNull CompatibilityRegistry compatibilityRegistry
    ) {
        String javaIdentifier = CompatibilityRuntimeDiagnostics.resolveJavaMenuIdentifier(session, packet.getContainerId());
        return create(javaIdentifier, compatibilityRegistry);
    }

    @Nullable
    public static InventoryTranslator<?> create(
        @Nullable String javaIdentifier,
        @NotNull CompatibilityRegistry compatibilityRegistry
    ) {
        if (javaIdentifier == null) {
            return null;
        }

        CompiledCompatibilityPlan plan = compatibilityRegistry.dispatchTable().menu(Identifier.parse(javaIdentifier));
        if (!BridgeAdapterSupport.supportsMenuFallback(plan)) {
            return null;
        }
        return create(plan);
    }

    static boolean supports(@Nullable CompiledCompatibilityPlan plan) {
        return BridgeAdapterSupport.supportsMenuFallback(plan);
    }

    @Nullable
    static InventoryTranslator<?> create(@Nullable CompiledCompatibilityPlan plan) {
        if (!supports(plan)) {
            return null;
        }

        return InventoryTranslator.inventoryTranslator(plan.menuFallbackContainerType());
    }
}