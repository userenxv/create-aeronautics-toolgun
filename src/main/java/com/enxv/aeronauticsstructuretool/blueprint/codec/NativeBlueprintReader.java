package com.enxv.aeronauticsstructuretool.blueprint.codec;

import com.enxv.aeronauticsstructuretool.blueprint.geometry.BlueprintLocalAnchorResolver;
import com.enxv.aeronauticsstructuretool.blueprint.model.NativeBlueprintDocument;
import com.enxv.aeronauticsstructuretool.blueprint.model.SavedSubLevelBlueprint;
import com.enxv.aeronauticsstructuretool.blueprint.runtime.RuntimeContraptionCodec;
import com.enxv.aeronauticsstructuretool.compat.create.CreateBlueprintCompat;
import com.enxv.aeronauticsstructuretool.compat.simulated.SimulatedGlueBlueprintCompat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class NativeBlueprintReader {

    private NativeBlueprintReader() {
    }

    public static NativeBlueprintDocument read(CompoundTag root) throws IOException {
        if (root == null || root.isEmpty()) {
            throw new IOException("blueprint root is empty");
        }
        try {
            String format = root.getString(NativeBlueprintFormat.FORMAT_TAG);
            if (!NativeBlueprintFormat.isSupported(format)) {
                throw new IOException("unsupported native blueprint format: " + format);
            }
            if (!root.hasUUID(NativeBlueprintFormat.ROOT_SUBLEVEL_TAG)) {
                throw new IOException("blueprint root_sublevel UUID is missing");
            }
            if (!root.contains(NativeBlueprintFormat.SUBLEVELS_TAG, Tag.TAG_LIST)) {
                throw new IOException("blueprint sublevels list is missing");
            }

            if (root.contains(NativeBlueprintFormat.SOURCE_MIN_BUILD_HEIGHT_TAG)
                    && !root.contains(NativeBlueprintFormat.SOURCE_MIN_BUILD_HEIGHT_TAG, Tag.TAG_INT)) {
                throw new IOException("blueprint source_min_build_height is not an integer");
            }
            int sourceMinBuildHeight = root.contains(
                    NativeBlueprintFormat.SOURCE_MIN_BUILD_HEIGHT_TAG,
                    Tag.TAG_INT
            )
                    ? root.getInt(NativeBlueprintFormat.SOURCE_MIN_BUILD_HEIGHT_TAG)
                    : NativeBlueprintFormat.LEGACY_V8_MIN_BUILD_HEIGHT;
            UUID rootBlueprintId = root.getUUID(NativeBlueprintFormat.ROOT_SUBLEVEL_TAG);
            Quaterniond rootOrientation = readOptionalQuaternion(
                    root,
                    NativeBlueprintFormat.ROOT_ORIENTATION_TAG
            );
            Vector3d rootRotationOffset = readOptionalVector(
                    root,
                    NativeBlueprintFormat.ROOT_ROTATION_OFFSET_TAG
            );
            List<SavedSubLevelBlueprint> sublevels = readSublevels(root, sourceMinBuildHeight);
            if (sublevels.stream().noneMatch(saved -> saved.blueprintId().equals(rootBlueprintId))) {
                throw new IOException("blueprint root_sublevel does not reference a saved sublevel: " + rootBlueprintId);
            }
            return new NativeBlueprintDocument(
                    format,
                    sourceMinBuildHeight,
                    rootBlueprintId,
                    rootOrientation,
                    rootRotationOffset,
                    sublevels
            );
        } catch (IllegalArgumentException exception) {
            throw new IOException("invalid native blueprint: " + exception.getMessage(), exception);
        }
    }

    private static List<SavedSubLevelBlueprint> readSublevels(
            CompoundTag root,
            int sourceMinBuildHeight
    ) throws IOException {
        ListTag list = root.getList(NativeBlueprintFormat.SUBLEVELS_TAG, Tag.TAG_COMPOUND);
        if (list.isEmpty()) {
            throw new IOException("blueprint sublevels list is empty");
        }
        List<SavedSubLevelBlueprint> sublevels = new ArrayList<>(list.size());
        Set<UUID> blueprintIds = new LinkedHashSet<>();
        double legacyAnchorY = BlueprintLocalAnchorResolver.resolveLegacyAnchorY(root);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            try {
                if (!tag.hasUUID(NativeBlueprintFormat.SUBLEVEL_ID_TAG)) {
                    throw new IllegalArgumentException("missing sublevel_id UUID");
                }
                UUID blueprintId = tag.getUUID(NativeBlueprintFormat.SUBLEVEL_ID_TAG);
                if (!blueprintIds.add(blueprintId)) {
                    throw new IllegalArgumentException("duplicate sublevel_id " + blueprintId);
                }
                if (tag.contains(NativeBlueprintFormat.ORIGINAL_SUBLEVEL_ID_TAG)
                        && !tag.hasUUID(NativeBlueprintFormat.ORIGINAL_SUBLEVEL_ID_TAG)) {
                    throw new IllegalArgumentException("invalid original_sublevel_id UUID");
                }
                if (!tag.contains(NativeBlueprintFormat.PLOT_TAG, Tag.TAG_COMPOUND)
                        || tag.getCompound(NativeBlueprintFormat.PLOT_TAG).isEmpty()) {
                    throw new IllegalArgumentException("missing or empty plot data");
                }
                if (tag.contains(NativeBlueprintFormat.DISABLE_STRUCTURE_COLLISION_TAG)
                        && !tag.contains(NativeBlueprintFormat.DISABLE_STRUCTURE_COLLISION_TAG, Tag.TAG_BYTE)) {
                    throw new IllegalArgumentException("invalid collision-disabled flag");
                }

                CompoundTag plotTag = tag.getCompound(NativeBlueprintFormat.PLOT_TAG);
                UUID originalId = tag.hasUUID(NativeBlueprintFormat.ORIGINAL_SUBLEVEL_ID_TAG)
                        ? tag.getUUID(NativeBlueprintFormat.ORIGINAL_SUBLEVEL_ID_TAG)
                        : blueprintId;
                Vector3d localAnchor = BlueprintLocalAnchorResolver.resolve(tag, plotTag, legacyAnchorY);
                sublevels.add(new SavedSubLevelBlueprint(
                        blueprintId,
                        originalId,
                        tag.getString(NativeBlueprintFormat.NAME_TAG),
                        plotTag,
                        RuntimeContraptionCodec.readList(tag, NativeBlueprintFormat.RUNTIME_CONTRAPTIONS_TAG),
                        readVector(tag, NativeBlueprintFormat.RELATIVE_POSITION_TAG),
                        readOptionalVector(tag, NativeBlueprintFormat.RELATIVE_ROTATION_OFFSET_TAG),
                        readOptionalQuaternion(tag, NativeBlueprintFormat.RELATIVE_ORIENTATION_TAG),
                        tag.getBoolean(NativeBlueprintFormat.DISABLE_STRUCTURE_COLLISION_TAG),
                        sourceMinBuildHeight,
                        localAnchor,
                        CreateBlueprintCompat.readSuperGlueEntries(tag),
                        SimulatedGlueBlueprintCompat.readHoneyGlueEntries(tag)
                ));
            } catch (IllegalArgumentException exception) {
                throw new IOException("invalid sublevel entry at index " + i + ": " + exception.getMessage(), exception);
            } catch (IOException exception) {
                throw new IOException("invalid sublevel entry at index " + i, exception);
            }
        }
        return sublevels;
    }

    private static Vector3d readVector(CompoundTag parent, String key) {
        if (!parent.contains(key, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("missing vector '" + key + "'");
        }
        return NbtTransformCodec.readVector(parent.getCompound(key), key);
    }

    private static Quaterniond readQuaternion(CompoundTag parent, String key) {
        if (!parent.contains(key, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("missing quaternion '" + key + "'");
        }
        return NbtTransformCodec.readQuaternion(parent.getCompound(key), key);
    }

    private static Vector3d readOptionalVector(CompoundTag parent, String key) {
        return parent.contains(key)
                ? readVector(parent, key)
                : new Vector3d();
    }

    private static Quaterniond readOptionalQuaternion(CompoundTag parent, String key) {
        return parent.contains(key)
                ? readQuaternion(parent, key)
                : new Quaterniond();
    }
}
