package com.enxv.aeronauticsstructuretool.compat.drivebywire;

import net.minecraft.core.BlockPos;

import java.util.UUID;

public record DriveByWireWorldSource(UUID ownerSubLevelId, BlockPos localSourcePos) {
}
