package com.enxv.aeronauticsstructuretool.compat.simulated;

import com.enxv.aeronauticsstructuretool.blueprint.model.CapturePlan;
import com.enxv.aeronauticsstructuretool.blueprint.model.CapturedSubLevel;
import com.enxv.aeronauticsstructuretool.blueprint.model.LoadedSubLevel;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Map;
import java.util.UUID;

public final class SimulatedStructureCompat {
    private SimulatedStructureCompat() {
    }

    public static void remapRopeForSave(
            CompoundTag blockEntityTag,
            CapturePlan plan,
            CapturedSubLevel currentSubLevel
    ) {
        SimulatedStructureNbtRemapper.remapRopeForSave(
                blockEntityTag,
                plan,
                currentSubLevel
        );
    }

    public static void remapRopeForLoad(
            CompoundTag blockEntityTag,
            Map<UUID, LoadedSubLevel> loadedSublevels,
            LoadedSubLevel currentSubLevel,
            Map<UUID, UUID> ropeIdRemap
    ) {
        SimulatedStructureNbtRemapper.remapRopeForLoad(
                blockEntityTag,
                loadedSublevels,
                currentSubLevel,
                ropeIdRemap
        );
    }

    public static void validateSwivelBearingForSave(CompoundTag tag, CapturePlan plan) {
        SimulatedStructureNbtRemapper.validateSwivelBearingForSave(tag, plan);
    }

    public static void remapSwivelPlateForSave(CompoundTag tag, CapturePlan plan) {
        SimulatedStructureNbtRemapper.remapSwivelPlateForSave(tag, plan);
    }

    public static void remapSwivelPlateForLoad(
            CompoundTag tag,
            Map<UUID, LoadedSubLevel> loadedSublevels
    ) {
        SimulatedStructureNbtRemapper.remapSwivelPlateForLoad(tag, loadedSublevels);
    }

    public static void restoreSwivelBearingPositionForLoad(
            CompoundTag tag,
            Map<UUID, LoadedSubLevel> loadedSublevels
    ) {
        SimulatedStructureNbtRemapper.restoreSwivelBearingPositionForLoad(tag, loadedSublevels);
    }

    public static void repairSwivelPlate(BlockEntity blockEntity) {
        SimulatedStructureRuntimeRestorer.repairSwivelPlate(blockEntity);
    }

    public static void refreshRopeAttachments(
            ServerLevel level,
            Map<UUID, LoadedSubLevel> loadedSublevels
    ) {
        SimulatedStructureRuntimeRestorer.refreshRopeAttachments(level, loadedSublevels);
    }
}
