package com.enxv.aeronauticsstructuretool.blueprint.model;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.UUID;

public record CapturedSubLevel(
        UUID blueprintId,
        ServerSubLevel subLevel,
        Vector3d relativePosition,
        Vector3d relativeRotationOffset,
        Quaterniond relativeOrientation,
        Vector3d localAnchor
) {
}
