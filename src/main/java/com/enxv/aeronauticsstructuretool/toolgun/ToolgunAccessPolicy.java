package com.enxv.aeronauticsstructuretool.toolgun;

import com.enxv.aeronauticsstructuretool.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class ToolgunAccessPolicy {
    private ToolgunAccessPolicy() {
    }

    public static boolean holdsSurvivalTool(ServerPlayer player) {
        return ModItems.isSurvivalStructureTool(player.getMainHandItem())
                || ModItems.isSurvivalStructureTool(player.getOffhandItem());
    }

    public static boolean holdsCreativeTool(ServerPlayer player) {
        return ModItems.isCreativeStructureTool(player.getMainHandItem())
                || ModItems.isCreativeStructureTool(player.getOffhandItem());
    }

    public static boolean rejectSurvivalFeature(ServerPlayer player, boolean allowed, Component featureName) {
        if (allowed || !holdsSurvivalTool(player)) {
            return false;
        }
        player.sendSystemMessage(Component.translatable(
                "message.create_aeronautics_toolgun.survival_tool_restricted",
                featureName
        ));
        return true;
    }
}
