package com.enxv.aeronauticsstructuretool;

import net.minecraft.network.chat.Component;

public enum WeldSelectionMode {
    FREE("screen.create_aeronautics_toolgun.weld_selection_mode.free"),
    FACE_POINTS_18("screen.create_aeronautics_toolgun.weld_selection_mode.face_points_18"),
    FACE_POINTS("screen.create_aeronautics_toolgun.weld_selection_mode.face_points");

    private final String translationKey;

    WeldSelectionMode(String translationKey) {
        this.translationKey = translationKey;
    }

    public WeldSelectionMode next() {
        WeldSelectionMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public WeldSelectionMode previous() {
        WeldSelectionMode[] values = values();
        return values[(ordinal() + values.length - 1) % values.length];
    }

    public Component title() {
        return Component.translatable(this.translationKey);
    }

    public static WeldSelectionMode fromName(String raw) {
        if (raw == null || raw.isBlank()) {
            return FACE_POINTS;
        }
        for (WeldSelectionMode mode : values()) {
            if (mode.name().equalsIgnoreCase(raw)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown weld selection mode: " + raw);
    }
}
