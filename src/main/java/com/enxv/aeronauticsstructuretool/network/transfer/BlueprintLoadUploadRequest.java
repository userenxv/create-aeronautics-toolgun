package com.enxv.aeronauticsstructuretool.network.transfer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.Arrays;
import java.util.UUID;

public record BlueprintLoadUploadRequest(
        UUID transferId,
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
        int totalBytes,
        byte[] sha256
) {
    public BlueprintLoadUploadRequest {
        sha256 = Arrays.copyOf(sha256, sha256.length);
    }
}
