package com.enxv.aeronauticsstructuretool.toolgun.transform;

import com.enxv.aeronauticsstructuretool.RotationAxisMode;
import com.enxv.aeronauticsstructuretool.toolgun.constraint.ConstraintPoseTransaction;
import net.minecraft.server.level.ServerLevel;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static com.enxv.aeronauticsstructuretool.toolgun.ToolgunTransformValidation.requireFinite;

final class RotationSessionManager {
    private static final Map<UUID, RotationSession> SESSIONS = new LinkedHashMap<>();

    private RotationSessionManager() {
    }

    static void begin(
            ServerLevel level,
            UUID playerId,
            UUID subLevelId,
            Vector3d pivotLocalPoint
    ) throws IOException {
        if (playerId == null) {
            throw new IOException("missing rotation player");
        }
        requireFinite(pivotLocalPoint, "rotation pivot");
        SESSIONS.remove(playerId);
        ConstraintPoseTransaction.requireSubLevel(level, subLevelId);
        SESSIONS.put(playerId, new RotationSession(
                dimensionId(level),
                subLevelId,
                new Vector3d(pivotLocalPoint),
                new Quaterniond()
        ));
    }

    static void adjust(
            ServerLevel level,
            UUID playerId,
            RotationAxisMode axisMode,
            double degreesDelta
    ) throws IOException {
        if (axisMode == null) {
            throw new IOException("rotation adjustment is invalid");
        }
        requireFinite(degreesDelta, "rotation adjustment");
        RotationSession session = requireSession(level, playerId);
        Quaterniond delta = createDeltaRotation(axisMode, degreesDelta);
        requireFinite(delta, "rotation adjustment");
        session.pendingLocalRotation().mul(delta).normalize();
        requireFinite(session.pendingLocalRotation(), "accumulated rotation");
    }

    static void finish(ServerLevel level, UUID playerId, boolean confirm) throws IOException {
        RotationSession session = SESSIONS.remove(playerId);
        if (session == null || !session.dimensionId().equals(dimensionId(level))) {
            return;
        }
        if (!confirm) {
            return;
        }
        ConstraintPoseTransaction.apply(level, session.subLevelId(), subLevel ->
                SubLevelPoseOperations.rotateAroundLocalPoint(
                        level,
                        subLevel,
                        session.pivotLocalPoint(),
                        session.pendingLocalRotation()
                )
        );
    }

    static void discardPlayer(UUID playerId) {
        SESSIONS.remove(playerId);
    }

    static void discardDimension(String dimensionId) {
        Iterator<RotationSession> iterator = SESSIONS.values().iterator();
        while (iterator.hasNext()) {
            if (dimensionId.equals(iterator.next().dimensionId())) {
                iterator.remove();
            }
        }
    }

    private static Quaterniond createDeltaRotation(RotationAxisMode axisMode, double degreesDelta) {
        if (Math.abs(degreesDelta) <= 1.0E-6D) {
            return new Quaterniond();
        }
        Vector3d localAxis = axisMode.localAxis();
        return new Quaterniond().fromAxisAngleRad(
                localAxis.x,
                localAxis.y,
                localAxis.z,
                Math.toRadians(degreesDelta)
        );
    }

    private static RotationSession requireSession(ServerLevel level, UUID playerId) throws IOException {
        RotationSession session = SESSIONS.get(playerId);
        if (session == null || !session.dimensionId().equals(dimensionId(level))) {
            throw new IOException("no active rotation session");
        }
        return session;
    }

    private static String dimensionId(ServerLevel level) throws IOException {
        if (level == null) {
            throw new IOException("missing rotation level");
        }
        return level.dimension().location().toString();
    }

    private record RotationSession(
            String dimensionId,
            UUID subLevelId,
            Vector3d pivotLocalPoint,
            Quaterniond pendingLocalRotation
    ) {
    }
}
