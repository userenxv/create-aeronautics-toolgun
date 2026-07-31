package com.enxv.aeronauticsstructuretool;

import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

public final class ModSetup {
    private ModSetup() {
    }

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModTooltips::register);
    }
}
