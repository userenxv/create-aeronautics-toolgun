package com.enxv.aeronauticsstructuretool;

import com.enxv.aeronauticsstructuretool.toolgun.weld.WeldAxisGeometry;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Direction;
import dev.ryanhcode.sable.sublevel.SubLevel;
import org.joml.Vector3d;

public enum BearingAxisMode {
    AUTO("screen.create_aeronautics_toolgun.bearing_axis.auto"),
    X_AXIS("screen.create_aeronautics_toolgun.bearing_axis.x_axis"),
    Z_AXIS("screen.create_aeronautics_toolgun.bearing_axis.z_axis"),
    Y_AXIS("screen.create_aeronautics_toolgun.bearing_axis.y_axis");

    private final String translationKey;

    BearingAxisMode(String translationKey) {
        this.translationKey = translationKey;
    }

    public Component title() {
        return Component.translatable(this.translationKey);
    }

    public BearingAxisMode next() {
        return values()[(this.ordinal() + 1) % values().length];
    }

    public BearingAxisMode previous() {
        return values()[(this.ordinal() + values().length - 1) % values().length];
    }

    public Vector3d resolveWorldAxis(Vector3d firstPoint, Vector3d secondPoint, Direction firstFace, Direction secondFace) {
        return switch (this) {
            case AUTO, Y_AXIS -> WeldAxisGeometry.primarySelectionAxis(firstPoint, secondPoint, firstFace, secondFace);
            case X_AXIS -> WeldAxisGeometry.secondarySelectionAxis(firstPoint, secondPoint, firstFace, secondFace);
            case Z_AXIS -> WeldAxisGeometry.tertiarySelectionAxis(firstPoint, secondPoint, firstFace, secondFace);
        };
    }

    public Vector3d resolveWorldAxis(SubLevel firstSubLevel, Direction firstFace, SubLevel secondSubLevel, Direction secondFace) {
        return switch (this) {
            case AUTO, Y_AXIS -> WeldAxisGeometry.primarySelectionAxis(firstSubLevel, firstFace, secondSubLevel, secondFace);
            case X_AXIS -> WeldAxisGeometry.secondarySelectionAxis(firstSubLevel, firstFace);
            case Z_AXIS -> WeldAxisGeometry.tertiarySelectionAxis(firstSubLevel, firstFace);
        };
    }

    public Vector3d resolveSingleFaceWorldAxis(SubLevel subLevel, Direction face) {
        return switch (this) {
            case AUTO, Y_AXIS -> WeldAxisGeometry.worldAxisFromFace(subLevel, face);
            case X_AXIS -> WeldAxisGeometry.secondarySelectionAxis(subLevel, face);
            case Z_AXIS -> WeldAxisGeometry.tertiarySelectionAxis(subLevel, face);
        };
    }

    public static BearingAxisMode fromName(String raw) {
        if (raw == null || raw.isBlank()) {
            return AUTO;
        }
        try {
            return BearingAxisMode.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown bearing axis mode: " + raw, exception);
        }
    }
}
