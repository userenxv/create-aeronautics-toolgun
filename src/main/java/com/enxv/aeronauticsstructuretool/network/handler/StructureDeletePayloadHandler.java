package com.enxv.aeronauticsstructuretool.network.handler;

import com.enxv.aeronauticsstructuretool.DeleteSubLevelPayload;
import com.enxv.aeronauticsstructuretool.toolgun.ToolgunAccessPolicy;
import com.enxv.aeronauticsstructuretool.toolgun.StructureDeleteService;
import com.enxv.aeronauticsstructuretool.core.FailureMessages;
import com.enxv.aeronauticsstructuretool.core.ModConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class StructureDeletePayloadHandler {
    private StructureDeletePayloadHandler() {
    }

    public static void handle(DeleteSubLevelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (ToolgunAccessPolicy.holdsSurvivalTool(player)) {
                player.sendSystemMessage(Component.translatable(
                        "message.create_aeronautics_toolgun.survival_tool_restricted",
                        Component.translatable("screen.create_aeronautics_toolgun.mode.delete")
                ));
                return;
            }
            try {
                StructureDeleteService.delete(
                        level,
                        payload.clickedPos(),
                        payload.rangeDeleteEnabled(),
                        payload.deleteRange()
                );
                player.sendSystemMessage(Component.translatable("message.create_aeronautics_toolgun.deleted"));
            } catch (Exception exception) {
                ModConstants.LOGGER.warn(
                        "Structure deletion failed for player {} at {}",
                        player.getGameProfile().getName(),
                        payload.clickedPos(),
                        exception
                );
                player.sendSystemMessage(Component.translatable(
                        "message.create_aeronautics_toolgun.delete_failed",
                        FailureMessages.describe(exception, "structure deletion failed")
                ));
            }
        });
    }
}
