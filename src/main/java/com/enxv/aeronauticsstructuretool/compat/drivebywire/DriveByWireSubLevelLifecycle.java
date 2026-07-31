package com.enxv.aeronauticsstructuretool.compat.drivebywire;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import net.minecraft.server.level.ServerLevel;

import java.util.Set;
import java.util.UUID;

public final class DriveByWireSubLevelLifecycle {
    private final DriveByWireWorldSourceRefreshManager sourceRefreshManager;
    private final DriveByWireRestoreManager restoreManager;
    private final DriveByWireFullSyncManager fullSyncManager;

    public DriveByWireSubLevelLifecycle(
            DriveByWireWorldSourceRefreshManager sourceRefreshManager,
            DriveByWireRestoreManager restoreManager,
            DriveByWireFullSyncManager fullSyncManager
    ) {
        this.sourceRefreshManager = sourceRefreshManager;
        this.restoreManager = restoreManager;
        this.fullSyncManager = fullSyncManager;
    }

    public void beforeRemoval(ServerLevel level, Set<UUID> subLevelIds) {
        int forgottenSources = sourceRefreshManager.forgetSubLevels(level, subLevelIds);
        int cancelledConnections = restoreManager.cancelForSubLevels(level, subLevelIds);
        if (forgottenSources > 0 || cancelledConnections > 0) {
            AeronauticsStructureToolMod.LOGGER.debug(
                    "Cancelled AST DriveByWire helper state before removing {} sublevel(s): sources={} deferredConnections={}",
                    subLevelIds.size(),
                    forgottenSources,
                    cancelledConnections
            );
        }
    }

    public void afterRemoval(ServerLevel level) {
        if (DriveByWireApiBridge.isInstalled()) {
            fullSyncManager.scheduleOnce(level, "sublevel removal");
        }
    }
}
