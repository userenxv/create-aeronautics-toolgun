package com.enxv.aeronauticsstructuretool.toolgun.constraint.persistence;

import com.enxv.aeronauticsstructuretool.ConnectionMode;
import com.enxv.aeronauticsstructuretool.blueprint.codec.NbtTransformCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import static com.enxv.aeronauticsstructuretool.toolgun.constraint.persistence.ConstraintNbtKeys.*;

final class ConstraintPersistentCodec {
    private ConstraintPersistentCodec() {
    }

    static PersistentConstraint read(CompoundTag entry) {
        return new PersistentConstraint(
                readCoordinateSpace(entry),
                requireString(entry, DIMENSION_TAG),
                requireUuid(entry, CONSTRAINT_ID_TAG),
                requireUuid(entry, FIRST_SUBLEVEL_TAG),
                requireUuid(entry, SECOND_SUBLEVEL_TAG),
                readConnectionMode(entry),
                readOptionalVector(entry, FIRST_DISPLAY_LOCAL_TAG),
                readOptionalVector(entry, SECOND_DISPLAY_LOCAL_TAG),
                readRequiredVector(entry, FIRST_LOCAL_TAG),
                readRequiredVector(entry, SECOND_LOCAL_TAG),
                readOptionalVector(entry, FIRST_WORLD_TAG),
                readOptionalVector(entry, SECOND_WORLD_TAG),
                readOptionalVector(entry, FIRST_PLOT_LOCAL_TAG),
                readOptionalVector(entry, SECOND_PLOT_LOCAL_TAG),
                readOptionalVector(entry, FIRST_DISPLAY_PLOT_LOCAL_TAG),
                readOptionalVector(entry, SECOND_DISPLAY_PLOT_LOCAL_TAG),
                readRequiredQuaternion(entry, RELATIVE_ORIENTATION_TAG),
                readOptionalVector(entry, FIRST_AXIS_LOCAL_TAG),
                readOptionalVector(entry, SECOND_AXIS_LOCAL_TAG)
        );
    }

    static CompoundTag write(PersistentConstraint constraint) {
        CompoundTag entry = new CompoundTag();
        if (constraint.coordinateSpace() != PersistentConstraintCoordinateSpace.LEGACY_UNVERSIONED) {
            entry.putString(COORDINATE_SPACE_TAG, constraint.coordinateSpace().serializedName());
        }
        entry.putString(DIMENSION_TAG, constraint.dimensionId());
        entry.putUUID(CONSTRAINT_ID_TAG, constraint.constraintId());
        entry.putUUID(FIRST_SUBLEVEL_TAG, constraint.firstSubLevelId());
        entry.putUUID(SECOND_SUBLEVEL_TAG, constraint.secondSubLevelId());
        entry.putString(MODE_TAG, constraint.connectionMode().name());
        putOptionalVector(entry, FIRST_DISPLAY_LOCAL_TAG, constraint.firstDisplayLocalPoint());
        putOptionalVector(entry, SECOND_DISPLAY_LOCAL_TAG, constraint.secondDisplayLocalPoint());
        entry.put(FIRST_LOCAL_TAG, NbtTransformCodec.writeVector(constraint.firstLocalPoint()));
        entry.put(SECOND_LOCAL_TAG, NbtTransformCodec.writeVector(constraint.secondLocalPoint()));
        putOptionalVector(entry, FIRST_WORLD_TAG, constraint.firstWorldPoint());
        putOptionalVector(entry, SECOND_WORLD_TAG, constraint.secondWorldPoint());
        putOptionalVector(entry, FIRST_PLOT_LOCAL_TAG, constraint.firstPlotLocalPoint());
        putOptionalVector(entry, SECOND_PLOT_LOCAL_TAG, constraint.secondPlotLocalPoint());
        putOptionalVector(entry, FIRST_DISPLAY_PLOT_LOCAL_TAG, constraint.firstDisplayPlotLocalPoint());
        putOptionalVector(entry, SECOND_DISPLAY_PLOT_LOCAL_TAG, constraint.secondDisplayPlotLocalPoint());
        entry.put(RELATIVE_ORIENTATION_TAG, NbtTransformCodec.writeQuaternion(constraint.relativeOrientation()));
        putOptionalVector(entry, FIRST_AXIS_LOCAL_TAG, constraint.firstAxisLocal());
        putOptionalVector(entry, SECOND_AXIS_LOCAL_TAG, constraint.secondAxisLocal());
        return entry;
    }

    private static ConnectionMode readConnectionMode(CompoundTag entry) {
        String raw = requireString(entry, MODE_TAG);
        try {
            return ConnectionMode.valueOf(raw);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported persistent constraint mode '" + raw + "'", exception);
        }
    }

    private static PersistentConstraintCoordinateSpace readCoordinateSpace(CompoundTag entry) {
        if (!entry.contains(COORDINATE_SPACE_TAG)) {
            return PersistentConstraintCoordinateSpace.LEGACY_UNVERSIONED;
        }
        if (!entry.contains(COORDINATE_SPACE_TAG, Tag.TAG_STRING)) {
            throw new IllegalArgumentException(
                    "Persistent constraint tag '" + COORDINATE_SPACE_TAG + "' must be a string"
            );
        }
        return PersistentConstraintCoordinateSpace.fromSerializedName(entry.getString(COORDINATE_SPACE_TAG));
    }

    private static String requireString(CompoundTag parent, String key) {
        if (!parent.contains(key, Tag.TAG_STRING)) {
            throw new IllegalArgumentException("Persistent constraint is missing string tag '" + key + "'");
        }
        String value = parent.getString(key);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Persistent constraint string tag '" + key + "' is blank");
        }
        return value;
    }

    private static java.util.UUID requireUuid(CompoundTag parent, String key) {
        if (!parent.hasUUID(key)) {
            throw new IllegalArgumentException("Persistent constraint is missing UUID tag '" + key + "'");
        }
        return parent.getUUID(key);
    }

    private static Vector3d readRequiredVector(CompoundTag parent, String key) {
        requireCompound(parent, key);
        return NbtTransformCodec.readVector(parent.getCompound(key), "persistent constraint " + key);
    }

    private static Vector3d readOptionalVector(CompoundTag parent, String key) {
        if (!parent.contains(key)) {
            return null;
        }
        if (!parent.contains(key, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Persistent constraint tag '" + key + "' must be a compound");
        }
        return NbtTransformCodec.readVector(parent.getCompound(key), "persistent constraint " + key);
    }

    private static Quaterniond readRequiredQuaternion(CompoundTag parent, String key) {
        requireCompound(parent, key);
        return NbtTransformCodec.readQuaternion(parent.getCompound(key), "persistent constraint " + key);
    }

    private static void requireCompound(CompoundTag parent, String key) {
        if (!parent.contains(key, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Persistent constraint is missing compound tag '" + key + "'");
        }
    }

    private static void putOptionalVector(CompoundTag parent, String key, Vector3d value) {
        if (value != null) {
            parent.put(key, NbtTransformCodec.writeVector(value));
        }
    }
}
