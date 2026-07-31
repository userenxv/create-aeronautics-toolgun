package com.enxv.aeronauticsstructuretool.toolgun.constraint;

import com.enxv.aeronauticsstructuretool.ConnectionMode;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.UUID;

public record TrackedConstraint(
        String dimensionId,
        UUID constraintId,
        UUID firstSubLevelId,
        UUID secondSubLevelId,
        ConnectionMode connectionMode,
        Vector3d firstDisplayLocalPoint,
        Vector3d secondDisplayLocalPoint,
        Vector3d firstLocalPoint,
        Vector3d secondLocalPoint,
        Quaterniond relativeOrientation,
        Vector3d firstAxisLocal,
        Vector3d secondAxisLocal,
        PhysicsConstraintHandle handle
) {
}
