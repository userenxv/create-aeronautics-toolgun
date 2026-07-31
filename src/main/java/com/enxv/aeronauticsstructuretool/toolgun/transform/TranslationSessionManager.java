package com.enxv.aeronauticsstructuretool.toolgun.transform;

import com.enxv.aeronauticsstructuretool.RotationAxisMode;
import com.enxv.aeronauticsstructuretool.toolgun.constraint.ConstraintPoseTransaction;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static com.enxv.aeronauticsstructuretool.toolgun.ToolgunTransformValidation.requireFinite;

final class TranslationSessionManager {
    private static final Map<UUID, TranslationSession> SESSIONS = new LinkedHashMap<>();

    private TranslationSessionManager() {
    }

    static void begin(
            ServerLevel level,
            UUID playerId,
            UUID subLevelId,
            Vector3d pivotLocalPoint
    ) throws IOException {
        if (playerId == null) {
            throw new IOException("missing translation player");
        }
        requireFinite(pivotLocalPoint, "translation pivot");
        SESSIONS.remove(playerId);
        ConstraintPoseTransaction.requireSubLevel(level, subLevelId);
        SESSIONS.put(playerId, new TranslationSession(
                dimensionId(level),
                subLevelId,
                new Vector3d(pivotLocalPoint),
                new Vector3d()
        ));
    }

    static void adjust(
            ServerLevel level,
            UUID playerId,
            RotationAxisMode axisMode,
            double distanceDelta
    ) throws IOException {
        if (axisMode == null) {
            throw new IOException("translation adjustment is invalid");
        }
        requireFinite(distanceDelta, "translation adjustment");
        TranslationSession session = requireSession(level, playerId);
        Vector3d delta = axisMode.localAxis().mul(distanceDelta, new Vector3d());
        requireFinite(delta, "translation adjustment");
        session.pendingLocalOffset().add(delta);
        requireFinite(session.pendingLocalOffset(), "accumulated translation");
    }

    static void finish(ServerLevel level, UUID playerId, boolean confirm) throws IOException {
        TranslationSession session = SESSIONS.remove(playerId);
        if (session == null || !session.dimensionId().equals(dimensionId(level))) {
            return;
        }
        if (!confirm) {
            return;
        }

        ConstraintPoseTransaction.apply(level, session.subLevelId(), subLevel -> {
            Vector3d currentPivotWorld = subLevel.logicalPose().transformPosition(
                    new Vector3d(session.pivotLocalPoint()),
                    new Vector3d()
            );
            Vector3d worldOffset = new Vector3d(session.pendingLocalOffset());
            subLevel.logicalPose().orientation().transform(worldOffset);
            Vector3d desiredPivotWorld = currentPivotWorld.add(worldOffset, new Vector3d());
            SubLevelPoseOperations.movePointToWorld(
                    level,
                    subLevel,
                    session.pivotLocalPoint(),
                    desiredPivotWorld
            );
        });
    }

    static void teleportToWorldPosition(
            ServerLevel level,
            UUID subLevelId,
            Vector3d desiredPosePosition
    ) throws IOException {
        requireFinite(desiredPosePosition, "translation target");
        ConstraintPoseTransaction.apply(level, subLevelId, subLevel ->
                SubLevelPoseOperations.movePointToWorld(
                        level,
                        subLevel,
                        new Vector3d(subLevel.logicalPose().rotationPoint()),
                        new Vector3d(desiredPosePosition)
                )
        );
    }

    static void discardPlayer(UUID playerId) {
        SESSIONS.remove(playerId);
    }

    static void discardDimension(String dimensionId) {
        Iterator<TranslationSession> iterator = SESSIONS.values().iterator();
        while (iterator.hasNext()) {
            if (dimensionId.equals(iterator.next().dimensionId())) {
                iterator.remove();
            }
        }
    }

    private static TranslationSession requireSession(ServerLevel level, UUID playerId) throws IOException {
        TranslationSession session = SESSIONS.get(playerId);
        if (session == null || !session.dimensionId().equals(dimensionId(level))) {
            throw new IOException("no active translation session");
        }
        return session;
    }

    private static String dimensionId(ServerLevel level) throws IOException {
        if (level == null) {
            throw new IOException("missing translation level");
        }
        return level.dimension().location().toString();
    }

    private record TranslationSession(
            String dimensionId,
            UUID subLevelId,
            Vector3d pivotLocalPoint,
            Vector3d pendingLocalOffset
    ) {
    }
}
