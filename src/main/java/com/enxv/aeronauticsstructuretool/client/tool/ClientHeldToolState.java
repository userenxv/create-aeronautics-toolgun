package com.enxv.aeronauticsstructuretool.client.tool;

import com.enxv.aeronauticsstructuretool.ModItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

public final class ClientHeldToolState {
    private ClientHeldToolState() {
    }

    public static boolean holdsStructureTool(LocalPlayer player) {
        return player != null
                && (isStructureTool(player.getMainHandItem()) || isStructureTool(player.getOffhandItem()));
    }

    public static boolean holdsAstGun(LocalPlayer player) {
        return player != null && (isAstGun(player.getMainHandItem()) || isAstGun(player.getOffhandItem()));
    }

    public static boolean holdsRestrictedStructureTool(LocalPlayer player) {
        return player != null
                && (ModItems.isSurvivalStructureTool(player.getMainHandItem())
                || ModItems.isSurvivalStructureTool(player.getOffhandItem()));
    }

    private static boolean isStructureTool(ItemStack stack) {
        return ModItems.isAnyStructureTool(stack);
    }

    private static boolean isAstGun(ItemStack stack) {
        return ModItems.isAnyStructureTool(stack)
                || stack.is(ModItems.MAGNETIC_GUN.get())
                || stack.is(ModItems.CREATIVE_MAGNETIC_GUN.get());
    }
}
