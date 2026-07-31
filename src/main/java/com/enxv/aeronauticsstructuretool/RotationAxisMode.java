package com.enxv.aeronauticsstructuretool;

import net.minecraft.network.chat.Component;
import org.joml.Vector3d;

public enum RotationAxisMode {
    X("screen.create_aeronautics_toolgun.rotate_axis.x", new Vector3d(1.0D, 0.0D, 0.0D)),
    Y("screen.create_aeronautics_toolgun.rotate_axis.y", new Vector3d(0.0D, 1.0D, 0.0D)),
    Z("screen.create_aeronautics_toolgun.rotate_axis.z", new Vector3d(0.0D, 0.0D, 1.0D));

    private final String translationKey;
    private final Vector3d localAxis;

    RotationAxisMode(String translationKey, Vector3d localAxis) {
        this.translationKey = translationKey;
        this.localAxis = localAxis;
    }

    public Component title() {
        return Component.translatable(this.translationKey);
    }

    public Vector3d localAxis() {
        return new Vector3d(this.localAxis);
    }

    public RotationAxisMode next() {
        return values()[(this.ordinal() + 1) % values().length];
    }

    public static RotationAxisMode fromName(String raw) {
        if (raw == null || raw.isBlank()) {
            return X;
        }
        try {
            return RotationAxisMode.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown rotation axis mode: " + raw, exception);
        }
    }
}
