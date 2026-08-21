package com.enxv.aeronauticsstructuretool.blueprint.geometry;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class LegacyPlotBlockCoordinates {
    private static final String LOG_SIZE_TAG = "log_size";
    private static final int BLOCKS_PER_CHUNK_SHIFT = 4;
    private static final int MAX_LOG_SIZE = 26;

    private LegacyPlotBlockCoordinates() {
    }

    /**
     * Recovers Plot-local X/Z from pre-versioned runtime coordinates. Sable aligns each Plot to its
     * full side length, so floor-modulo is exact and is also idempotent for coordinates already local.
     */
    public static BlockPos toSavedLocal(BlockPos storedPos, CompoundTag plotTag) {
        int sideLength = sideLengthBlocks(plotTag);
        return new BlockPos(
                Math.floorMod(storedPos.getX(), sideLength),
                storedPos.getY(),
                Math.floorMod(storedPos.getZ(), sideLength)
        );
    }

    public static boolean containsSavedLocal(BlockPos pos, CompoundTag plotTag) {
        int sideLength = sideLengthBlocks(plotTag);
        return pos.getX() >= 0 && pos.getX() < sideLength
                && pos.getZ() >= 0 && pos.getZ() < sideLength;
    }

    public static int sideLengthBlocks(CompoundTag plotTag) {
        if (plotTag == null || !plotTag.contains(LOG_SIZE_TAG, Tag.TAG_INT)) {
            throw new IllegalArgumentException("plot log_size is missing or invalid");
        }
        int logSize = plotTag.getInt(LOG_SIZE_TAG);
        if (logSize < 0 || logSize > MAX_LOG_SIZE) {
            throw new IllegalArgumentException("unsupported plot log_size " + logSize);
        }
        return 1 << (logSize + BLOCKS_PER_CHUNK_SHIFT);
    }
}
