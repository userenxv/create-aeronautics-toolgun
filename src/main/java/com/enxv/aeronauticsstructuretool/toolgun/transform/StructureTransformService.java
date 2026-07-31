package com.enxv.aeronauticsstructuretool.toolgun.transform;

import com.enxv.aeronauticsstructuretool.RotationAxisMode;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;

import java.io.IOException;
import java.util.UUID;

public final class StructureTransformService {
    private StructureTransformService() {
    }

    public static void beginTranslation(
            ServerLevel level,
            UUID playerId,
            UUID subLevelId,
            Vector3d localPivot
    ) throws IOException {
        TranslationSessionManager.begin(level, playerId, subLevelId, localPivot);
    }

    public static void adjustTranslation(
            ServerLevel level,
            UUID playerId,
            RotationAxisMode axis,
            double distanceDelta
    ) throws IOException {
        TranslationSessionManager.adjust(level, playerId, axis, distanceDelta);
    }

    public static void finishTranslation(ServerLevel level, UUID playerId, boolean confirm) throws IOException {
        TranslationSessionManager.finish(level, playerId, confirm);
    }

    public static void beginRotation(
            ServerLevel level,
            UUID playerId,
            UUID subLevelId,
            Vector3d localPivot
    ) throws IOException {
        RotationSessionManager.begin(level, playerId, subLevelId, localPivot);
    }

    public static void adjustRotation(
            ServerLevel level,
            UUID playerId,
            RotationAxisMode axis,
            double degreesDelta
    ) throws IOException {
        RotationSessionManager.adjust(level, playerId, axis, degreesDelta);
    }

    public static void finishRotation(ServerLevel level, UUID playerId, boolean confirm) throws IOException {
        RotationSessionManager.finish(level, playerId, confirm);
    }

    public static void teleportToWorldPosition(
            ServerLevel level,
            UUID subLevelId,
            Vector3d desiredPosePosition
    ) throws IOException {
        TranslationSessionManager.teleportToWorldPosition(level, subLevelId, desiredPosePosition);
    }

    static void discardPlayer(ServerLevel level, UUID playerId) {
        TranslationSessionManager.discardPlayer(playerId);
        RotationSessionManager.discardPlayer(playerId);
    }

    static void discardDimension(ServerLevel level) {
        String dimensionId = level.dimension().location().toString();
        TranslationSessionManager.discardDimension(dimensionId);
        RotationSessionManager.discardDimension(dimensionId);
    }
}
