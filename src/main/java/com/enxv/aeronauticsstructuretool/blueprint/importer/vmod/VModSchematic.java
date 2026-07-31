package com.enxv.aeronauticsstructuretool.blueprint.importer.vmod;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class VModSchematic {
    private final Map<Long, VModShip> ships = new LinkedHashMap<>();

    Map<Long, VModShip> ships() {
        return this.ships;
    }
}

final class VModShip {
    private final long shipId;
    private final VModShipInfo info;
    private final List<VModBlock> blocks = new ArrayList<>();

    VModShip(long shipId, VModShipInfo info) {
        this.shipId = shipId;
        this.info = info;
    }

    long shipId() {
        return this.shipId;
    }

    VModShipInfo info() {
        return this.info;
    }

    List<VModBlock> blocks() {
        return this.blocks;
    }
}

record VModShipInfo(
        Vector3d relativePositionToCenter,
        Quaterniond rotation
) {
}

record VModBlock(
        BlockPos position,
        BlockState state,
        CompoundTag blockEntityTag
) {
}
