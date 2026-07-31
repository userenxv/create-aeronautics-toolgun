package com.enxv.aeronauticsstructuretool.toolgun.constraint.persistence;

import org.joml.Vector3d;

record ResolvedPersistentConstraint(
        Vector3d firstLocalPoint,
        Vector3d secondLocalPoint,
        Vector3d firstDisplayLocalPoint,
        Vector3d secondDisplayLocalPoint
) {
}
