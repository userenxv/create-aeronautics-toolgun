package com.enxv.aeronauticsstructuretool.toolgun.constraint;

import com.enxv.aeronauticsstructuretool.ConnectionMode;
import org.joml.Vector3d;

import java.util.UUID;

public record ConstraintVisualSnapshot(
        UUID firstSubLevelId,
        UUID secondSubLevelId,
        ConnectionMode connectionMode,
        Vector3d firstDisplayLocalPoint,
        Vector3d secondDisplayLocalPoint,
        Vector3d firstConstraintLocalPoint,
        Vector3d secondConstraintLocalPoint,
        Vector3d firstAxisLocal
) {
}
