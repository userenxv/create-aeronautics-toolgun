package com.enxv.aeronauticsstructuretool.toolgun.magnetic;

import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class MagneticDragControlMath {
    private MagneticDragControlMath() {
    }

    public static Quaterniond cameraOrientation(
            Vector3dc forward,
            Vector3dc right,
            Vector3dc up,
            Quaterniond destination
    ) {
        return new Matrix3d()
                .setColumn(0, right)
                .setColumn(1, up)
                .setColumn(2, new Vector3d(forward).negate())
                .getNormalizedRotation(destination)
                .normalize();
    }

    public static Quaterniond applyReferenceFrameDelta(
            Quaterniondc previousFrame,
            Quaterniondc currentFrame,
            Quaterniondc orientation,
            Quaterniond destination
    ) {
        Quaterniond frameDelta = new Quaterniond(currentFrame)
                .mul(new Quaterniond(previousFrame).conjugate())
                .normalize();
        return frameDelta.mul(orientation, destination).normalize();
    }

    /** Extracts world-Y rotation while preserving heading. */
    public static Quaterniond uprightOrientation(
            Quaterniondc orientation,
            Quaterniond destination
    ) {
        double lengthSquared = orientation.w() * orientation.w()
                + orientation.y() * orientation.y();
        if (lengthSquared < 1.0E-12D) {
            // X/Z half-turns make twist projection singular. Recover yaw from
            // forward; a vertical direction has no yaw and resolves to identity.
            Vector3d forward = orientation.transform(
                    new Vector3d(0.0D, 0.0D, -1.0D),
                    new Vector3d()
            );
            double horizontalLengthSquared = forward.x * forward.x + forward.z * forward.z;
            if (horizontalLengthSquared < 1.0E-12D) {
                return destination.identity();
            }
            double yaw = Math.atan2(forward.x, -forward.z);
            return destination.fromAxisAngleRad(0.0D, 1.0D, 0.0D, yaw).normalize();
        }
        double inverseLength = 1.0D / Math.sqrt(lengthSquared);
        return destination
                .set(0.0D, orientation.y() * inverseLength, 0.0D,
                        orientation.w() * inverseLength)
                .normalize();
    }
}
