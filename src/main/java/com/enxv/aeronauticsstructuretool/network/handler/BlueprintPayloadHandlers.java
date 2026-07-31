package com.enxv.aeronauticsstructuretool.network.handler;

import com.enxv.aeronauticsstructuretool.LoadSubLevelPayload;
import com.enxv.aeronauticsstructuretool.LoadSubLevelChunkPayload;
import com.enxv.aeronauticsstructuretool.CompleteLoadSubLevelPayload;
import com.enxv.aeronauticsstructuretool.SaveSubLevelPayload;
import com.enxv.aeronauticsstructuretool.SurvivalToolgunConfig;
import com.enxv.aeronauticsstructuretool.SyncLocalBlueprintPayload;
import com.enxv.aeronauticsstructuretool.toolgun.ToolgunAccessPolicy;
import com.enxv.aeronauticsstructuretool.toolgun.blueprint.BlueprintToolWorkflow;
import com.enxv.aeronauticsstructuretool.toolgun.blueprint.BlueprintLoadResult;
import com.enxv.aeronauticsstructuretool.toolgun.blueprint.BlueprintSaveResult;
import com.enxv.aeronauticsstructuretool.toolgun.blueprint.LoadBlueprintRequest;
import com.enxv.aeronauticsstructuretool.toolgun.blueprint.SaveBlueprintRequest;
import com.enxv.aeronauticsstructuretool.network.transfer.BlueprintLoadUploadManager;
import com.enxv.aeronauticsstructuretool.network.transfer.BlueprintLoadUploadRequest;
import com.enxv.aeronauticsstructuretool.server.ServerServices;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class BlueprintPayloadHandlers {
    private BlueprintPayloadHandlers() {
    }

    public static void handleSave(SaveSubLevelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                if (ToolgunAccessPolicy.rejectSurvivalFeature(
                        player,
                        SurvivalToolgunConfig.allowSave(),
                        Component.translatable("screen.create_aeronautics_toolgun.mode.save")
                )) {
                    return;
                }
                BlueprintSaveResult result = BlueprintToolWorkflow.save(player, new SaveBlueprintRequest(
                        payload.clickedPos(),
                        payload.fileName(),
                        payload.connectedSublevelProximity()
                ));
                if (!result.successful()) {
                    player.sendSystemMessage(Component.translatable(
                            "message.create_aeronautics_toolgun.save_failed",
                            result.failureReason()
                    ));
                    return;
                }
                PacketDistributor.sendToPlayer(player, new SyncLocalBlueprintPayload(
                        result.fileName(),
                        result.fileContents()
                ));
                player.sendSystemMessage(Component.translatable(
                        "message.create_aeronautics_toolgun.saved",
                        payload.fileName()
                ));
            }
        });
    }

    public static void handleLoad(LoadSubLevelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                if (!mayLoad(player)) {
                    return;
                }
                try {
                    ServerServices.BLUEPRINT_LOAD_UPLOAD.begin(player, toUploadRequest(payload));
                } catch (java.io.IOException exception) {
                    sendLoadFailure(player, exception.getMessage());
                }
            }
        });
    }

    public static void handleLoadChunk(LoadSubLevelChunkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            try {
                ServerServices.BLUEPRINT_LOAD_UPLOAD.append(
                        player,
                        payload.transferId(),
                        payload.offset(),
                        payload.contents()
                );
            } catch (java.io.IOException exception) {
                if (!"blueprint upload did not start or expired".equals(exception.getMessage())) {
                    sendLoadFailure(player, exception.getMessage());
                }
            }
        });
    }

    public static void handleLoadComplete(CompleteLoadSubLevelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!mayLoad(player)) {
                ServerServices.BLUEPRINT_LOAD_UPLOAD.cancel(player);
                return;
            }
            try {
                BlueprintLoadUploadManager.CompletedUpload upload =
                        ServerServices.BLUEPRINT_LOAD_UPLOAD.complete(player, payload.transferId());
                placeUploadedBlueprint(player, upload);
            } catch (java.io.IOException exception) {
                sendLoadFailure(player, exception.getMessage());
            }
        });
    }

    private static boolean mayLoad(ServerPlayer player) {
        if (!ToolgunAccessPolicy.holdsCreativeTool(player)) {
            sendLoadFailure(player, "creative structure tool required");
            return false;
        }
        if (ToolgunAccessPolicy.holdsSurvivalTool(player)) {
            player.sendSystemMessage(Component.translatable(
                    "message.create_aeronautics_toolgun.survival_tool_restricted",
                    Component.translatable("screen.create_aeronautics_toolgun.mode.load")
            ));
            return false;
        }
        return true;
    }

    private static void placeUploadedBlueprint(
            ServerPlayer player,
            BlueprintLoadUploadManager.CompletedUpload upload
    ) {
        BlueprintLoadUploadRequest request = upload.request();
        BlueprintLoadResult result = BlueprintToolWorkflow.load(player, new LoadBlueprintRequest(
                request.clickedPos(),
                request.face(),
                request.hitX(),
                request.hitY(),
                request.hitZ(),
                request.rotationDegrees(),
                request.scalePercent(),
                request.offsetX(),
                request.offsetY(),
                request.offsetZ(),
                request.autoWeld(),
                request.connectionMode(),
                request.snapMode(),
                request.fileName(),
                upload.contents()
        ));
        if (result.successful()) {
            player.sendSystemMessage(Component.translatable(
                    "message.create_aeronautics_toolgun.loaded",
                    request.fileName()
            ));
        } else {
            sendLoadFailure(player, result.failureReason());
        }
    }

    private static BlueprintLoadUploadRequest toUploadRequest(LoadSubLevelPayload payload) {
        return new BlueprintLoadUploadRequest(
                payload.transferId(),
                payload.clickedPos(),
                payload.face(),
                payload.hitX(),
                payload.hitY(),
                payload.hitZ(),
                payload.rotationDegrees(),
                payload.scalePercent(),
                payload.offsetX(),
                payload.offsetY(),
                payload.offsetZ(),
                payload.autoWeld(),
                payload.connectionMode(),
                payload.snapMode(),
                payload.fileName(),
                payload.totalBytes(),
                payload.sha256()
        );
    }

    private static void sendLoadFailure(ServerPlayer player, String reason) {
        player.sendSystemMessage(Component.translatable(
                "message.create_aeronautics_toolgun.load_failed",
                reason == null || reason.isBlank() ? "unknown blueprint upload error" : reason
        ));
    }
}
