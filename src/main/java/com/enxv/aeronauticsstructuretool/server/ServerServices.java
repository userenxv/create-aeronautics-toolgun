package com.enxv.aeronauticsstructuretool.server;

import com.enxv.aeronauticsstructuretool.compat.drivebywire.DriveByWireFullSyncManager;
import com.enxv.aeronauticsstructuretool.compat.drivebywire.DriveByWireRestoreManager;
import com.enxv.aeronauticsstructuretool.compat.drivebywire.DriveByWireWorldSourceRefreshManager;
import com.enxv.aeronauticsstructuretool.compat.drivebywire.DriveByWireSubLevelLifecycle;
import com.enxv.aeronauticsstructuretool.compat.hardblock.HardBlockMissileCleanupManager;
import com.enxv.aeronauticsstructuretool.blueprint.runtime.RuntimeContraptionRestoreCoordinator;
import com.enxv.aeronauticsstructuretool.compat.synaxis.SynaxisControllerWireRestoreManager;
import com.enxv.aeronauticsstructuretool.toolgun.constraint.lifecycle.ConstraintLifecycleManager;
import com.enxv.aeronauticsstructuretool.toolgun.magnetic.MagneticGunServerService;
import com.enxv.aeronauticsstructuretool.network.transfer.BlueprintLoadUploadManager;
import net.neoforged.bus.api.IEventBus;

public final class ServerServices {
    public static final RuntimeContraptionRestoreCoordinator RUNTIME_CONTRAPTION_RESTORE =
            new RuntimeContraptionRestoreCoordinator();
    public static final MagneticGunServerService MAGNETIC_GUN = new MagneticGunServerService();
    public static final SubLevelCollisionToggleManager SUBLEVEL_COLLISION = new SubLevelCollisionToggleManager();
    public static final DriveByWireWorldSourceRefreshManager DRIVEBYWIRE_WORLD_SOURCE_REFRESH = new DriveByWireWorldSourceRefreshManager();
    public static final DriveByWireFullSyncManager DRIVEBYWIRE_FULL_SYNC = new DriveByWireFullSyncManager();
    public static final DriveByWireRestoreManager DRIVEBYWIRE_RESTORE =
            new DriveByWireRestoreManager(DRIVEBYWIRE_FULL_SYNC);
    public static final DriveByWireSubLevelLifecycle DRIVEBYWIRE_SUBLEVEL_LIFECYCLE =
            new DriveByWireSubLevelLifecycle(
                    DRIVEBYWIRE_WORLD_SOURCE_REFRESH,
                    DRIVEBYWIRE_RESTORE,
                    DRIVEBYWIRE_FULL_SYNC
            );
    public static final SynaxisControllerWireRestoreManager SYNAXIS_CONTROLLER_WIRE_RESTORE = new SynaxisControllerWireRestoreManager();
    public static final HardBlockMissileCleanupManager HARDBLOCK_MISSILE_CLEANUP = new HardBlockMissileCleanupManager();
    public static final ConstraintLifecycleManager CONSTRAINT_LIFECYCLE = new ConstraintLifecycleManager();
    public static final BlueprintLoadUploadManager BLUEPRINT_LOAD_UPLOAD = new BlueprintLoadUploadManager();

    private ServerServices() {
    }

    public static void register(IEventBus gameBus) {
        gameBus.register(RUNTIME_CONTRAPTION_RESTORE);
        gameBus.register(MAGNETIC_GUN);
        gameBus.register(SUBLEVEL_COLLISION);
        gameBus.register(DRIVEBYWIRE_WORLD_SOURCE_REFRESH);
        gameBus.register(DRIVEBYWIRE_FULL_SYNC);
        gameBus.register(DRIVEBYWIRE_RESTORE);
        gameBus.register(SYNAXIS_CONTROLLER_WIRE_RESTORE);
        gameBus.register(HARDBLOCK_MISSILE_CLEANUP);
        gameBus.register(CONSTRAINT_LIFECYCLE);
        gameBus.register(BLUEPRINT_LOAD_UPLOAD);
    }
}
