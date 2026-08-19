package com.enxv.aeronauticsstructuretool.toolgun.transform;

import com.enxv.aeronauticsstructuretool.compat.sable.SablePhysicsPipelineAccess;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.io.IOException;

import static com.enxv.aeronauticsstructuretool.toolgun.ToolgunTransformValidation.requireFinite;

public final class SubLevelPoseOperations {
    private SubLevelPoseOperations() {
    }

    public static void movePointToWorld(
            ServerLevel level,
            ServerSubLevel subLevel,
            Vector3d localPoint,
            Vector3d desiredWorldPoint
    ) throws IOException {
        if (subLevel == null || localPoint == null || desiredWorldPoint == null) {
            throw new IOException("missing sublevel alignment target");
        }
        requireFinite(localPoint, "sublevel local alignment point");
        requireFinite(desiredWorldPoint, "sublevel world alignment point");

        Vector3d currentWorldPoint = subLevel.logicalPose().transformPosition(localPoint, new Vector3d());
        Vector3d translation = desiredWorldPoint.sub(currentWorldPoint, new Vector3d());
        requireFinite(currentWorldPoint, "current sublevel alignment point");
        requireFinite(translation, "sublevel translation");
        if (translation.lengthSquared() <= 1.0E-8D) {
            return;
        }

        subLevel.logicalPose().position().add(translation);
        requireFinite(subLevel.logicalPose().position(), "translated sublevel position");
        synchronize(level, subLevel);
    }

    public static void teleportToWorldPosition(
            ServerLevel level,
            ServerSubLevel subLevel,
            Vector3d destination,
            boolean resetVelocity
    ) throws IOException {
        if (subLevel == null || destination == null) {
            throw new IOException("missing sublevel teleport target");
        }
        requireFinite(destination, "sublevel teleport destination");
        try {
            PhysicsPipeline pipeline = SablePhysicsPipelineAccess.require(level);
            if (resetVelocity) {
                pipeline.resetVelocity(subLevel);
            }
            pipeline.teleport(
                    subLevel,
                    new Vector3d(destination),
                    new Quaterniond(subLevel.logicalPose().orientation())
            );
        } catch (RuntimeException exception) {
            throw new IOException("failed to teleport sublevel " + subLevel.getUniqueId(), exception);
        }
    }

    public static void resizeAtCenter(
            ServerLevel level,
            ServerSubLevel subLevel,
            double scaleFactor
    ) throws IOException {
        if (subLevel == null) {
            throw new IOException("missing sublevel");
        }
        requireFinite(scaleFactor, "sublevel scale");
        if (!(scaleFactor > 0.0D)) {
            throw new IOException("invalid scale");
        }

        BlockPos centerBlock = subLevel.getPlot().getCenterBlock();
        Vector3d centerBefore = subLevel.logicalPose().transformPosition(new Vector3d(
                centerBlock.getX(),
                centerBlock.getY(),
                centerBlock.getZ()
        ));
        subLevel.logicalPose().scale().set(scaleFactor, scaleFactor, scaleFactor);
        Vector3d centerAfter = subLevel.logicalPose().transformPosition(new Vector3d(
                centerBlock.getX(),
                centerBlock.getY(),
                centerBlock.getZ()
        ));
        subLevel.logicalPose().position().add(centerBefore.sub(centerAfter));
        requireFinite(subLevel.logicalPose().position(), "resized sublevel position");
        synchronize(level, subLevel);
    }

    public static void rotateAroundLocalPoint(
            ServerLevel level,
            ServerSubLevel subLevel,
            Vector3d localPivot,
            Vector3d localAxis,
            double degreesDelta
    ) throws IOException {
        if (subLevel == null || localPivot == null || localAxis == null) {
            throw new IOException("missing sublevel rotation target");
        }
        requireFinite(localPivot, "sublevel rotation pivot");
        requireFinite(localAxis, "sublevel rotation axis");
        requireFinite(degreesDelta, "sublevel rotation delta");
        if (Math.abs(degreesDelta) <= 1.0E-6D) {
            return;
        }
        if (localAxis.lengthSquared() <= 1.0E-6D) {
            throw new IOException("sublevel rotation axis is zero");
        }
        rotateAroundLocalPoint(
                level,
                subLevel,
                localPivot,
                new Quaterniond().fromAxisAngleRad(
                        localAxis.x,
                        localAxis.y,
                        localAxis.z,
                        Math.toRadians(degreesDelta)
                )
        );
    }

    public static void rotateAroundLocalPoint(
            ServerLevel level,
            ServerSubLevel subLevel,
            Vector3d localPivot,
            Quaterniond localRotationDelta
    ) throws IOException {
        if (subLevel == null || localPivot == null || localRotationDelta == null) {
            throw new IOException("missing sublevel rotation target");
        }
        requireFinite(localPivot, "sublevel rotation pivot");
        requireFinite(localRotationDelta, "sublevel rotation delta");
        Quaterniond normalizedDelta = new Quaterniond(localRotationDelta).normalize();
        if (Math.abs(normalizedDelta.x) <= 1.0E-8D
                && Math.abs(normalizedDelta.y) <= 1.0E-8D
                && Math.abs(normalizedDelta.z) <= 1.0E-8D
                && Math.abs(normalizedDelta.w - 1.0D) <= 1.0E-8D) {
            return;
        }

        Vector3d pivotBefore = subLevel.logicalPose().transformPosition(
                new Vector3d(localPivot),
                new Vector3d()
        );
        subLevel.logicalPose().orientation().mul(normalizedDelta).normalize();
        Vector3d pivotAfter = subLevel.logicalPose().transformPosition(
                new Vector3d(localPivot),
                new Vector3d()
        );
        subLevel.logicalPose().position().add(pivotBefore.sub(pivotAfter, new Vector3d()));
        requireFinite(subLevel.logicalPose().position(), "rotated sublevel position");
        requireFinite(subLevel.logicalPose().orientation(), "rotated sublevel orientation");
        synchronize(level, subLevel);
    }

    public static void synchronize(ServerLevel level, ServerSubLevel subLevel) throws IOException {
        if (subLevel == null) {
            throw new IOException("missing sublevel pose target");
        }
        requireFinite(subLevel.logicalPose().position(), "sublevel pose position");
        requireFinite(subLevel.logicalPose().orientation(), "sublevel pose orientation");
        try {
            PhysicsPipeline pipeline = SablePhysicsPipelineAccess.require(level);
            pipeline.teleport(
                    subLevel,
                    subLevel.logicalPose().position(),
                    subLevel.logicalPose().orientation()
            );
            pipeline.wakeUp(subLevel);
            subLevel.updateLastPose();
        } catch (RuntimeException exception) {
            throw new IOException("failed to synchronize sublevel pose " + subLevel.getUniqueId(), exception);
        }
    }
}
