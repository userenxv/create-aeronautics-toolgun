package com.enxv.aeronauticsstructuretool.toolgun.weld;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PendingWeldMoveTracker {
    private static final double RESTORE_EPSILON = 1.0E-4D;
    private static final Map<MoveKey, Vector3d> ORIGINAL_POSITIONS = new HashMap<>();

    private PendingWeldMoveTracker() {
    }

    public static void remember(
            UUID playerId,
            UUID subLevelId,
            Vector3d localPoint,
            ServerSubLevel subLevel
    ) {
        MoveKey key = new MoveKey(playerId, subLevelId, localPoint.x, localPoint.y, localPoint.z);
        ORIGINAL_POSITIONS.computeIfAbsent(key, ignored -> subLevel.logicalPose().transformPosition(
                localPoint,
                new Vector3d()
        ));
    }

    public static boolean isRestore(
            UUID playerId,
            UUID subLevelId,
            Vector3d localPoint,
            Vector3d target
    ) {
        Vector3d original = ORIGINAL_POSITIONS.get(new MoveKey(
                playerId,
                subLevelId,
                localPoint.x,
                localPoint.y,
                localPoint.z
        ));
        return original != null && original.distance(target) <= RESTORE_EPSILON;
    }

    public static void clearIfRestored(
            UUID playerId,
            UUID subLevelId,
            Vector3d localPoint,
            Vector3d target
    ) {
        MoveKey key = new MoveKey(playerId, subLevelId, localPoint.x, localPoint.y, localPoint.z);
        Vector3d original = ORIGINAL_POSITIONS.get(key);
        if (original != null && original.distance(target) <= RESTORE_EPSILON) {
            ORIGINAL_POSITIONS.remove(key);
        }
    }

    private record MoveKey(
            UUID playerId,
            UUID subLevelId,
            double localX,
            double localY,
            double localZ
    ) {
    }
}
