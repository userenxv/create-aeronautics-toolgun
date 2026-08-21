package com.enxv.aeronauticsstructuretool.compat.sableschematicapi;

import com.enxv.aeronauticsstructuretool.blueprint.geometry.LegacyPlotBlockCoordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

final class SableBlueprintFrameCoordinates {
    private static final String STORAGE_BOUNDS_TAG = "storage_bounds";
    private static final String BLOCKS_ORIGIN_TAG = "blocks_origin";

    private SableBlueprintFrameCoordinates() {
    }

    static BlockPos savedLocalOrigin(CompoundTag frame, CompoundTag plotTag) {
        int[] bounds = frame.getIntArray(STORAGE_BOUNDS_TAG);
        int[] origin = frame.getIntArray(BLOCKS_ORIGIN_TAG);
        if (bounds.length != 6 || origin.length != 3) {
            throw new IllegalArgumentException("Sable weld frame bounds are malformed");
        }
        return LegacyPlotBlockCoordinates.toSavedLocal(
                new BlockPos(origin[0], origin[1], origin[2]),
                plotTag
        );
    }
}
