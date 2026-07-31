package com.enxv.aeronauticsstructuretool.network.handler;

import com.enxv.aeronauticsstructuretool.MagneticGunAdjustDistancePayload;
import com.enxv.aeronauticsstructuretool.MagneticGunFreezePayload;
import com.enxv.aeronauticsstructuretool.MagneticGunLaunchPayload;
import com.enxv.aeronauticsstructuretool.MagneticGunPrecisionPayload;
import com.enxv.aeronauticsstructuretool.MagneticGunRotatePayload;
import com.enxv.aeronauticsstructuretool.MagneticGunStartResultPayload;
import com.enxv.aeronauticsstructuretool.toolgun.magnetic.MagneticGunServerService;
import com.enxv.aeronauticsstructuretool.MagneticGunStartPayload;
import com.enxv.aeronauticsstructuretool.MagneticGunStopPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;

public final class MagneticGunPayloadHandlers {
    private MagneticGunPayloadHandlers() {
    }

    public static void handleStart(MagneticGunStartPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                MagneticGunServerService.ActionResult result = MagneticGunServerService.start(player, payload);
                reportFailure(player, result);
                PacketDistributor.sendToPlayer(
                        player,
                        new MagneticGunStartResultPayload(payload.subLevelId(), result.successful())
                );
            }
        });
    }

    public static void handleStop(MagneticGunStopPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                reportFailure(player, MagneticGunServerService.stop(player));
            }
        });
    }

    public static void handleAdjustDistance(MagneticGunAdjustDistancePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                reportFailure(player, MagneticGunServerService.adjustDistance(player, payload.delta()));
            }
        });
    }

    public static void handleFreeze(MagneticGunFreezePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                reportFailure(player, MagneticGunServerService.setFrozen(player, payload.frozen()));
            }
        });
    }

    public static void handleRotate(MagneticGunRotatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                reportFailure(player, MagneticGunServerService.rotate(player, payload));
            }
        });
    }

    public static void handleLaunch(MagneticGunLaunchPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                reportFailure(player, MagneticGunServerService.launch(player, payload));
            }
        });
    }

    public static void handlePrecision(MagneticGunPrecisionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                reportFailure(player, MagneticGunServerService.setPrecisionMode(player, payload.enabled()));
            }
        });
    }

    private static void reportFailure(
            ServerPlayer player,
            MagneticGunServerService.ActionResult result
    ) {
        if (result.successful()) {
            return;
        }
        player.sendSystemMessage(Component.translatable(
                "message.create_aeronautics_toolgun.magnetic_gun_failed",
                Component.translatable(result.failureTranslationKey())
        ));
    }
}
