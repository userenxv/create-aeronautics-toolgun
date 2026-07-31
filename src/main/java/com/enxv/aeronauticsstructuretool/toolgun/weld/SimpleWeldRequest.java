package com.enxv.aeronauticsstructuretool.toolgun.weld;

import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.UUID;

public record SimpleWeldRequest(
        UUID childSubLevelId,
        UUID parentSubLevelId,
        Vector3d childLocalPoint,
        Vector3d parentLocalPoint,
        Quaterniond relativeRotation,
        Vector3d parentOffset
) {
}
