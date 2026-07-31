package com.enxv.aeronauticsstructuretool.toolgun.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record LoadBlueprintRequest(
        BlockPos clickedPos,
        Direction face,
        double hitX,
        double hitY,
        double hitZ,
        int rotationDegrees,
        int scalePercent,
        int offsetX,
        int offsetY,
        int offsetZ,
        boolean autoWeld,
        String connectionMode,
        String snapMode,
        String fileName,
        byte[] fileContents
) {
    public LoadBlueprintRequest {
        fileContents = fileContents == null ? new byte[0] : fileContents.clone();
    }

    @Override
    public byte[] fileContents() {
        return fileContents.clone();
    }
}
