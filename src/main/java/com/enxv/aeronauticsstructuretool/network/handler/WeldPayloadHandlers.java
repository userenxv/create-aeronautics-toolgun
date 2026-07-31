package com.enxv.aeronauticsstructuretool.network.handler;

import com.enxv.aeronauticsstructuretool.BearingAxisMode;
import com.enxv.aeronauticsstructuretool.ConnectionMode;
import com.enxv.aeronauticsstructuretool.FinishSimpleWeldPayload;
import com.enxv.aeronauticsstructuretool.MoveSubLevelPointPayload;
import com.enxv.aeronauticsstructuretool.SurvivalToolgunConfig;
import com.enxv.aeronauticsstructuretool.WeldSubLevelsPayload;
import com.enxv.aeronauticsstructuretool.toolgun.ToolgunAccessPolicy;
import com.enxv.aeronauticsstructuretool.toolgun.weld.WeldToolService;
import com.enxv.aeronauticsstructuretool.toolgun.weld.MoveSubLevelPointRequest;
import com.enxv.aeronauticsstructuretool.toolgun.weld.SimpleWeldRequest;
import com.enxv.aeronauticsstructuretool.toolgun.weld.WeldRequest;
import com.enxv.aeronauticsstructuretool.core.FailureMessages;
import com.enxv.aeronauticsstructuretool.core.ModConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class WeldPayloadHandlers {
    private WeldPayloadHandlers() {
    }

    public static void handleWeld(WeldSubLevelsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (ToolgunAccessPolicy.rejectSurvivalFeature(
                    player,
                    SurvivalToolgunConfig.allowWeld(),
                    Component.translatable("screen.create_aeronautics_toolgun.mode.weld")
            )) {
                return;
            }
            try {
                WeldToolService.weld(level, new WeldRequest(
                        payload.firstSubLevelId(),
                        payload.secondSubLevelId(),
                        new Vector3d(payload.firstX(), payload.firstY(), payload.firstZ()),
                        new Vector3d(payload.adjustedSecondX(), payload.adjustedSecondY(), payload.adjustedSecondZ()),
                        new Vector3d(payload.secondLocalX(), payload.secondLocalY(), payload.secondLocalZ()),
                        payload.firstFace(),
                        payload.secondFace(),
                        BearingAxisMode.fromName(payload.bearingAxisMode()),
                        ConnectionMode.fromName(payload.connectionMode())
                ));
                player.sendSystemMessage(Component.translatable("message.create_aeronautics_toolgun.weld_success"));
            } catch (Exception exception) {
                ModConstants.LOGGER.warn(
                        "Manual weld failed for player {}",
                        player.getGameProfile().getName(),
                        exception
                );
                player.sendSystemMessage(Component.translatable(
                        "message.create_aeronautics_toolgun.weld_failed",
                        FailureMessages.describe(exception, "weld failed")
                ));
            }
        });
    }

    public static void handleMovePoint(MoveSubLevelPointPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            try {
                WeldToolService.MovePointResult result = WeldToolService.movePoint(
                        player,
                        level,
                        new MoveSubLevelPointRequest(
                                payload.subLevelId(),
                                new Vector3d(payload.localX(), payload.localY(), payload.localZ()),
                                new Vector3d(payload.targetX(), payload.targetY(), payload.targetZ())
                        )
                );
                if (result == WeldToolService.MovePointResult.RESTRICTED) {
                    player.sendSystemMessage(Component.translatable(
                            "message.create_aeronautics_toolgun.survival_tool_restricted",
                            Component.translatable("screen.create_aeronautics_toolgun.mode.weld")
                    ));
                }
            } catch (Exception exception) {
                player.sendSystemMessage(Component.translatable(
                        "message.create_aeronautics_toolgun.weld_failed",
                        FailureMessages.describe(exception, "weld point move failed")
                ));
                com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod.LOGGER.warn(
                        "Failed to move weld point for player {}",
                        player.getGameProfile().getName(),
                        exception
                );
            }
        });
    }

    public static void handleSimpleWeld(FinishSimpleWeldPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (ToolgunAccessPolicy.rejectSurvivalFeature(
                    player,
                    SurvivalToolgunConfig.allowSimpleWeld(),
                    Component.translatable("screen.create_aeronautics_toolgun.mode.simple_weld")
            )) {
                return;
            }
            try {
                WeldToolService.simpleWeld(level, new SimpleWeldRequest(
                        payload.childSubLevelId(),
                        payload.parentSubLevelId(),
                        new Vector3d(payload.childLocalX(), payload.childLocalY(), payload.childLocalZ()),
                        new Vector3d(payload.parentLocalX(), payload.parentLocalY(), payload.parentLocalZ()),
                        new Quaterniond(
                                payload.relativeRotationX(),
                                payload.relativeRotationY(),
                                payload.relativeRotationZ(),
                                payload.relativeRotationW()
                        ),
                        new Vector3d(payload.parentOffsetX(), payload.parentOffsetY(), payload.parentOffsetZ())
                ));
                player.sendSystemMessage(Component.translatable(
                        "message.create_aeronautics_toolgun.simple_weld_confirmed"
                ));
            } catch (Exception exception) {
                ModConstants.LOGGER.warn(
                        "Simple weld failed for player {}",
                        player.getGameProfile().getName(),
                        exception
                );
                player.sendSystemMessage(Component.translatable(
                        "message.create_aeronautics_toolgun.simple_weld_failed",
                        FailureMessages.describe(exception, "simple weld failed")
                ));
            }
        });
    }
}
