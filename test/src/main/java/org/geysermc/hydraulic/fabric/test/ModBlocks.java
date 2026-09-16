package org.geysermc.hydraulic.fabric.test;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import org.geysermc.hydraulic.fabric.test.machine.ItemTransferMachineBlock;
import org.geysermc.hydraulic.fabric.test.machine.ProcessingMachineBlock;
import org.geysermc.hydraulic.fabric.test.machine.EnergyMachineBlockEntity;
import org.geysermc.hydraulic.fabric.test.machine.FluidMachineBlockEntity;
import org.geysermc.hydraulic.fabric.test.machine.MixedResourceMachineBlock;
import org.geysermc.hydraulic.fabric.test.machine.MenuMachineBlock;
import org.geysermc.hydraulic.fabric.test.machine.ResourceMachineBlock;

import java.util.function.Function;

public class ModBlocks {
    public static final Block GOLDEN_BARREL = register(
            "golden_barrel",
            Block::new,
            BlockBehaviour.Properties.of()
                    .requiresCorrectToolForDrops()
                    .pushReaction(PushReaction.BLOCK)
                    .explosionResistance(9999f),
            true
    );

    public static final Block ITEM_TRANSFER_MACHINE = register(
            "item_transfer_machine",
            ItemTransferMachineBlock::new,
            BlockBehaviour.Properties.of()
                    .requiresCorrectToolForDrops()
                    .strength(3.5f),
            true
    );

    public static final Block PROCESSING_MACHINE = register(
            "processing_machine",
            ProcessingMachineBlock::new,
            BlockBehaviour.Properties.of()
                    .requiresCorrectToolForDrops()
                    .strength(3.5f),
            true
    );

    public static final Block FLUID_MACHINE = register(
            "fluid_machine",
            properties -> new ResourceMachineBlock(properties, FluidMachineBlockEntity::new),
            BlockBehaviour.Properties.of().requiresCorrectToolForDrops().strength(3.5f),
            true
    );

    public static final Block ENERGY_MACHINE = register(
            "energy_machine",
            properties -> new ResourceMachineBlock(properties, EnergyMachineBlockEntity::new),
            BlockBehaviour.Properties.of().requiresCorrectToolForDrops().strength(3.5f),
            true
    );

    public static final Block MIXED_RESOURCE_MACHINE = register(
            "mixed_resource_machine",
            MixedResourceMachineBlock::new,
            BlockBehaviour.Properties.of().requiresCorrectToolForDrops().strength(3.5f),
            true
    );

    public static final Block MENU_MACHINE = register(
            "menu_machine",
            MenuMachineBlock::new,
            BlockBehaviour.Properties.of().requiresCorrectToolForDrops().strength(3.5f),
            true
    );

    private static Block register(String name, Function<BlockBehaviour.Properties, Block> blockFactory, BlockBehaviour.Properties properties, boolean shouldRegisterItem) {
        ResourceKey<Block> blockKey = keyOfBlock(name);
        Block block = blockFactory.apply(properties.setId(blockKey));

        if (shouldRegisterItem) {
            ResourceKey<Item> itemKey = keyOfItem(name);

            BlockItem blockItem = new BlockItem(block, new Item.Properties().setId(itemKey));
            Registry.register(BuiltInRegistries.ITEM, itemKey, blockItem);
        }

        return Registry.register(BuiltInRegistries.BLOCK, blockKey, block);
    }

    private static ResourceKey<Block> keyOfBlock(String name) {
        return ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(HydraulicTestMod.MOD_ID, name));
    }

    private static ResourceKey<Item> keyOfItem(String name) {
        return ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(HydraulicTestMod.MOD_ID, name));
    }

    public static void init() {}
}
