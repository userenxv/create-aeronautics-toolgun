package com.enxv.aeronauticsstructuretool.compat.aeronautics;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.FlexibleBlockPosCodec;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.LegacyPlotBlockCoordinates;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.LoadedSubLevelCoordinates;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.PlotBlockTransform;
import com.enxv.aeronauticsstructuretool.blueprint.model.CapturePlan;
import com.enxv.aeronauticsstructuretool.blueprint.model.CapturedSubLevel;
import com.enxv.aeronauticsstructuretool.blueprint.model.LoadedSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Map;
import java.util.UUID;

final class HydraulicHingeBlueprintCompat {
    static final String COORDINATE_SPACE_TAG = "AST_HydraulicHingeCoordinateSpace";
    static final int SAVED_PLOT_LOCAL_V1 = 1;

    private static final String HINGE_SUBLEVEL_TAG = "HingeSubLevel";
    private static final String HINGE_LINK_POS_TAG = "HingeLinkPos";
    private static final String HINGE_PARENT_SUBLEVEL_TAG = "HingeParentSubLevel";
    private static final String HINGE_OWNER_POS_TAG = "HingeOwnerPos";
    private static final String LINKED_POS_TAG = "LinkedPos";
    private static final String LEGACY_HINGE_LINK_ID = "aero_universal_joint:hydraulic_hinge_link";
    private static final String HINGE_LINK_ID = "aeronautics_utility_objects:hydraulic_hinge_link";

    private HydraulicHingeBlueprintCompat() {
    }

    static void remapForSave(
            CompoundTag tag,
            CapturePlan plan,
            CapturedSubLevel currentSubLevel
    ) {
        if (!hasHingeState(tag)) {
            tag.remove(COORDINATE_SPACE_TAG);
            return;
        }

        BlockPos ownerPos = readBlockEntityPos(tag);
        try {
            if (!tag.contains(LINKED_POS_TAG)) {
                throw new InvalidHingeReference("hydraulic connection is not part of the saved link");
            }
            requireCoreReference(tag);
            CapturedSubLevel helper = requireCaptured(
                    plan,
                    requireUuid(tag, HINGE_SUBLEVEL_TAG),
                    "hinge helper"
            );
            CapturedSubLevel parent = tag.contains(HINGE_PARENT_SUBLEVEL_TAG)
                    ? requireCaptured(
                            plan,
                            requireUuid(tag, HINGE_PARENT_SUBLEVEL_TAG),
                            "hinge parent"
                    )
                    : currentSubLevel;
            if (!parent.blueprintId().equals(currentSubLevel.blueprintId())) {
                throw new InvalidHingeReference("hinge parent is not the block entity's sublevel");
            }
            if (helper.blueprintId().equals(parent.blueprintId())) {
                throw new InvalidHingeReference("hinge helper and parent are the same sublevel");
            }

            BlockPos hingeLinkPos = readPosition(tag, HINGE_LINK_POS_TAG);
            PlotBlockTransform helperTransform = PlotBlockTransform.capture(helper.subLevel());
            if (!helperTransform.containsPlotAbsolute(hingeLinkPos)) {
                throw new InvalidHingeReference("hinge link position is outside its helper sublevel");
            }
            String helperBlockId = BuiltInRegistries.BLOCK.getKey(
                    helper.subLevel().getLevel().getBlockState(hingeLinkPos).getBlock()
            ).toString();
            if (!HINGE_LINK_ID.equals(helperBlockId) && !LEGACY_HINGE_LINK_ID.equals(helperBlockId)) {
                throw new InvalidHingeReference("hinge helper does not contain a hydraulic hinge link");
            }

            BlockPos storedOwnerPos = tag.contains(HINGE_OWNER_POS_TAG)
                    ? readPosition(tag, HINGE_OWNER_POS_TAG)
                    : ownerPos;
            if (!storedOwnerPos.equals(ownerPos)) {
                throw new InvalidHingeReference("hinge owner position does not match its block entity");
            }
            PlotBlockTransform parentTransform = PlotBlockTransform.capture(parent.subLevel());
            if (!parentTransform.containsPlotAbsolute(storedOwnerPos)) {
                throw new InvalidHingeReference("hinge owner position is outside its parent sublevel");
            }

            tag.putUUID(HINGE_SUBLEVEL_TAG, helper.blueprintId());
            tag.putUUID(HINGE_PARENT_SUBLEVEL_TAG, parent.blueprintId());
            writePosition(tag, HINGE_LINK_POS_TAG, helperTransform.toSavedLocalBlockPos(hingeLinkPos));
            writePosition(tag, HINGE_OWNER_POS_TAG, parentTransform.toSavedLocalBlockPos(storedOwnerPos));
            tag.putInt(COORDINATE_SPACE_TAG, SAVED_PLOT_LOCAL_V1);
        } catch (InvalidHingeReference exception) {
            clearHingeState(tag);
            AeronauticsStructureToolMod.LOGGER.warn(
                    "Hydraulic hinge state at {} was not saved: {}; it will be rebuilt when placed",
                    ownerPos,
                    exception.getMessage()
            );
        }
    }

    static void remapForLoad(
            CompoundTag tag,
            Map<UUID, LoadedSubLevel> loadedSublevels,
            LoadedSubLevel currentSubLevel
    ) {
        if (!hasHingeState(tag)) {
            tag.remove(COORDINATE_SPACE_TAG);
            return;
        }

        boolean savedPlotLocal = readCoordinateSpace(tag);
        BlockPos ownerTagPos = readBlockEntityPos(tag);
        try {
            if (!tag.contains(LINKED_POS_TAG)) {
                throw new InvalidHingeReference("hydraulic connection is not part of the loaded link");
            }
            requireCoreReference(tag);
            LoadedSubLevel helper = requireLoaded(
                    loadedSublevels,
                    requireUuid(tag, HINGE_SUBLEVEL_TAG),
                    "hinge helper"
            );
            LoadedSubLevel parent = tag.contains(HINGE_PARENT_SUBLEVEL_TAG)
                    ? requireLoaded(
                            loadedSublevels,
                            requireUuid(tag, HINGE_PARENT_SUBLEVEL_TAG),
                            "hinge parent"
                    )
                    : currentSubLevel;
            if (!parent.saved().blueprintId().equals(currentSubLevel.saved().blueprintId())) {
                throw new InvalidHingeReference("hinge parent is not the block entity's sublevel");
            }
            if (helper.saved().blueprintId().equals(parent.saved().blueprintId())) {
                throw new InvalidHingeReference("hinge helper and parent are the same sublevel");
            }

            BlockPos savedLinkPos = decodeSavedPosition(
                    readPosition(tag, HINGE_LINK_POS_TAG),
                    helper,
                    savedPlotLocal
            );
            BlockPos expectedOwnerPos = currentSubLevel.transform().toGlobalBlockPos(ownerTagPos);
            BlockPos loadedOwnerPos;
            if (tag.contains(HINGE_OWNER_POS_TAG)) {
                BlockPos savedOwnerPos = decodeSavedPosition(
                        readPosition(tag, HINGE_OWNER_POS_TAG),
                        parent,
                        savedPlotLocal
                );
                loadedOwnerPos = LoadedSubLevelCoordinates.toGlobalBlockPos(parent, savedOwnerPos);
            } else {
                loadedOwnerPos = expectedOwnerPos;
            }
            if (!loadedOwnerPos.equals(expectedOwnerPos)) {
                throw new InvalidHingeReference("hinge owner position does not match its block entity");
            }

            tag.putUUID(HINGE_SUBLEVEL_TAG, helper.subLevel().getUniqueId());
            tag.putUUID(HINGE_PARENT_SUBLEVEL_TAG, parent.subLevel().getUniqueId());
            writePosition(
                    tag,
                    HINGE_LINK_POS_TAG,
                    LoadedSubLevelCoordinates.toGlobalBlockPos(helper, savedLinkPos)
            );
            writePosition(tag, HINGE_OWNER_POS_TAG, loadedOwnerPos);
            tag.remove(COORDINATE_SPACE_TAG);
        } catch (InvalidHingeReference exception) {
            clearHingeState(tag);
            AeronauticsStructureToolMod.LOGGER.warn(
                    "Hydraulic hinge state at {} was not restored: {}; it will be rebuilt after placement",
                    ownerTagPos,
                    exception.getMessage()
            );
        }
    }

    static BlockPos decodeLegacyPosition(BlockPos storedPos, CompoundTag targetPlotTag) {
        return LegacyPlotBlockCoordinates.toSavedLocal(storedPos, targetPlotTag);
    }

    private static BlockPos decodeSavedPosition(
            BlockPos storedPos,
            LoadedSubLevel target,
            boolean savedPlotLocal
    ) {
        BlockPos localPos = savedPlotLocal
                ? storedPos
                : decodeLegacyPosition(storedPos, target.saved().plotTag());
        if (!LegacyPlotBlockCoordinates.containsSavedLocal(localPos, target.saved().plotTag())) {
            throw new InvalidHingeReference("hinge position is outside its saved sublevel");
        }
        return localPos;
    }

    private static boolean readCoordinateSpace(CompoundTag tag) {
        if (!tag.contains(COORDINATE_SPACE_TAG)) {
            return false;
        }
        if (!tag.contains(COORDINATE_SPACE_TAG, Tag.TAG_INT)) {
            throw new IllegalArgumentException("hydraulic hinge coordinate-space tag is not an integer");
        }
        int version = tag.getInt(COORDINATE_SPACE_TAG);
        if (version != SAVED_PLOT_LOCAL_V1) {
            throw new IllegalArgumentException("unsupported hydraulic hinge coordinate-space version " + version);
        }
        return true;
    }

    private static CapturedSubLevel requireCaptured(CapturePlan plan, UUID id, String label) {
        CapturedSubLevel captured = plan.findByOriginalId(id);
        if (captured == null) {
            throw new InvalidHingeReference(label + " is outside the captured blueprint");
        }
        return captured;
    }

    private static LoadedSubLevel requireLoaded(
            Map<UUID, LoadedSubLevel> loadedSublevels,
            UUID storedId,
            String label
    ) {
        LoadedSubLevel direct = loadedSublevels.get(storedId);
        if (direct != null) {
            return direct;
        }
        LoadedSubLevel match = null;
        for (LoadedSubLevel candidate : loadedSublevels.values()) {
            if (!candidate.saved().originalId().equals(storedId)) {
                continue;
            }
            if (match != null) {
                throw new InvalidHingeReference(label + " source UUID is ambiguous");
            }
            match = candidate;
        }
        if (match == null) {
            throw new InvalidHingeReference(label + " is missing from the blueprint");
        }
        return match;
    }

    private static void requireCoreReference(CompoundTag tag) {
        if (!tag.contains(HINGE_SUBLEVEL_TAG) || !tag.contains(HINGE_LINK_POS_TAG)) {
            throw new InvalidHingeReference("hinge helper reference is incomplete");
        }
    }

    private static UUID requireUuid(CompoundTag tag, String key) {
        if (!tag.hasUUID(key)) {
            throw new InvalidHingeReference("invalid UUID '" + key + "'");
        }
        return tag.getUUID(key);
    }

    private static BlockPos readBlockEntityPos(CompoundTag tag) {
        if (!tag.contains("x", Tag.TAG_INT)
                || !tag.contains("y", Tag.TAG_INT)
                || !tag.contains("z", Tag.TAG_INT)) {
            throw new IllegalArgumentException("hydraulic head has no integer block entity position");
        }
        return new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
    }

    private static BlockPos readPosition(CompoundTag tag, String key) {
        try {
            return FlexibleBlockPosCodec.readRequired(tag, key, "Hydraulic hinge state");
        } catch (IllegalArgumentException exception) {
            throw new InvalidHingeReference(exception.getMessage());
        }
    }

    private static void writePosition(CompoundTag tag, String key, BlockPos pos) {
        FlexibleBlockPosCodec.write(
                tag,
                key,
                pos,
                FlexibleBlockPosCodec.Encoding.NBT_BLOCK_POS
        );
    }

    private static boolean hasHingeState(CompoundTag tag) {
        return tag.contains(HINGE_SUBLEVEL_TAG)
                || tag.contains(HINGE_LINK_POS_TAG)
                || tag.contains(HINGE_PARENT_SUBLEVEL_TAG)
                || tag.contains(HINGE_OWNER_POS_TAG);
    }

    private static void clearHingeState(CompoundTag tag) {
        tag.remove(HINGE_SUBLEVEL_TAG);
        tag.remove(HINGE_LINK_POS_TAG);
        tag.remove(HINGE_PARENT_SUBLEVEL_TAG);
        tag.remove(HINGE_OWNER_POS_TAG);
        tag.remove(COORDINATE_SPACE_TAG);
    }

    private static final class InvalidHingeReference extends IllegalArgumentException {
        private InvalidHingeReference(String message) {
            super(message);
        }
    }
}
