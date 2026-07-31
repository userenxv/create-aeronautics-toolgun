package com.enxv.aeronauticsstructuretool.compat.aeronautics;

import com.enxv.aeronauticsstructuretool.blueprint.geometry.FlexibleBlockPosCodec;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.LinkedBlockReferenceRemapper;
import com.enxv.aeronauticsstructuretool.blueprint.model.CapturePlan;
import com.enxv.aeronauticsstructuretool.blueprint.model.LoadedSubLevel;
import net.minecraft.nbt.CompoundTag;

import java.util.Map;
import java.util.UUID;

public final class AeronauticsLinkBlueprintCompat {
    private static final String LEGACY_UNIVERSAL_JOINT_ID = "aero_universal_joint:universal_joint";
    private static final String UNIVERSAL_JOINT_ID = "aeronautics_utility_objects:universal_joint";
    private static final String HYDRAULIC_HEAD_ID = "aeronautics_utility_objects:hydraulic_connection_head";
    private static final String LINKED_POS_TAG = "LinkedPos";
    private static final String LINKED_SUBLEVEL_TAG = "LinkedSubLevel";

    private AeronauticsLinkBlueprintCompat() {
    }

    public static boolean supports(String blockEntityId) {
        return LEGACY_UNIVERSAL_JOINT_ID.equals(blockEntityId)
                || UNIVERSAL_JOINT_ID.equals(blockEntityId)
                || HYDRAULIC_HEAD_ID.equals(blockEntityId);
    }

    public static void remapForSave(CompoundTag tag, CapturePlan plan) {
        LinkedBlockReferenceRemapper.remapForSave(
                tag,
                LINKED_POS_TAG,
                LINKED_SUBLEVEL_TAG,
                plan,
                FlexibleBlockPosCodec.Encoding.NBT_BLOCK_POS,
                "Aeronautics linked connection"
        );
    }

    public static void remapForLoad(
            CompoundTag tag,
            Map<UUID, LoadedSubLevel> loadedSublevels,
            LoadedSubLevel currentSubLevel
    ) {
        LinkedBlockReferenceRemapper.remapForLoad(
                tag,
                LINKED_POS_TAG,
                LINKED_SUBLEVEL_TAG,
                loadedSublevels,
                currentSubLevel,
                FlexibleBlockPosCodec.Encoding.NBT_BLOCK_POS,
                true,
                "Aeronautics linked connection"
        );
    }
}
