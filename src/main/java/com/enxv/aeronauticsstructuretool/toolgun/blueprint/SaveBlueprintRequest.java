package com.enxv.aeronauticsstructuretool.toolgun.blueprint;

import net.minecraft.core.BlockPos;

public record SaveBlueprintRequest(
        BlockPos clickedPos,
        String fileName,
        double connectedSublevelProximity
) {
}
