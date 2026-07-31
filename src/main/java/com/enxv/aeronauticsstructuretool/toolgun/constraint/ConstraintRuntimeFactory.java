package com.enxv.aeronauticsstructuretool.toolgun.constraint;

import com.enxv.aeronauticsstructuretool.ConnectionMode;
import com.enxv.aeronauticsstructuretool.ToolgunConstraintTracker;
import com.enxv.aeronauticsstructuretool.compat.sable.SableConstraintApiBridge;
import com.enxv.aeronauticsstructuretool.compat.sable.SablePhysicsPipelineAccess;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.io.IOException;
import java.util.UUID;

import static com.enxv.aeronauticsstructuretool.toolgun.ToolgunTransformValidation.requireAxis;
import static com.enxv.aeronauticsstructuretool.toolgun.ToolgunTransformValidation.requireFinite;
import static com.enxv.aeronauticsstructuretool.toolgun.ToolgunTransformValidation.requireFiniteOptional;

public final class ConstraintRuntimeFactory {
    private ConstraintRuntimeFactory() {
    }

    public static void weldAtWorldPoint(
            ServerLevel level,
            ServerSubLevel first,
            ServerSubLevel second,
            Vector3d worldPoint,
            Vector3d firstDisplayWorldPoint,
            Vector3d secondDisplayWorldPoint,
            Vector3d worldAxis,
            ConnectionMode connectionMode
    ) throws IOException {
        ToolgunConstraintTracker.register(level, createTrackedConstraint(
                level,
                first,
                second,
                worldPoint,
                firstDisplayWorldPoint,
                secondDisplayWorldPoint,
                worldAxis,
                connectionMode,
                UUID.randomUUID()
        ));
    }

    public static TrackedConstraint createTrackedConstraint(
            ServerLevel level,
            ServerSubLevel first,
            ServerSubLevel second,
            Vector3d worldPoint,
            Vector3d firstDisplayWorldPoint,
            Vector3d secondDisplayWorldPoint,
            Vector3d worldAxis,
            ConnectionMode connectionMode,
            UUID constraintId
    ) throws IOException {
        validateEndpoints(first, second, connectionMode, constraintId, "create");
        requireFinite(worldPoint, "constraint world point");
        requireFiniteOptional(firstDisplayWorldPoint, "first constraint display point");
        requireFiniteOptional(secondDisplayWorldPoint, "second constraint display point");

        Vector3d firstLocal = first.logicalPose().transformPositionInverse(worldPoint, new Vector3d());
        Vector3d secondLocal = second.logicalPose().transformPositionInverse(worldPoint, new Vector3d());
        Vector3d firstDisplayLocal = firstDisplayWorldPoint != null
                ? first.logicalPose().transformPositionInverse(firstDisplayWorldPoint, new Vector3d())
                : new Vector3d(firstLocal);
        Vector3d secondDisplayLocal = secondDisplayWorldPoint != null
                ? second.logicalPose().transformPositionInverse(secondDisplayWorldPoint, new Vector3d())
                : new Vector3d(secondLocal);
        Quaterniond relativeOrientation = new Quaterniond(first.logicalPose().orientation())
                .conjugate()
                .mul(second.logicalPose().orientation())
                .normalize();
        requireFinite(firstLocal, "first constraint local point");
        requireFinite(secondLocal, "second constraint local point");
        requireFinite(firstDisplayLocal, "first constraint display-local point");
        requireFinite(secondDisplayLocal, "second constraint display-local point");
        requireFinite(relativeOrientation, "constraint relative orientation");
        Vector3d axis = connectionMode == ConnectionMode.BEARING
                ? requireAxis(worldAxis, "bearing world axis")
                : null;

        PhysicsPipeline pipeline = SablePhysicsPipelineAccess.require(level);
        PhysicsConstraintHandle handle;
        Vector3d firstAxisLocal = null;
        Vector3d secondAxisLocal = null;
        switch (connectionMode) {
            case FIXED -> handle = SableConstraintApiBridge.addFixed(
                    pipeline,
                    first,
                    second,
                    firstLocal,
                    secondLocal,
                    relativeOrientation
            );
            case FREE -> handle = SableConstraintApiBridge.addGeneric(
                    pipeline,
                    first,
                    second,
                    firstLocal,
                    secondLocal,
                    relativeOrientation
            );
            case BEARING -> {
                firstAxisLocal = transformDirectionInverse(first, axis);
                secondAxisLocal = transformDirectionInverse(second, axis);
                handle = SableConstraintApiBridge.addRotary(
                        pipeline,
                        first,
                        second,
                        firstLocal,
                        secondLocal,
                        firstAxisLocal,
                        secondAxisLocal
                );
            }
            default -> throw new IOException("unsupported connection mode");
        }
        requireValidHandle(handle);
        pipeline.wakeUp(first);
        pipeline.wakeUp(second);
        return trackedConstraint(
                level,
                first,
                second,
                firstLocal,
                secondLocal,
                firstDisplayLocal,
                secondDisplayLocal,
                relativeOrientation,
                firstAxisLocal,
                secondAxisLocal,
                connectionMode,
                constraintId,
                handle
        );
    }

    public static TrackedConstraint restoreTrackedConstraint(
            ServerLevel level,
            ServerSubLevel first,
            ServerSubLevel second,
            Vector3d firstLocal,
            Vector3d secondLocal,
            Vector3d firstDisplayLocal,
            Vector3d secondDisplayLocal,
            Quaterniond relativeOrientation,
            Vector3d firstAxisLocal,
            Vector3d secondAxisLocal,
            ConnectionMode connectionMode,
            UUID constraintId
    ) throws IOException {
        validateEndpoints(first, second, connectionMode, constraintId, "restore");
        requireFinite(firstLocal, "first constraint local point");
        requireFinite(secondLocal, "second constraint local point");
        requireFiniteOptional(firstDisplayLocal, "first constraint display-local point");
        requireFiniteOptional(secondDisplayLocal, "second constraint display-local point");
        requireFinite(relativeOrientation, "constraint relative orientation");
        if (connectionMode == ConnectionMode.BEARING) {
            requireAxis(firstAxisLocal, "first bearing local axis");
            requireAxis(secondAxisLocal, "second bearing local axis");
        } else {
            requireFiniteOptional(firstAxisLocal, "first constraint local axis");
            requireFiniteOptional(secondAxisLocal, "second constraint local axis");
        }

        PhysicsPipeline pipeline = SablePhysicsPipelineAccess.require(level);
        PhysicsConstraintHandle handle = switch (connectionMode) {
            case FIXED -> SableConstraintApiBridge.addFixed(
                    pipeline,
                    first,
                    second,
                    firstLocal,
                    secondLocal,
                    relativeOrientation
            );
            case FREE -> SableConstraintApiBridge.addGeneric(
                    pipeline,
                    first,
                    second,
                    firstLocal,
                    secondLocal,
                    relativeOrientation
            );
            case BEARING -> SableConstraintApiBridge.addRotary(
                    pipeline,
                    first,
                    second,
                    firstLocal,
                    secondLocal,
                    firstAxisLocal,
                    secondAxisLocal
            );
        };
        requireValidHandle(handle);
        pipeline.wakeUp(first);
        pipeline.wakeUp(second);
        return trackedConstraint(
                level,
                first,
                second,
                firstLocal,
                secondLocal,
                firstDisplayLocal != null ? firstDisplayLocal : firstLocal,
                secondDisplayLocal != null ? secondDisplayLocal : secondLocal,
                relativeOrientation,
                firstAxisLocal,
                secondAxisLocal,
                connectionMode,
                constraintId,
                handle
        );
    }

    private static TrackedConstraint trackedConstraint(
            ServerLevel level,
            ServerSubLevel first,
            ServerSubLevel second,
            Vector3d firstLocal,
            Vector3d secondLocal,
            Vector3d firstDisplayLocal,
            Vector3d secondDisplayLocal,
            Quaterniond relativeOrientation,
            Vector3d firstAxisLocal,
            Vector3d secondAxisLocal,
            ConnectionMode connectionMode,
            UUID constraintId,
            PhysicsConstraintHandle handle
    ) {
        return new TrackedConstraint(
                level.dimension().location().toString(),
                constraintId,
                first.getUniqueId(),
                second.getUniqueId(),
                connectionMode,
                new Vector3d(firstDisplayLocal),
                new Vector3d(secondDisplayLocal),
                new Vector3d(firstLocal),
                new Vector3d(secondLocal),
                new Quaterniond(relativeOrientation),
                firstAxisLocal != null ? new Vector3d(firstAxisLocal) : null,
                secondAxisLocal != null ? new Vector3d(secondAxisLocal) : null,
                handle
        );
    }

    private static void validateEndpoints(
            ServerSubLevel first,
            ServerSubLevel second,
            ConnectionMode connectionMode,
            UUID constraintId,
            String operation
    ) throws IOException {
        if (first == null || second == null) {
            throw new IOException("missing sublevel");
        }
        if (connectionMode == null || constraintId == null) {
            throw new IOException("missing constraint identity or mode");
        }
        if (first.getUniqueId().equals(second.getUniqueId())) {
            throw new IOException("cannot " + operation + " constraint between the same structure");
        }
    }

    private static Vector3d transformDirectionInverse(
            ServerSubLevel subLevel,
            Vector3d worldAxis
    ) throws IOException {
        Vector3d localAxis = new Vector3d(worldAxis);
        subLevel.logicalPose().orientation().transformInverse(localAxis);
        return requireAxis(localAxis, "transformed bearing local axis");
    }

    private static void requireValidHandle(PhysicsConstraintHandle handle) throws IOException {
        if (handle == null || !handle.isValid()) {
            throw new IOException("constraint pipeline returned no valid handle");
        }
    }
}
