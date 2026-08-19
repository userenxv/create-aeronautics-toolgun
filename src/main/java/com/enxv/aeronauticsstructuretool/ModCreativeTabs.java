package com.enxv.aeronauticsstructuretool;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AeronauticsStructureToolMod.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = CREATIVE_MODE_TABS.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.create_aeronautics_toolgun.main"))
                    .icon(() -> new ItemStack(ModItems.MAGNETIC_GUN.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.STRUCTURE_TOOL.get());
                        output.accept(ModItems.SURVIVAL_STRUCTURE_TOOL.get());
                        output.accept(ModItems.MAGNETIC_GUN.get());
                        output.accept(ModItems.CREATIVE_MAGNETIC_GUN.get());
                        output.accept(ModItems.PORTABLE_STRUCTURE_CONTAINER.get());
                        output.accept(ModItems.DISPOSABLE_VEHICLE_CONTAINER.get());
                    })
                    .build()
    );

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modBus) {
        CREATIVE_MODE_TABS.register(modBus);
    }
}
