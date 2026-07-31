package com.enxv.aeronauticsstructuretool.blueprint.codec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class NbtTransformCodec {
    private static final double MIN_QUATERNION_LENGTH_SQUARED = 1.0E-12D;

    private NbtTransformCodec() {
    }

    public static CompoundTag writeVector(Vector3d vector) {
        requireFinite(vector.x, "vector.x");
        requireFinite(vector.y, "vector.y");
        requireFinite(vector.z, "vector.z");
        CompoundTag tag = new CompoundTag();
        tag.putDouble("x", vector.x);
        tag.putDouble("y", vector.y);
        tag.putDouble("z", vector.z);
        return tag;
    }

    public static Vector3d readVector(CompoundTag tag, String fieldName) {
        requireNumericComponents(tag, fieldName, "x", "y", "z");
        double x = tag.getDouble("x");
        double y = tag.getDouble("y");
        double z = tag.getDouble("z");
        requireFinite(x, fieldName + ".x");
        requireFinite(y, fieldName + ".y");
        requireFinite(z, fieldName + ".z");
        return new Vector3d(x, y, z);
    }

    public static CompoundTag writeQuaternion(Quaterniond quaternion) {
        requireFinite(quaternion.x, "quaternion.x");
        requireFinite(quaternion.y, "quaternion.y");
        requireFinite(quaternion.z, "quaternion.z");
        requireFinite(quaternion.w, "quaternion.w");
        if (quaternion.lengthSquared() <= MIN_QUATERNION_LENGTH_SQUARED) {
            throw new IllegalArgumentException("cannot serialize a zero quaternion");
        }
        CompoundTag tag = new CompoundTag();
        tag.putDouble("x", quaternion.x);
        tag.putDouble("y", quaternion.y);
        tag.putDouble("z", quaternion.z);
        tag.putDouble("w", quaternion.w);
        return tag;
    }

    public static Quaterniond readQuaternion(CompoundTag tag, String fieldName) {
        requireNumericComponents(tag, fieldName, "x", "y", "z", "w");
        double x = tag.getDouble("x");
        double y = tag.getDouble("y");
        double z = tag.getDouble("z");
        double w = tag.getDouble("w");
        requireFinite(x, fieldName + ".x");
        requireFinite(y, fieldName + ".y");
        requireFinite(z, fieldName + ".z");
        requireFinite(w, fieldName + ".w");
        Quaterniond quaternion = new Quaterniond(x, y, z, w);
        if (quaternion.lengthSquared() <= MIN_QUATERNION_LENGTH_SQUARED) {
            throw new IllegalArgumentException(fieldName + " is a zero quaternion");
        }
        return quaternion;
    }

    private static void requireNumericComponents(CompoundTag tag, String fieldName, String... components) {
        if (tag == null || tag.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is missing");
        }
        for (String component : components) {
            if (!tag.contains(component, Tag.TAG_ANY_NUMERIC)) {
                throw new IllegalArgumentException(fieldName + " is missing numeric component '" + component + "'");
            }
        }
    }

    private static void requireFinite(double value, String fieldName) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite");
        }
    }
}
