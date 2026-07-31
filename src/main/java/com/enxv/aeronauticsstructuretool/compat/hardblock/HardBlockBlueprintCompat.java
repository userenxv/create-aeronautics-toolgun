package com.enxv.aeronauticsstructuretool.compat.hardblock;

import com.enxv.aeronauticsstructuretool.blueprint.geometry.FlexibleBlockPosCodec;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.LinkedBlockReferenceRemapper;
import com.enxv.aeronauticsstructuretool.blueprint.model.CapturePlan;
import com.enxv.aeronauticsstructuretool.blueprint.model.LoadedSubLevel;
import net.minecraft.nbt.CompoundTag;

import java.util.Map;
import java.util.UUID;

public final class HardBlockBlueprintCompat {
    public static final String GUN_SIGHT_EYEPIECE_ID = "hardblock:gun_sight_eyepiece";
    public static final String GUN_SIGHT_OBJECTIVE_ID = "hardblock:gun_sight_objective";

    private static final String LINKED_OBJECTIVE_POS = "LinkedObjectivePos";
    private static final String LINKED_RADAR_POS = "LinkedRadarPos";
    private static final String LINKED_EYEPIECE_POS = "LinkedEyepiecePos";
    private static final String OBJECTIVE_SUBLEVEL = "AST_LinkedObjectiveSubLevel";
    private static final String RADAR_SUBLEVEL = "AST_LinkedRadarSubLevel";
    private static final String EYEPIECE_SUBLEVEL = "AST_LinkedEyepieceSubLevel";

    private HardBlockBlueprintCompat() {
    }

    public static void remapForSave(String blockEntityId, CompoundTag tag, CapturePlan plan) {
        if (GUN_SIGHT_EYEPIECE_ID.equals(blockEntityId)) {
            save(tag, LINKED_OBJECTIVE_POS, OBJECTIVE_SUBLEVEL, plan, "HardBlock gun sight objective");
            save(tag, LINKED_RADAR_POS, RADAR_SUBLEVEL, plan, "HardBlock gun sight radar");
        } else if (GUN_SIGHT_OBJECTIVE_ID.equals(blockEntityId)) {
            save(tag, LINKED_EYEPIECE_POS, EYEPIECE_SUBLEVEL, plan, "HardBlock gun sight eyepiece");
        }
    }

    public static void remapForLoad(
            String blockEntityId,
            CompoundTag tag,
            Map<UUID, LoadedSubLevel> loadedSublevels,
            LoadedSubLevel currentSubLevel
    ) {
        if (GUN_SIGHT_EYEPIECE_ID.equals(blockEntityId)) {
            load(tag, LINKED_OBJECTIVE_POS, OBJECTIVE_SUBLEVEL, loadedSublevels, currentSubLevel, "HardBlock gun sight objective");
            load(tag, LINKED_RADAR_POS, RADAR_SUBLEVEL, loadedSublevels, currentSubLevel, "HardBlock gun sight radar");
        } else if (GUN_SIGHT_OBJECTIVE_ID.equals(blockEntityId)) {
            load(tag, LINKED_EYEPIECE_POS, EYEPIECE_SUBLEVEL, loadedSublevels, currentSubLevel, "HardBlock gun sight eyepiece");
        }
    }

    private static void save(
            CompoundTag tag,
            String positionTag,
            String sublevelTag,
            CapturePlan plan,
            String owner
    ) {
        LinkedBlockReferenceRemapper.remapForSave(
                tag,
                positionTag,
                sublevelTag,
                plan,
                FlexibleBlockPosCodec.Encoding.PACKED_LONG,
                owner
        );
    }

    private static void load(
            CompoundTag tag,
            String positionTag,
            String sublevelTag,
            Map<UUID, LoadedSubLevel> loadedSublevels,
            LoadedSubLevel currentSubLevel,
            String owner
    ) {
        LinkedBlockReferenceRemapper.remapForLoad(
                tag,
                positionTag,
                sublevelTag,
                loadedSublevels,
                currentSubLevel,
                FlexibleBlockPosCodec.Encoding.PACKED_LONG,
                false,
                owner
        );
    }
}
