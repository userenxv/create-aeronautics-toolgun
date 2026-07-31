package com.enxv.aeronauticsstructuretool;

import com.enxv.aeronauticsstructuretool.printer.PortableStructurePrinterBlock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AeronauticsStructureToolMod.MOD_ID);

    public static final DeferredHolder<Block, PortableStructurePrinterBlock> PORTABLE_STRUCTURE_PRINTER = BLOCKS.register(
            "portable_structure_printer",
            () -> new PortableStructurePrinterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.5F)
                    .noOcclusion()
                    .requiresCorrectToolForDrops())
    );

    private ModBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
