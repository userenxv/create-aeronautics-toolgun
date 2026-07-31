package com.enxv.aeronauticsstructuretool.server;

import com.enxv.aeronauticsstructuretool.network.handler.ConstraintVisualPayloadHandler;
import com.enxv.aeronauticsstructuretool.toolgun.constraint.ConstraintVisualSnapshotService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class ConstraintVisualPublisher {
    private ConstraintVisualPublisher() {
    }

    public static void sync(ServerLevel level) {
        ConstraintVisualPayloadHandler.sync(level, ConstraintVisualSnapshotService.snapshots(level));
    }

    public static void syncTo(ServerPlayer player, ServerLevel level) {
        ConstraintVisualPayloadHandler.syncTo(player, ConstraintVisualSnapshotService.snapshots(level));
    }
}
