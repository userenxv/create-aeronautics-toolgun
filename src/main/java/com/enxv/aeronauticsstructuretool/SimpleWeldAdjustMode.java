package com.enxv.aeronauticsstructuretool;

import net.minecraft.network.chat.Component;

public enum SimpleWeldAdjustMode {
    ROTATE("screen.create_aeronautics_toolgun.simple_weld.adjust.rotate"),
    TRANSLATE("screen.create_aeronautics_toolgun.simple_weld.adjust.translate");

    private final String translationKey;

    SimpleWeldAdjustMode(String translationKey) {
        this.translationKey = translationKey;
    }

    public Component title() {
        return Component.translatable(this.translationKey);
    }

    public SimpleWeldAdjustMode toggle() {
        return this == ROTATE ? TRANSLATE : ROTATE;
    }
}
