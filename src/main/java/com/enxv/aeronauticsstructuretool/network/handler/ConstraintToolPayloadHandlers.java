package com.enxv.aeronauticsstructuretool.network.handler;

import com.enxv.aeronauticsstructuretool.DisconnectSubLevelPayload;
import com.enxv.aeronauticsstructuretool.ToggleSubLevelCollisionPayload;
import com.enxv.aeronauticsstructuretool.toolgun.constraint.ConstraintToolService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ConstraintToolPayloadHandlers {
    private ConstraintToolPayloadHandlers() {
    }

    public static void handleDisconnect(DisconnectSubLevelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ConstraintToolService.disconnect(player, payload.clickedPos());
            }
        });
    }

    public static void handleToggleCollision(ToggleSubLevelCollisionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ConstraintToolService.toggleCollision(player, payload.clickedPos());
            }
        });
    }
}
