package com.enxv.aeronauticsstructuretool.toolgun;

import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.io.IOException;

public final class ToolgunTransformValidation {
    private ToolgunTransformValidation() {
    }

    public static boolean isFinite(Vector3d vector) {
        return vector != null
                && Double.isFinite(vector.x)
                && Double.isFinite(vector.y)
                && Double.isFinite(vector.z);
    }

    public static boolean isFinite(Quaterniond quaternion) {
        return quaternion != null
                && Double.isFinite(quaternion.x)
                && Double.isFinite(quaternion.y)
                && Double.isFinite(quaternion.z)
                && Double.isFinite(quaternion.w)
                && quaternion.lengthSquared() > 1.0E-12D;
    }

    public static void requireFinite(double value, String field) throws IOException {
        if (!Double.isFinite(value)) {
            throw new IOException(field + " is non-finite");
        }
    }

    public static void requireFinite(Vector3d vector, String field) throws IOException {
        if (!isFinite(vector)) {
            throw new IOException(field + " is missing or non-finite");
        }
    }

    public static void requireFiniteOptional(Vector3d vector, String field) throws IOException {
        if (vector != null) {
            requireFinite(vector, field);
        }
    }

    public static void requireFinite(Quaterniond quaternion, String field) throws IOException {
        if (!isFinite(quaternion)) {
            throw new IOException(field + " is missing, non-finite, or zero");
        }
    }

    public static Vector3d requireAxis(Vector3d axis, String field) throws IOException {
        requireFinite(axis, field);
        if (axis.lengthSquared() <= 1.0E-6D) {
            throw new IOException(field + " is zero");
        }
        return new Vector3d(axis).normalize();
    }
}
