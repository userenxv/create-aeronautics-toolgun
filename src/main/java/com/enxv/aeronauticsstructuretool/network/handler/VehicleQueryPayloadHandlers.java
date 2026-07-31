package com.enxv.aeronauticsstructuretool.network.handler;

import com.enxv.aeronauticsstructuretool.QueryVehicleActionPayload;
import com.enxv.aeronauticsstructuretool.RequestQueryVehiclePreviewPayload;
import com.enxv.aeronauticsstructuretool.RequestQueryVehiclesPayload;
import com.enxv.aeronauticsstructuretool.SurvivalToolgunConfig;
import com.enxv.aeronauticsstructuretool.SyncQueryVehiclePreviewPayload;
import com.enxv.aeronauticsstructuretool.SyncQueryVehiclesPayload;
import com.enxv.aeronauticsstructuretool.toolgun.ToolgunAccessPolicy;
import com.enxv.aeronauticsstructuretool.vehicle.query.VehiclePreview;
import com.enxv.aeronauticsstructuretool.vehicle.query.VehicleQueryEntry;
import com.enxv.aeronauticsstructuretool.vehicle.query.VehicleQueryService;
import com.enxv.aeronauticsstructuretool.core.FailureMessages;
import com.enxv.aeronauticsstructuretool.core.ModConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Vector3d;

import java.util.List;

public final class VehicleQueryPayloadHandlers {
    private VehicleQueryPayloadHandlers() {
    }

    public static void handleQuery(RequestQueryVehiclesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (ToolgunAccessPolicy.rejectSurvivalFeature(
                    player,
                    SurvivalToolgunConfig.allowQuery(),
                    Component.translatable("screen.create_aeronautics_toolgun.mode.query")
            )) {
                PacketDistributor.sendToPlayer(player, new SyncQueryVehiclesPayload(List.of()));
                return;
            }
            try {
                List<SyncQueryVehiclesPayload.Entry> entries = VehicleQueryService.query(
                                level,
                                player.blockPosition(),
                                new Vector3d(player.getX(), player.getY(), player.getZ()),
                                payload.range(),
                                ToolgunAccessPolicy.holdsSurvivalTool(player)
                        ).stream()
                        .map(VehicleQueryPayloadHandlers::toPayloadEntry)
                        .toList();
                PacketDistributor.sendToPlayer(player, new SyncQueryVehiclesPayload(entries));
            } catch (Exception exception) {
                PacketDistributor.sendToPlayer(player, new SyncQueryVehiclesPayload(List.of()));
                player.sendSystemMessage(Component.translatable(
                        "message.create_aeronautics_toolgun.query_action_failed",
                        exception.getMessage() == null ? "query failed" : exception.getMessage()
                ));
            }
        });
    }

    public static void handlePreview(RequestQueryVehiclePreviewPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (ToolgunAccessPolicy.rejectSurvivalFeature(
                    player,
                    SurvivalToolgunConfig.allowQuery(),
                    Component.translatable("screen.create_aeronautics_toolgun.mode.query")
            )) {
                PacketDistributor.sendToPlayer(player, new SyncQueryVehiclePreviewPayload(
                        payload.subLevelId(),
                        false,
                        "",
                        new byte[0],
                        "query disabled"
                ));
                return;
            }
            try {
                VehiclePreview preview = VehicleQueryService.createPreview(level, payload.subLevelId());
                PacketDistributor.sendToPlayer(player, new SyncQueryVehiclePreviewPayload(
                        payload.subLevelId(),
                        true,
                        preview.name(),
                        preview.blueprintBytes(),
                        ""
                ));
            } catch (Exception exception) {
                PacketDistributor.sendToPlayer(player, new SyncQueryVehiclePreviewPayload(
                        payload.subLevelId(),
                        false,
                        "",
                        new byte[0],
                        exception.getMessage() == null ? "preview failed" : exception.getMessage()
                ));
            }
        });
    }

    public static void handleAction(QueryVehicleActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (ToolgunAccessPolicy.rejectSurvivalFeature(
                    player,
                    SurvivalToolgunConfig.allowQuery(),
                    Component.translatable("screen.create_aeronautics_toolgun.mode.query")
            )) {
                return;
            }
            try {
                switch (payload.action()) {
                    case QueryVehicleActionPayload.ACTION_TELEPORT -> teleport(payload, level, player);
                    case QueryVehicleActionPayload.ACTION_TELEPORT_PLAYER_TO_VEHICLE -> {
                        VehicleQueryService.teleportPlayerAboveVehicle(level, player, payload.subLevelId());
                        player.sendSystemMessage(Component.translatable(
                                "message.create_aeronautics_toolgun.query_player_teleported"
                        ));
                    }
                    case QueryVehicleActionPayload.ACTION_RENAME -> {
                        String name = VehicleQueryService.rename(level, payload.subLevelId(), payload.name());
                        player.sendSystemMessage(Component.translatable(
                                "message.create_aeronautics_toolgun.query_renamed",
                                name
                        ));
                    }
                    case QueryVehicleActionPayload.ACTION_DELETE -> delete(payload, level, player);
                    case QueryVehicleActionPayload.ACTION_RECOVER -> recover(payload, level, player);
                    case QueryVehicleActionPayload.ACTION_CREATE_GHOST -> createGhost(payload, level, player);
                    default -> throw new IllegalArgumentException("unknown query action");
                }
            } catch (Exception exception) {
                ModConstants.LOGGER.warn(
                        "Vehicle query action {} failed for player {} and target {}",
                        payload.action(),
                        player.getGameProfile().getName(),
                        payload.subLevelId(),
                        exception
                );
                String failureKey = switch (payload.action()) {
                    case QueryVehicleActionPayload.ACTION_RECOVER ->
                            "message.create_aeronautics_toolgun.query_recovery_failed";
                    case QueryVehicleActionPayload.ACTION_CREATE_GHOST ->
                            "message.create_aeronautics_toolgun.query_ghost_failed";
                    default -> "message.create_aeronautics_toolgun.query_action_failed";
                };
                player.sendSystemMessage(Component.translatable(
                        failureKey,
                        FailureMessages.describe(exception, "vehicle query action failed")
                ));
            }
        });
    }

    private static void teleport(
            QueryVehicleActionPayload payload,
            ServerLevel level,
            ServerPlayer player
    ) throws Exception {
        if (!Double.isFinite(payload.x()) || !Double.isFinite(payload.y()) || !Double.isFinite(payload.z())) {
            throw new IllegalArgumentException("invalid coordinates");
        }
        VehicleQueryService.teleportVehicle(
                level,
                payload.subLevelId(),
                new Vector3d(payload.x(), payload.y(), payload.z())
        );
        player.sendSystemMessage(Component.translatable("message.create_aeronautics_toolgun.query_teleported"));
    }

    private static void delete(
            QueryVehicleActionPayload payload,
            ServerLevel level,
            ServerPlayer player
    ) {
        VehicleQueryService.requireLoadedVehicle(level, payload.subLevelId());
        if (ToolgunAccessPolicy.holdsSurvivalTool(player)) {
            player.sendSystemMessage(Component.translatable(
                    "message.create_aeronautics_toolgun.survival_tool_restricted",
                    Component.translatable("screen.create_aeronautics_toolgun.query.delete")
            ));
            return;
        }
        VehicleQueryService.delete(level, payload.subLevelId());
        player.sendSystemMessage(Component.translatable("message.create_aeronautics_toolgun.deleted"));
    }

    private static void recover(
            QueryVehicleActionPayload payload,
            ServerLevel level,
            ServerPlayer player
    ) throws Exception {
        requireCreativeStructureTool(player);
        var result = VehicleQueryService.recoverStoredVehicle(level, payload.subLevelId());
        player.sendSystemMessage(Component.translatable(
                "message.create_aeronautics_toolgun.query_recovered",
                result.subLevelCount()
        ));
    }

    private static void createGhost(
            QueryVehicleActionPayload payload,
            ServerLevel level,
            ServerPlayer player
    ) throws Exception {
        requireCreativeStructureTool(player);
        if (!player.hasPermissions(2)) {
            throw new IllegalArgumentException("operator permission level 2 is required");
        }
        VehicleQueryService.createGhostVehicle(level, payload.subLevelId());
        player.sendSystemMessage(Component.translatable(
                "message.create_aeronautics_toolgun.query_ghost_created"
        ));
    }

    private static void requireCreativeStructureTool(ServerPlayer player) {
        if (!ToolgunAccessPolicy.holdsCreativeTool(player)) {
            throw new IllegalArgumentException("creative structure tool required");
        }
    }

    private static SyncQueryVehiclesPayload.Entry toPayloadEntry(VehicleQueryEntry entry) {
        return new SyncQueryVehiclesPayload.Entry(
                entry.id(),
                entry.displayName(),
                entry.fullName(),
                entry.distance(),
                entry.position(),
                entry.loaded(),
                entry.broken()
        );
    }
}
