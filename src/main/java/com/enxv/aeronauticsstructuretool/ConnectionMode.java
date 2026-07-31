package com.enxv.aeronauticsstructuretool;

import net.minecraft.network.chat.Component;

public enum ConnectionMode {
    FIXED("screen.create_aeronautics_toolgun.connection.fixed"),
    BEARING("screen.create_aeronautics_toolgun.connection.bearing"),
    FREE("screen.create_aeronautics_toolgun.connection.free");

    private final String translationKey;

    ConnectionMode(String translationKey) {
        this.translationKey = translationKey;
    }

    public Component title() {
        return Component.translatable(this.translationKey);
    }

    public ConnectionMode next() {
        return values()[(this.ordinal() + 1) % values().length];
    }

    public ConnectionMode previous() {
        return values()[(this.ordinal() + values().length - 1) % values().length];
    }

    public static ConnectionMode fromName(String raw) {
        if (raw == null || raw.isBlank()) {
            return FIXED;
        }
        try {
            return ConnectionMode.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown connection mode: " + raw, exception);
        }
    }
}
