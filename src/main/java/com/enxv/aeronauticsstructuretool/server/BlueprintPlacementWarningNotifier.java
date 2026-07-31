package com.enxv.aeronauticsstructuretool.server;

import com.enxv.aeronauticsstructuretool.blueprint.placement.BlueprintPlacementDiagnostics;
import com.enxv.aeronauticsstructuretool.blueprint.security.MissingRegistryContentSanitizer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

public final class BlueprintPlacementWarningNotifier {
    private BlueprintPlacementWarningNotifier() {
    }

    public static void notify(
            ServerLevel level,
            UUID playerId,
            List<BlueprintPlacementDiagnostics.Warning> warnings
    ) {
        if (playerId == null || warnings == null || warnings.isEmpty()) {
            return;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
        if (player != null) {
            for (BlueprintPlacementDiagnostics.Warning warning : warnings) {
                player.sendSystemMessage(Component.translatable(
                        "message.create_aeronautics_toolgun.blueprint_warning",
                        warning.message()
                ));
            }
        }
    }

    public static void notify(ServerLevel level, UUID playerId, String feature, String detail) {
        notify(level, playerId, List.of(new BlueprintPlacementDiagnostics.Warning(feature, detail)));
    }

    public static void notifySkippedContent(
            ServerLevel level,
            UUID playerId,
            MissingRegistryContentSanitizer.Result skipped
    ) {
        if (skipped == null || skipped.isEmpty()) {
            return;
        }
        for (var entry : skipped.skippedBlocks().entrySet()) {
            send(level, playerId, Component.translatable(
                    "message.create_aeronautics_toolgun.blueprint_skipped_block",
                    entry.getKey(),
                    entry.getValue()
            ));
        }
        for (var entry : skipped.skippedBlockEntities().entrySet()) {
            send(level, playerId, Component.translatable(
                    "message.create_aeronautics_toolgun.blueprint_skipped_block_entity",
                    entry.getKey(),
                    entry.getValue()
            ));
        }
        for (var entry : skipped.replacedBiomes().entrySet()) {
            send(level, playerId, Component.translatable(
                    "message.create_aeronautics_toolgun.blueprint_replaced_biome",
                    entry.getKey(),
                    entry.getValue(),
                    "minecraft:plains"
            ));
        }
    }

    public static void notifySkippedDriveByWire(
            ServerLevel level,
            UUID playerId,
            int snapshotCount,
            int connectionCount
    ) {
        if (snapshotCount <= 0) {
            return;
        }
        send(level, playerId, Component.translatable(
                "message.create_aeronautics_toolgun.blueprint_skipped_drivebywire",
                snapshotCount,
                connectionCount
        ));
    }

    public static void notifySkippedRuntimeEntity(
            ServerLevel level,
            UUID playerId,
            String entityName,
            BlockPos controllerPos,
            String reason
    ) {
        send(level, playerId, Component.translatable(
                "message.create_aeronautics_toolgun.blueprint_skipped_runtime_entity",
                entityName,
                controllerPos.toShortString(),
                reason
        ));
    }

    private static void send(ServerLevel level, UUID playerId, Component message) {
        if (playerId == null) {
            return;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
        if (player != null) {
            player.sendSystemMessage(message);
        }
    }
}
