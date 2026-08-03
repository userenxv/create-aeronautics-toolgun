package com.enxv.aeronauticsstructuretool.toolgun.transform;

import com.enxv.aeronauticsstructuretool.RotationAxisMode;
import com.enxv.aeronauticsstructuretool.toolgun.constraint.ConstraintPoseTransaction;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.io.IOException;
import java.util.*;

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
        ServerSubLevelContainer serverSubLevelContainer = ServerSubLevelContainer.getContainer(level);
        if (serverSubLevelContainer == null) return;
        SubLevel selectedSubLevel = serverSubLevelContainer.getSubLevel(session.subLevelId());
        if (selectedSubLevel == null) return;
        Collection<SubLevel> connectedSubLevels = SubLevelHelper.getConnectedChain(selectedSubLevel);

        Vector3d pivotWorldPoint = selectedSubLevel.logicalPose().transformPosition(session.pivotLocalPoint(), new Vector3d());
        Quaterniond selectedOrientation = new Quaterniond(selectedSubLevel.logicalPose().orientation());
        Quaterniond pendingWorldRotation = new Quaterniond(selectedOrientation).mul(session.pendingLocalRotation()).normalize();
        Quaterniond worldRotationDelta = new Quaterniond(pendingWorldRotation).mul(new Quaterniond(selectedOrientation).conjugate()).normalize();

        for (SubLevel connectedSubLevel : connectedSubLevels) {
            UUID subLevelId = connectedSubLevel.getUniqueId();
            ConstraintPoseTransaction.apply(level, subLevelId, subLevel -> {
                        Quaterniond currentOrientation = new Quaterniond(subLevel.logicalPose().orientation());
                        Quaterniond localRotation = new Quaterniond(currentOrientation).conjugate()
                                .mul(worldRotationDelta)
                                .mul(currentOrientation)
                                .normalize();
                        SubLevelPoseOperations.rotateAroundLocalPoint(
                                level,
                                subLevel,
                                subLevel.logicalPose().transformPositionInverse(pivotWorldPoint, new Vector3d()),
                                localRotation
                        );
                    }
            );
        }
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
