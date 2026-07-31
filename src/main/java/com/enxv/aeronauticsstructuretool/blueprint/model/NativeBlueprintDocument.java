package com.enxv.aeronauticsstructuretool.blueprint.model;

import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

public record NativeBlueprintDocument(
        String format,
        int sourceMinBuildHeight,
        UUID rootBlueprintId,
        Quaterniond rootOrientation,
        Vector3d rootRotationOffset,
        List<SavedSubLevelBlueprint> sublevels
) {
    public NativeBlueprintDocument {
        rootOrientation = new Quaterniond(rootOrientation);
        rootRotationOffset = new Vector3d(rootRotationOffset);
        sublevels = List.copyOf(sublevels);
    }
}
