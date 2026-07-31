package com.enxv.aeronauticsstructuretool.compat.synaxis;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record SynaxisControllerWireConnection(
        BlockPos sourcePos,
        BlockPos sinkPos,
        Direction direction,
        String channel
) {
}
