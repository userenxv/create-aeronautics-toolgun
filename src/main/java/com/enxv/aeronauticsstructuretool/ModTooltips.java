package com.enxv.aeronauticsstructuretool;

import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;
import net.createmod.catnip.lang.FontHelper;

public final class ModTooltips {
    private ModTooltips() {
    }

    public static void register() {
        register(ModItems.STRUCTURE_TOOL.get());
        register(ModItems.SURVIVAL_STRUCTURE_TOOL.get());
        register(ModItems.MAGNETIC_GUN.get());
        register(ModItems.CREATIVE_MAGNETIC_GUN.get());
    }

    private static void register(net.minecraft.world.item.Item item) {
        TooltipModifier.REGISTRY.register(
                item,
                new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE)
                        .andThen(TooltipModifier.mapNull(KineticStats.create(item)))
        );
    }
}
