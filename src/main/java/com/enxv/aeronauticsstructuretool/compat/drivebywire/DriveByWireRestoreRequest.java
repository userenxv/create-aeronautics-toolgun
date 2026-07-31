package com.enxv.aeronauticsstructuretool.compat.drivebywire;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

record DriveByWireRestoreRequest(BlockPos backupBlockPos, CompoundTag snapshot) {
}
