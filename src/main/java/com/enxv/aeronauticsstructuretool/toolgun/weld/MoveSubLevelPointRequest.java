package com.enxv.aeronauticsstructuretool.toolgun.weld;

import org.joml.Vector3d;

import java.util.UUID;

public record MoveSubLevelPointRequest(
        UUID subLevelId,
        Vector3d localPoint,
        Vector3d targetPoint
) {
}
