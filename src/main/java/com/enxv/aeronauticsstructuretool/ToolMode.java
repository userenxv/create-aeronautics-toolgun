package com.enxv.aeronauticsstructuretool;

import net.minecraft.network.chat.Component;

public enum ToolMode {
    SAVE("screen.create_aeronautics_toolgun.mode.save"),
    LOAD("screen.create_aeronautics_toolgun.mode.load"),
    DELETE("screen.create_aeronautics_toolgun.mode.delete"),
    NO_COLLISION("screen.create_aeronautics_toolgun.mode.no_collision"),
    GHOST_VEHICLE_TEST("screen.create_aeronautics_toolgun.mode.ghost_vehicle_test"),
    WELD("screen.create_aeronautics_toolgun.mode.weld"),
    SIMPLE_WELD("screen.create_aeronautics_toolgun.mode.simple_weld"),
    TRANSLATE("screen.create_aeronautics_toolgun.mode.translate"),
    ROTATE("screen.create_aeronautics_toolgun.mode.rotate"),
    DISCONNECT("screen.create_aeronautics_toolgun.mode.disconnect");

    private final String translationKey;

    ToolMode(String translationKey) {
        this.translationKey = translationKey;
    }

    public Component title() {
        return Component.translatable(this.translationKey);
    }

    public ToolMode next() {
        return values()[(this.ordinal() + 1) % values().length];
    }
}
