package com.enxv.aeronauticsstructuretool.network.handler;

import com.enxv.aeronauticsstructuretool.AdjustRotateSubLevelPayload;
import com.enxv.aeronauticsstructuretool.AdjustTranslateSubLevelPayload;
import com.enxv.aeronauticsstructuretool.BeginRotateSubLevelPayload;
import com.enxv.aeronauticsstructuretool.BeginTranslateSubLevelPayload;
import com.enxv.aeronauticsstructuretool.FinishRotateSubLevelPayload;
import com.enxv.aeronauticsstructuretool.FinishTranslateSubLevelPayload;
import com.enxv.aeronauticsstructuretool.RotationAxisMode;
import com.enxv.aeronauticsstructuretool.SurvivalToolgunConfig;
import com.enxv.aeronauticsstructuretool.toolgun.ToolgunAccessPolicy;
import com.enxv.aeronauticsstructuretool.toolgun.transform.StructureTransformService;
import com.enxv.aeronauticsstructuretool.core.FailureMessages;
import com.enxv.aeronauticsstructuretool.core.ModConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Vector3d;

public final class StructureTransformPayloadHandlers {
    private StructureTransformPayloadHandlers() {
    }

    public static void handleBeginTranslation(BeginTranslateSubLevelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (rejectTranslation(player)) {
                return;
            }
            try {
                StructureTransformService.beginTranslation(
                        level,
                        player.getUUID(),
                        payload.subLevelId(),
                        new Vector3d(payload.pivotLocalX(), payload.pivotLocalY(), payload.pivotLocalZ())
                );
                player.sendSystemMessage(Component.translatable(
                        "message.create_aeronautics_toolgun.translate_started"
                ));
            } catch (Exception exception) {
                sendTranslationFailure(player, exception);
            }
        });
    }

    public static void handleAdjustTranslation(AdjustTranslateSubLevelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (rejectTranslation(player)) {
                return;
            }
            try {
                StructureTransformService.adjustTranslation(
                        level,
                        player.getUUID(),
                        RotationAxisMode.fromName(payload.axisName()),
                        payload.distanceDelta()
                );
            } catch (Exception exception) {
                sendTranslationFailure(player, exception);
            }
        });
    }

    public static void handleFinishTranslation(FinishTranslateSubLevelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (payload.confirm() && rejectTranslation(player)) {
                cancelTranslation(level, player);
                return;
            }
            try {
                StructureTransformService.finishTranslation(level, player.getUUID(), payload.confirm());
                player.sendSystemMessage(Component.translatable(payload.confirm()
                        ? "message.create_aeronautics_toolgun.translate_confirmed"
                        : "message.create_aeronautics_toolgun.translate_cancelled"));
            } catch (Exception exception) {
                sendTranslationFailure(player, exception);
            }
        });
    }

    public static void handleBeginRotation(BeginRotateSubLevelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (rejectRotation(player)) {
                return;
            }
            try {
                StructureTransformService.beginRotation(
                        level,
                        player.getUUID(),
                        payload.subLevelId(),
                        new Vector3d(payload.pivotLocalX(), payload.pivotLocalY(), payload.pivotLocalZ())
                );
                player.sendSystemMessage(Component.translatable(
                        "message.create_aeronautics_toolgun.rotate_started"
                ));
            } catch (Exception exception) {
                sendRotationFailure(player, exception);
            }
        });
    }

    public static void handleAdjustRotation(AdjustRotateSubLevelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (rejectRotation(player)) {
                return;
            }
            try {
                StructureTransformService.adjustRotation(
                        level,
                        player.getUUID(),
                        RotationAxisMode.fromName(payload.axisName()),
                        payload.degreesDelta()
                );
            } catch (Exception exception) {
                sendRotationFailure(player, exception);
            }
        });
    }

    public static void handleFinishRotation(FinishRotateSubLevelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (payload.confirm() && rejectRotation(player)) {
                cancelRotation(level, player);
                return;
            }
            try {
                StructureTransformService.finishRotation(level, player.getUUID(), payload.confirm());
                player.sendSystemMessage(Component.translatable(payload.confirm()
                        ? "message.create_aeronautics_toolgun.rotate_confirmed"
                        : "message.create_aeronautics_toolgun.rotate_cancelled"));
            } catch (Exception exception) {
                sendRotationFailure(player, exception);
            }
        });
    }

    private static boolean rejectTranslation(ServerPlayer player) {
        return ToolgunAccessPolicy.rejectSurvivalFeature(
                player,
                SurvivalToolgunConfig.allowTranslate(),
                Component.translatable("screen.create_aeronautics_toolgun.mode.translate")
        );
    }

    private static boolean rejectRotation(ServerPlayer player) {
        return ToolgunAccessPolicy.rejectSurvivalFeature(
                player,
                SurvivalToolgunConfig.allowRotate(),
                Component.translatable("screen.create_aeronautics_toolgun.mode.rotate")
        );
    }

    private static void cancelTranslation(ServerLevel level, ServerPlayer player) {
        try {
            StructureTransformService.finishTranslation(level, player.getUUID(), false);
        } catch (Exception exception) {
            sendTranslationFailure(player, exception);
        }
    }

    private static void cancelRotation(ServerLevel level, ServerPlayer player) {
        try {
            StructureTransformService.finishRotation(level, player.getUUID(), false);
        } catch (Exception exception) {
            sendRotationFailure(player, exception);
        }
    }

    private static void sendTranslationFailure(ServerPlayer player, Exception exception) {
        ModConstants.LOGGER.warn(
                "Structure translation failed for player {}",
                player.getGameProfile().getName(),
                exception
        );
        player.sendSystemMessage(Component.translatable(
                "message.create_aeronautics_toolgun.translate_failed",
                FailureMessages.describe(exception, "structure translation failed")
        ));
    }

    private static void sendRotationFailure(ServerPlayer player, Exception exception) {
        ModConstants.LOGGER.warn(
                "Structure rotation failed for player {}",
                player.getGameProfile().getName(),
                exception
        );
        player.sendSystemMessage(Component.translatable(
                "message.create_aeronautics_toolgun.rotate_failed",
                FailureMessages.describe(exception, "structure rotation failed")
        ));
    }
}
