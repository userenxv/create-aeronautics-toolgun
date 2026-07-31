package com.enxv.aeronauticsstructuretool.toolgun.constraint.persistence;

public enum PersistentConstraintCoordinateSpace {
    LEGACY_UNVERSIONED(""),
    SAVED_PLOT_LOCAL_V1("saved_plot_local_v1");

    private final String serializedName;

    PersistentConstraintCoordinateSpace(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    static PersistentConstraintCoordinateSpace fromSerializedName(String raw) {
        if (raw == null || raw.isBlank()) {
            return LEGACY_UNVERSIONED;
        }
        for (PersistentConstraintCoordinateSpace value : values()) {
            if (value.serializedName.equals(raw)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unsupported persistent constraint coordinate space '" + raw + "'");
    }
}
