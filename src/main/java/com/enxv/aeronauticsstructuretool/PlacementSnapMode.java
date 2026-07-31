package com.enxv.aeronauticsstructuretool;

public enum PlacementSnapMode {
    LEGACY("Legacy"),
    FACE_CENTER("Face"),
    HIT("Hit");

    private final String label;

    PlacementSnapMode(String label) {
        this.label = label;
    }

    public String label() {
        return this.label;
    }

    public PlacementSnapMode next() {
        PlacementSnapMode[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    public PlacementSnapMode previous() {
        PlacementSnapMode[] values = values();
        return values[(this.ordinal() + values.length - 1) % values.length];
    }

    public static PlacementSnapMode fromName(String raw) {
        if (raw == null || raw.isBlank()) {
            return LEGACY;
        }
        for (PlacementSnapMode value : values()) {
            if (value.name().equalsIgnoreCase(raw)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown placement snap mode: " + raw);
    }
}
