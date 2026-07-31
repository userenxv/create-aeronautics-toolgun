package com.enxv.aeronauticsstructuretool;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AeronauticsStructureToolMod.MOD_ID);
    public static final DeferredItem<Item> STRUCTURE_TOOL = ITEMS.register(
            "structure_tool",
            () -> new StructureToolItem(new Item.Properties().stacksTo(1), false)
    );
    public static final DeferredItem<Item> SURVIVAL_STRUCTURE_TOOL = ITEMS.register(
            "survival_structure_tool",
            () -> new StructureToolItem(new Item.Properties().stacksTo(1), true)
    );
    public static final DeferredItem<Item> MAGNETIC_GUN = ITEMS.register(
            "magnetic_gun",
            () -> new MagneticGunItem(new Item.Properties().stacksTo(1).durability(3600))
    );
    public static final DeferredItem<Item> CREATIVE_MAGNETIC_GUN = ITEMS.register(
            "creative_magnetic_gun",
            () -> new MagneticGunItem(new Item.Properties().stacksTo(1))
    );
    public static final DeferredItem<Item> PORTABLE_STRUCTURE_CONTAINER = ITEMS.register(
            "portable_structure_container",
            () -> new PortableStructureContainerItem(new Item.Properties().stacksTo(1))
    );
    public static final DeferredItem<Item> DISPOSABLE_VEHICLE_CONTAINER = ITEMS.register(
            "disposable_vehicle_container",
            () -> PortableStructureContainerItem.disposable(new Item.Properties().stacksTo(1))
    );

    private ModItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    public static boolean isAnyMagneticGun(ItemStack stack) {
        return stack.is(MAGNETIC_GUN.get()) || stack.is(CREATIVE_MAGNETIC_GUN.get());
    }

    public static boolean isAnyStructureTool(ItemStack stack) {
        return stack.is(STRUCTURE_TOOL.get()) || stack.is(SURVIVAL_STRUCTURE_TOOL.get());
    }

    public static boolean isSurvivalStructureTool(ItemStack stack) {
        return stack.is(SURVIVAL_STRUCTURE_TOOL.get());
    }

    public static boolean isCreativeStructureTool(ItemStack stack) {
        return stack.is(STRUCTURE_TOOL.get());
    }

    public static boolean isCreativeMagneticGun(ItemStack stack) {
        return stack.is(CREATIVE_MAGNETIC_GUN.get());
    }
}
