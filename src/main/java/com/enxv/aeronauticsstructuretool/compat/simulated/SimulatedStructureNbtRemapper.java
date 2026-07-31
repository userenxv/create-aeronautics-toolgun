package com.enxv.aeronauticsstructuretool.compat.simulated;

import com.enxv.aeronauticsstructuretool.blueprint.geometry.LoadedSubLevelCoordinates;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.PlotBlockTransform;
import com.enxv.aeronauticsstructuretool.blueprint.model.CapturePlan;
import com.enxv.aeronauticsstructuretool.blueprint.model.CapturedSubLevel;
import com.enxv.aeronauticsstructuretool.blueprint.model.LoadedSubLevel;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import java.util.Map;
import java.util.UUID;

final class SimulatedStructureNbtRemapper {
    private static final String ROPE_ATTACHED_ID_TAG = "HasRopeAttached";
    private static final String ROPE_OWN_STRAND_TAG = "OwnStrand";
    private static final String ROPE_STRAND_TAG = "Strand";
    private static final String ROPE_ATTACHMENTS_TAG = "attachments";
    private static final String ROPE_ATTACHMENT_SUBLEVEL_ID_TAG = "subLevelID";
    private static final String ROPE_ATTACHMENT_BLOCK_TAG = "blockAttachment";
    private static final String ROPE_UUID_TAG = "uuid";
    private static final String PARENT_POS_TAG = "ParentPos";
    private static final String PARENT_SUBLEVEL_ID_TAG = "ParentSubLevelId";
    private static final String SWIVEL_PLATE_POS_TAG = "SwivelPlate";
    private static final String SWIVEL_CHILD_SUBLEVEL_ID_TAG = "SubLevelID";

    private SimulatedStructureNbtRemapper() {
    }

    static void remapRopeForSave(
            CompoundTag blockEntityTag,
            CapturePlan plan,
            CapturedSubLevel currentSubLevel
    ) {
        if (!blockEntityTag.getBoolean(ROPE_OWN_STRAND_TAG)) {
            return;
        }
        CompoundTag strandTag = requireCompound(
                blockEntityTag,
                ROPE_STRAND_TAG,
                "Simulated rope owner"
        );
        requireRopeUuid(strandTag);
        if (!strandTag.contains(ROPE_ATTACHMENTS_TAG, Tag.TAG_LIST)) {
            throw new IllegalArgumentException("Simulated rope strand has no attachments list");
        }
        ListTag attachments = strandTag.getList(ROPE_ATTACHMENTS_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < attachments.size(); i++) {
            remapAttachmentForSave(attachments.getCompound(i), plan, currentSubLevel);
        }
    }

    static void remapRopeForLoad(
            CompoundTag blockEntityTag,
            Map<UUID, LoadedSubLevel> loadedSublevels,
            LoadedSubLevel currentSubLevel,
            Map<UUID, UUID> ropeIdRemap
    ) {
        UUID attachedRopeId = blockEntityTag.hasUUID(ROPE_ATTACHED_ID_TAG)
                ? blockEntityTag.getUUID(ROPE_ATTACHED_ID_TAG)
                : null;
        CompoundTag strandTag = blockEntityTag.getBoolean(ROPE_OWN_STRAND_TAG)
                ? requireCompound(blockEntityTag, ROPE_STRAND_TAG, "Simulated rope owner")
                : null;
        UUID strandId = strandTag == null ? null : requireRopeUuid(strandTag);
        UUID sourceRopeId = strandId != null ? strandId : attachedRopeId;
        if (sourceRopeId != null) {
            UUID runtimeRopeId = ropeIdRemap.computeIfAbsent(sourceRopeId, ignored -> UUID.randomUUID());
            blockEntityTag.putUUID(ROPE_ATTACHED_ID_TAG, runtimeRopeId);
            if (strandTag != null) {
                strandTag.putString(ROPE_UUID_TAG, runtimeRopeId.toString());
            }
            if (attachedRopeId != null) {
                UUID previous = ropeIdRemap.putIfAbsent(attachedRopeId, runtimeRopeId);
                if (previous != null && !previous.equals(runtimeRopeId)) {
                    throw new IllegalArgumentException(
                            "conflicting Simulated rope UUID mapping for " + attachedRopeId
                    );
                }
            }
        }
        if (strandTag == null) {
            return;
        }
        if (!strandTag.contains(ROPE_ATTACHMENTS_TAG, Tag.TAG_LIST)) {
            throw new IllegalArgumentException("Simulated rope strand has no attachments list");
        }
        ListTag attachments = strandTag.getList(ROPE_ATTACHMENTS_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < attachments.size(); i++) {
            remapAttachmentForLoad(attachments.getCompound(i), loadedSublevels, currentSubLevel);
        }
    }

    static void validateSwivelBearingForSave(CompoundTag tag, CapturePlan plan) {
        if (!tag.contains(SWIVEL_PLATE_POS_TAG) && !tag.contains(SWIVEL_CHILD_SUBLEVEL_ID_TAG)) {
            return;
        }
        UUID childBlueprintId = requireUuid(tag, SWIVEL_CHILD_SUBLEVEL_ID_TAG, "Simulated swivel bearing");
        if (plan.findByBlueprintId(childBlueprintId) == null) {
            throw new IllegalArgumentException(
                    "Simulated swivel bearing references an unknown Sable blueprint sublevel: "
                            + childBlueprintId
            );
        }
        requireBlockPos(tag, SWIVEL_PLATE_POS_TAG, "Simulated swivel bearing");
    }

    static void remapSwivelPlateForSave(CompoundTag tag, CapturePlan plan) {
        if (!tag.contains(PARENT_POS_TAG) && !tag.contains(PARENT_SUBLEVEL_ID_TAG)) {
            return;
        }
        UUID parentId = requireUuid(tag, PARENT_SUBLEVEL_ID_TAG, "Simulated swivel plate");
        CapturedSubLevel parent = plan.findByOriginalId(parentId);
        if (parent == null) {
            throw new IllegalArgumentException(
                    "Simulated swivel parent was not included in capture: " + parentId
            );
        }
        BlockPos parentPos = requireBlockPos(tag, PARENT_POS_TAG, "Simulated swivel plate");
        PlotBlockTransform parentTransform = PlotBlockTransform.capture(parent.subLevel());
        tag.putUUID(PARENT_SUBLEVEL_ID_TAG, parent.blueprintId());
        tag.put(
                PARENT_POS_TAG,
                NbtUtils.writeBlockPos(parentTransform.toSavedLocalBlockPos(parentPos))
        );
    }

    static void remapSwivelPlateForLoad(
            CompoundTag tag,
            Map<UUID, LoadedSubLevel> loadedSublevels
    ) {
        if (!tag.contains(PARENT_POS_TAG) && !tag.contains(PARENT_SUBLEVEL_ID_TAG)) {
            return;
        }
        UUID parentId = requireUuid(tag, PARENT_SUBLEVEL_ID_TAG, "Simulated swivel plate");
        LoadedSubLevel parent = requireLoadedSubLevel(
                loadedSublevels,
                parentId,
                "Simulated swivel parent"
        );
        BlockPos parentPos = requireBlockPos(tag, PARENT_POS_TAG, "Simulated swivel plate");
        tag.putUUID(PARENT_SUBLEVEL_ID_TAG, parent.subLevel().getUniqueId());
        tag.put(
                PARENT_POS_TAG,
                NbtUtils.writeBlockPos(LoadedSubLevelCoordinates.toGlobalBlockPos(parent, parentPos))
        );
    }

    static void restoreSwivelBearingPositionForLoad(
            CompoundTag tag,
            Map<UUID, LoadedSubLevel> loadedSublevels
    ) {
        if (!tag.contains(SWIVEL_PLATE_POS_TAG) && !tag.contains(SWIVEL_CHILD_SUBLEVEL_ID_TAG)) {
            return;
        }
        UUID childBlueprintId = requireUuid(
                tag,
                SWIVEL_CHILD_SUBLEVEL_ID_TAG,
                "Simulated swivel bearing"
        );
        LoadedSubLevel child = requireLoadedSubLevel(
                loadedSublevels,
                childBlueprintId,
                "Simulated swivel child"
        );
        BlockPos platePos = requireBlockPos(tag, SWIVEL_PLATE_POS_TAG, "Simulated swivel bearing");
        // Simulated maps the UUID through Sable while reading, but does not transform this position.
        tag.put(
                SWIVEL_PLATE_POS_TAG,
                NbtUtils.writeBlockPos(LoadedSubLevelCoordinates.toGlobalBlockPos(child, platePos))
        );
    }

    private static void remapAttachmentForSave(
            CompoundTag tag,
            CapturePlan plan,
            CapturedSubLevel currentSubLevel
    ) {
        BlockPos attachmentPos = requireBlockPos(
                tag,
                ROPE_ATTACHMENT_BLOCK_TAG,
                "Simulated rope attachment"
        );
        CapturedSubLevel mapped = currentSubLevel;
        String rawSubLevelId = tag.getString(ROPE_ATTACHMENT_SUBLEVEL_ID_TAG);
        if (!rawSubLevelId.isBlank()) {
            UUID subLevelId = parseUuid(rawSubLevelId, "Simulated rope attachment sublevel");
            mapped = plan.findByOriginalId(subLevelId);
            if (mapped == null) {
                throw new IllegalArgumentException(
                        "Simulated rope attachment sublevel was not captured: " + subLevelId
                );
            }
            tag.putString(ROPE_ATTACHMENT_SUBLEVEL_ID_TAG, mapped.blueprintId().toString());
        }
        PlotBlockTransform transform = PlotBlockTransform.capture(mapped.subLevel());
        tag.put(
                ROPE_ATTACHMENT_BLOCK_TAG,
                NbtUtils.writeBlockPos(transform.toSavedLocalBlockPos(attachmentPos))
        );
    }

    private static void remapAttachmentForLoad(
            CompoundTag tag,
            Map<UUID, LoadedSubLevel> loadedSublevels,
            LoadedSubLevel currentSubLevel
    ) {
        BlockPos attachmentPos = requireBlockPos(
                tag,
                ROPE_ATTACHMENT_BLOCK_TAG,
                "Simulated rope attachment"
        );
        LoadedSubLevel mapped = currentSubLevel;
        String rawSubLevelId = tag.getString(ROPE_ATTACHMENT_SUBLEVEL_ID_TAG);
        if (!rawSubLevelId.isBlank()) {
            UUID blueprintId = parseUuid(
                    rawSubLevelId,
                    "Simulated rope attachment blueprint sublevel"
            );
            mapped = requireLoadedSubLevel(
                    loadedSublevels,
                    blueprintId,
                    "Simulated rope attachment"
            );
            tag.putString(
                    ROPE_ATTACHMENT_SUBLEVEL_ID_TAG,
                    mapped.subLevel().getUniqueId().toString()
            );
        }
        tag.put(
                ROPE_ATTACHMENT_BLOCK_TAG,
                NbtUtils.writeBlockPos(LoadedSubLevelCoordinates.toGlobalBlockPos(mapped, attachmentPos))
        );
    }

    private static CompoundTag requireCompound(CompoundTag parent, String key, String owner) {
        if (!parent.contains(key, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException(owner + " is missing compound '" + key + "'");
        }
        return parent.getCompound(key);
    }

    private static UUID requireRopeUuid(CompoundTag strandTag) {
        if (strandTag.hasUUID(ROPE_UUID_TAG)) {
            return strandTag.getUUID(ROPE_UUID_TAG);
        }
        String rawUuid = strandTag.getString(ROPE_UUID_TAG);
        if (rawUuid.isBlank()) {
            throw new IllegalArgumentException("Simulated rope strand has no UUID");
        }
        return parseUuid(rawUuid, "Simulated rope UUID");
    }

    private static UUID requireUuid(CompoundTag tag, String key, String owner) {
        if (!tag.hasUUID(key)) {
            throw new IllegalArgumentException(owner + " is missing UUID '" + key + "'");
        }
        return tag.getUUID(key);
    }

    private static BlockPos requireBlockPos(CompoundTag tag, String key, String owner) {
        return NbtUtils.readBlockPos(tag, key).orElseThrow(
                () -> new IllegalArgumentException(owner + " is missing block position '" + key + "'")
        );
    }

    private static LoadedSubLevel requireLoadedSubLevel(
            Map<UUID, LoadedSubLevel> loadedSublevels,
            UUID blueprintId,
            String owner
    ) {
        LoadedSubLevel loaded = loadedSublevels.get(blueprintId);
        if (loaded == null) {
            throw new IllegalArgumentException(
                    owner + " references missing blueprint sublevel " + blueprintId
            );
        }
        return loaded;
    }

    private static UUID parseUuid(String raw, String label) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(label + " is invalid: '" + raw + "'", exception);
        }
    }
}
