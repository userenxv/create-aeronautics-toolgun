package com.enxv.aeronauticsstructuretool.vehicle.query;

import net.minecraft.core.BlockPos;

import java.util.UUID;

public record VehicleQueryEntry(
        UUID id,
        String displayName,
        String fullName,
        double distance,
        BlockPos position,
        boolean loaded,
        boolean broken
) {
}
