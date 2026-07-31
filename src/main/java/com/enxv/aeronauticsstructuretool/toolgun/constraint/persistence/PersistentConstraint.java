package com.enxv.aeronauticsstructuretool.toolgun.constraint.persistence;

import com.enxv.aeronauticsstructuretool.ConnectionMode;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.UUID;

public record PersistentConstraint(
        PersistentConstraintCoordinateSpace coordinateSpace,
        String dimensionId,
        UUID constraintId,
        UUID firstSubLevelId,
        UUID secondSubLevelId,
        ConnectionMode connectionMode,
        Vector3d firstDisplayLocalPoint,
        Vector3d secondDisplayLocalPoint,
        Vector3d firstLocalPoint,
        Vector3d secondLocalPoint,
        Vector3d firstWorldPoint,
        Vector3d secondWorldPoint,
        Vector3d firstPlotLocalPoint,
        Vector3d secondPlotLocalPoint,
        Vector3d firstDisplayPlotLocalPoint,
        Vector3d secondDisplayPlotLocalPoint,
        Quaterniond relativeOrientation,
        Vector3d firstAxisLocal,
        Vector3d secondAxisLocal
) {
}
