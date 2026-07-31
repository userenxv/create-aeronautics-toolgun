package com.enxv.aeronauticsstructuretool.toolgun.weld;

import com.enxv.aeronauticsstructuretool.ConnectionMode;
import com.enxv.aeronauticsstructuretool.SurvivalToolgunConfig;
import com.enxv.aeronauticsstructuretool.toolgun.ToolgunAccessPolicy;
import com.enxv.aeronauticsstructuretool.toolgun.constraint.ConstraintPoseTransaction;
import com.enxv.aeronauticsstructuretool.toolgun.constraint.ConstraintRuntimeFactory;
import com.enxv.aeronauticsstructuretool.toolgun.transform.SubLevelPoseOperations;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.io.IOException;

import static com.enxv.aeronauticsstructuretool.toolgun.ToolgunTransformValidation.requireFinite;

public final class WeldToolService {
    private WeldToolService() {
    }

    public static void weld(ServerLevel level, WeldRequest request) throws IOException {
        if (request == null
                || request.firstSubLevelId() == null
                || request.secondSubLevelId() == null
                || request.firstFace() == null
                || request.secondFace() == null
                || request.bearingAxisMode() == null
                || request.connectionMode() == null) {
            throw new IOException("weld request is incomplete");
        }
        requireFinite(request.firstPoint(), "first weld point");
        requireFinite(request.adjustedSecondPoint(), "second weld point");
        requireFinite(request.secondLocalPoint(), "second weld local point");

        ServerSubLevelContainer container = requireContainer(level);
        SubLevel firstSubLevel = container.getSubLevel(request.firstSubLevelId());
        SubLevel secondSubLevel = container.getSubLevel(request.secondSubLevelId());
        if (!(firstSubLevel instanceof ServerSubLevel first)
                || !(secondSubLevel instanceof ServerSubLevel second)) {
            throw new IOException("weld target is not a loaded physical structure");
        }
        if (first.getUniqueId().equals(second.getUniqueId())) {
            throw new IOException("cannot weld the same structure");
        }

        Vector3d weldPoint = new Vector3d(request.firstPoint())
                .add(request.adjustedSecondPoint())
                .mul(0.5D);
        Vector3d worldAxis = request.connectionMode() == ConnectionMode.BEARING
                ? request.bearingAxisMode().resolveWorldAxis(
                        first,
                        request.firstFace(),
                        second,
                        request.secondFace()
                )
                : null;
        ConstraintPoseTransaction.apply(
                level,
                request.secondSubLevelId(),
                moved -> SubLevelPoseOperations.movePointToWorld(
                        level,
                        moved,
                        request.secondLocalPoint(),
                        request.adjustedSecondPoint()
                ),
                moved -> ConstraintRuntimeFactory.weldAtWorldPoint(
                        level,
                        first,
                        moved,
                        weldPoint,
                        request.firstPoint(),
                        request.adjustedSecondPoint(),
                        worldAxis,
                        request.connectionMode()
                )
        );
    }

    public static MovePointResult movePoint(
            ServerPlayer player,
            ServerLevel level,
            MoveSubLevelPointRequest request
    ) throws IOException {
        if (player == null || request == null || request.subLevelId() == null) {
            throw new IOException("move-point request is incomplete");
        }
        requireFinite(request.localPoint(), "move-point local point");
        requireFinite(request.targetPoint(), "move-point target");

        ServerSubLevelContainer container = requireContainer(level);
        if (!(container.getSubLevel(request.subLevelId()) instanceof ServerSubLevel subLevel)) {
            return MovePointResult.TARGET_UNAVAILABLE;
        }
        if (ToolgunAccessPolicy.holdsSurvivalTool(player)) {
            if (SurvivalToolgunConfig.allowWeld()) {
                PendingWeldMoveTracker.remember(
                        player.getUUID(),
                        request.subLevelId(),
                        request.localPoint(),
                        subLevel
                );
            } else if (!PendingWeldMoveTracker.isRestore(
                    player.getUUID(),
                    request.subLevelId(),
                    request.localPoint(),
                    request.targetPoint()
            )) {
                return MovePointResult.RESTRICTED;
            }
        }
        SubLevelPoseOperations.movePointToWorld(
                level,
                subLevel,
                request.localPoint(),
                request.targetPoint()
        );
        PendingWeldMoveTracker.clearIfRestored(
                player.getUUID(),
                request.subLevelId(),
                request.localPoint(),
                request.targetPoint()
        );
        return MovePointResult.MOVED;
    }

    public static void simpleWeld(ServerLevel level, SimpleWeldRequest request) throws IOException {
        if (request == null
                || request.childSubLevelId() == null
                || request.parentSubLevelId() == null) {
            throw new IOException("simple-weld request is incomplete");
        }
        requireFinite(request.childLocalPoint(), "simple-weld child point");
        requireFinite(request.parentLocalPoint(), "simple-weld parent point");
        requireFinite(request.parentOffset(), "simple-weld parent offset");
        requireFinite(request.relativeRotation(), "simple-weld relative rotation");

        ServerSubLevel child = ConstraintPoseTransaction.requireSubLevel(level, request.childSubLevelId());
        ServerSubLevel parent = ConstraintPoseTransaction.requireSubLevel(level, request.parentSubLevelId());
        if (child.getUniqueId().equals(parent.getUniqueId())) {
            throw new IOException("cannot weld the same structure");
        }

        Quaterniond parentRelativeRotation = new Quaterniond(request.relativeRotation()).normalize();
        requireFinite(parentRelativeRotation, "normalized simple-weld relative rotation");
        Quaterniond targetOrientation = new Quaterniond(parent.logicalPose().orientation())
                .mul(parentRelativeRotation)
                .normalize();
        requireFinite(targetOrientation, "simple-weld target orientation");

        Vector3d parentPointWorld = parent.logicalPose().transformPosition(
                new Vector3d(request.parentLocalPoint()),
                new Vector3d()
        );
        Vector3d worldOffset = new Vector3d(request.parentOffset());
        parent.logicalPose().orientation().transform(worldOffset);
        Vector3d targetPointWorld = parentPointWorld.add(worldOffset, new Vector3d());
        requireFinite(targetPointWorld, "simple-weld target point");

        ConstraintPoseTransaction.apply(
                level,
                request.childSubLevelId(),
                movedChild -> {
                    Quaterniond rotationDelta = new Quaterniond(movedChild.logicalPose().orientation())
                            .conjugate()
                            .mul(new Quaterniond(targetOrientation))
                            .normalize();
                    SubLevelPoseOperations.rotateAroundLocalPoint(
                            level,
                            movedChild,
                            new Vector3d(request.childLocalPoint()),
                            rotationDelta
                    );
                    SubLevelPoseOperations.movePointToWorld(
                            level,
                            movedChild,
                            new Vector3d(request.childLocalPoint()),
                            targetPointWorld
                    );
                },
                movedChild -> {
                    Vector3d childPointWorld = movedChild.logicalPose().transformPosition(
                            new Vector3d(request.childLocalPoint()),
                            new Vector3d()
                    );
                    Vector3d weldPoint = childPointWorld.add(targetPointWorld, new Vector3d()).mul(0.5D);
                    ConstraintRuntimeFactory.weldAtWorldPoint(
                            level,
                            parent,
                            movedChild,
                            weldPoint,
                            targetPointWorld,
                            childPointWorld,
                            null,
                            ConnectionMode.FIXED
                    );
                }
        );
    }

    private static ServerSubLevelContainer requireContainer(ServerLevel level) throws IOException {
        if (level == null) {
            throw new IOException("missing weld level");
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            throw new IOException("sublevel container unavailable in " + level.dimension().location());
        }
        return container;
    }

    public enum MovePointResult {
        MOVED,
        RESTRICTED,
        TARGET_UNAVAILABLE
    }
}
