package com.enxv.aeronauticsstructuretool.blueprint.runtime;

import com.enxv.aeronauticsstructuretool.BlueprintMaterialSummary;
import com.enxv.aeronauticsstructuretool.RuntimeContraptionBlueprint;
import com.enxv.aeronauticsstructuretool.blueprint.material.MaterialCountCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.enxv.aeronauticsstructuretool.blueprint.codec.NbtTagValidator.requireCompoundList;
import static com.enxv.aeronauticsstructuretool.blueprint.codec.NbtTagValidator.requireOptionalType;

public final class RuntimeContraptionCodec {
    public static final String CREATE_CONTROLLED_KIND = "create_controlled";
    public static final String CBC_PITCH_KIND = "cbc_pitch";

    private static final String KIND_TAG = "kind";
    private static final String CONTROLLER_TAG = "controller";
    private static final String CONTRAPTION_TAG = "contraption";
    private static final String ENTITY_CLASS_TAG = "entity_class";
    private static final String ANGLE_TAG = "angle";
    private static final String YAW_TAG = "yaw";
    private static final String PITCH_TAG = "pitch";
    private static final String INITIAL_ORIENTATION_TAG = "initial_orientation";

    private RuntimeContraptionCodec() {
    }

    public static ListTag writeList(List<RuntimeContraptionBlueprint> blueprints) {
        ListTag list = new ListTag();
        for (RuntimeContraptionBlueprint blueprint : blueprints) {
            list.add(write(blueprint));
        }
        return list;
    }

    public static List<RuntimeContraptionBlueprint> readList(CompoundTag root, String key) throws IOException {
        if (!root.contains(key)) {
            return List.of();
        }
        if (!root.contains(key, Tag.TAG_LIST)) {
            throw new IOException("runtime contraption entry '" + key + "' is not a list");
        }

        ListTag list = requireCompoundList(
                root,
                key,
                "runtime contraption entry '" + key + "'",
                false
        );
        List<RuntimeContraptionBlueprint> blueprints = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            try {
                blueprints.add(read(list.getCompound(i)));
            } catch (IOException exception) {
                throw new IOException("invalid runtime contraption at index " + i, exception);
            }
        }
        return blueprints;
    }

    public static Direction parseDirection(String serialized) {
        for (Direction direction : Direction.values()) {
            if (direction.getSerializedName().equals(serialized)) {
                return direction;
            }
        }
        throw new IllegalArgumentException("invalid runtime contraption orientation: " + serialized);
    }

    private static CompoundTag write(RuntimeContraptionBlueprint blueprint) {
        CompoundTag tag = new CompoundTag();
        tag.putString(KIND_TAG, blueprint.kind());
        tag.put(CONTROLLER_TAG, NbtUtils.writeBlockPos(blueprint.controllerLocalPos()));
        tag.put(CONTRAPTION_TAG, blueprint.contraptionTag().copy());
        if (blueprint.entityClassName() != null && !blueprint.entityClassName().isBlank()) {
            tag.putString(ENTITY_CLASS_TAG, blueprint.entityClassName());
        }
        if (blueprint.angle() != 0.0F) {
            tag.putFloat(ANGLE_TAG, blueprint.angle());
        }
        if (blueprint.yaw() != 0.0F) {
            tag.putFloat(YAW_TAG, blueprint.yaw());
        }
        if (blueprint.pitch() != 0.0F) {
            tag.putFloat(PITCH_TAG, blueprint.pitch());
        }
        if (blueprint.initialOrientation() != null && !blueprint.initialOrientation().isBlank()) {
            tag.putString(INITIAL_ORIENTATION_TAG, blueprint.initialOrientation());
        }
        tag.put(
                BlueprintMaterialSummary.RUNTIME_ITEMS_TAG,
                BlueprintMaterialSummary.writeItemCounts(blueprint.materialItemCounts())
        );
        return tag;
    }

    private static RuntimeContraptionBlueprint read(CompoundTag tag) throws IOException {
        if (!tag.contains(KIND_TAG, Tag.TAG_STRING)) {
            throw new IOException("runtime contraption kind is missing");
        }
        String kind = tag.getString(KIND_TAG);
        if (!(CREATE_CONTROLLED_KIND.equals(kind) || CBC_PITCH_KIND.equals(kind))) {
            throw new IOException("unsupported runtime contraption kind '" + kind + "'");
        }
        BlockPos controller = NbtUtils.readBlockPos(tag, CONTROLLER_TAG)
                .orElseThrow(() -> new IOException("missing runtime contraption controller position"));
        if (!tag.contains(CONTRAPTION_TAG, Tag.TAG_COMPOUND)) {
            throw new IOException("missing runtime contraption data");
        }
        CompoundTag contraptionTag = tag.getCompound(CONTRAPTION_TAG);
        if (contraptionTag.isEmpty()) {
            throw new IOException("empty runtime contraption data");
        }

        requireOptionalType(tag, ENTITY_CLASS_TAG, Tag.TAG_STRING,
                "runtime contraption '" + ENTITY_CLASS_TAG + "'");
        requireOptionalType(tag, ANGLE_TAG, Tag.TAG_ANY_NUMERIC,
                "runtime contraption '" + ANGLE_TAG + "'");
        requireOptionalType(tag, YAW_TAG, Tag.TAG_ANY_NUMERIC,
                "runtime contraption '" + YAW_TAG + "'");
        requireOptionalType(tag, PITCH_TAG, Tag.TAG_ANY_NUMERIC,
                "runtime contraption '" + PITCH_TAG + "'");
        requireOptionalType(tag, INITIAL_ORIENTATION_TAG, Tag.TAG_STRING,
                "runtime contraption '" + INITIAL_ORIENTATION_TAG + "'");

        String orientation = tag.getString(INITIAL_ORIENTATION_TAG);
        if (!orientation.isBlank()) {
            try {
                parseDirection(orientation);
            } catch (IllegalArgumentException exception) {
                throw new IOException(exception.getMessage(), exception);
            }
        }
        Map<String, Long> materialItems = Map.of();
        if (tag.contains(BlueprintMaterialSummary.RUNTIME_ITEMS_TAG)) {
            Tag rawItems = tag.get(BlueprintMaterialSummary.RUNTIME_ITEMS_TAG);
            if (!(rawItems instanceof ListTag itemList)) {
                throw new IOException("runtime contraption material items are not a list");
            }
            materialItems = MaterialCountCodec.readCountsStrict(
                    itemList,
                    "runtime contraption material items"
            );
        }
        return new RuntimeContraptionBlueprint(
                kind,
                controller,
                contraptionTag,
                tag.getString(ENTITY_CLASS_TAG),
                tag.getFloat(ANGLE_TAG),
                tag.getFloat(YAW_TAG),
                tag.getFloat(PITCH_TAG),
                orientation,
                materialItems
        );
    }

}
