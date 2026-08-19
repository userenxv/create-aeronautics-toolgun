package com.enxv.aeronauticsstructuretool.toolgun.transform;

import com.enxv.aeronauticsstructuretool.RotationAxisMode;
import com.enxv.aeronauticsstructuretool.ToolgunConstraintTracker;
import com.enxv.aeronauticsstructuretool.toolgun.constraint.ConstraintPoseTransaction;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;

import java.io.IOException;
import java.util.*;

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
        SubLevelContainer subLevelContainer = SubLevelContainer.getContainer(level);
        if (subLevelContainer == null) return;
        Vector3d worldOffset = new Vector3d(session.pendingLocalOffset());
        Collection<SubLevel> connectedSubLevels = SubLevelHelper.getConnectedChain(subLevelContainer.getSubLevel(session.subLevelId()));
        ((SubLevel) (connectedSubLevels.toArray())[0]).logicalPose().orientation().transform(worldOffset);
        for (SubLevel connectedSubLevel : connectedSubLevels) {
            UUID subLevelId = connectedSubLevel.getUniqueId();
            ConstraintPoseTransaction.apply(level, subLevelId, subLevel -> {
                Vector3d currentPivotWorld = subLevel.logicalPose().transformPosition(
                        new Vector3d(session.pivotLocalPoint()),
                        new Vector3d()
                );

                Vector3d desiredPivotWorld = currentPivotWorld.add(worldOffset, new Vector3d());
                SubLevelPoseOperations.movePointToWorld(
                        level,
                        subLevel,
                        session.pivotLocalPoint(),
                        desiredPivotWorld
                );
            });
        }
    }

    static void teleportToWorldPosition(
            ServerLevel level,
            UUID subLevelId,
            Vector3d desiredPosePosition
    ) throws IOException {
        requireFinite(desiredPosePosition, "translation target");
        boolean resetVelocity = ToolgunConstraintTracker.getConstraintsForSubLevel(subLevelId).isEmpty();
        ConstraintPoseTransaction.apply(level, subLevelId, subLevel ->
                SubLevelPoseOperations.teleportToWorldPosition(
                        level,
                        subLevel,
                        new Vector3d(desiredPosePosition),
                        resetVelocity
                )
        );
    }

    static void discardPlayer(UUID playerId) {
        SESSIONS.remove(playerId);
    }

    static void discardDimension(String dimensionId) {
        SESSIONS.values().removeIf(translationSession -> dimensionId.equals(translationSession.dimensionId()));
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
