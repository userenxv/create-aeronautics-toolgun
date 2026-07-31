package com.enxv.aeronauticsstructuretool;

import com.enxv.aeronauticsstructuretool.printer.PortableStructurePrinterBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AeronauticsStructureToolMod.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PortableStructurePrinterBlockEntity>> PORTABLE_STRUCTURE_PRINTER =
            BLOCK_ENTITY_TYPES.register(
                    "portable_structure_printer",
                    () -> BlockEntityType.Builder.of(
                            PortableStructurePrinterBlockEntity::new,
                            ModBlocks.PORTABLE_STRUCTURE_PRINTER.get()
                    ).build(null)
            );

    private ModBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITY_TYPES.register(modBus);
    }
}
