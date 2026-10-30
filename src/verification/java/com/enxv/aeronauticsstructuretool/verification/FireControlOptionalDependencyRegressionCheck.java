package com.enxv.aeronauticsstructuretool.verification;

import com.enxv.aeronauticsstructuretool.compat.sableschematicapi.FireControlBlueprintCompat;
import net.minecraft.nbt.CompoundTag;

import java.util.Map;

/** Run without Sable Schematic API on the classpath: ordinary blueprints must still load. */
public final class FireControlOptionalDependencyRegressionCheck {
    public static void main(String[] args) throws Exception {
        boolean available;
        try {
            Class.forName("dev.rew1nd.sableschematicapi.api.blueprint.BlueprintPlaceSession");
            available = true;
        } catch (ClassNotFoundException expected) {
            available = false;
        }
        if (args.length > 0 && args[0].equals("--require-absent") && available) {
            throw new AssertionError("Run this check without Sable Schematic API");
        }
        FireControlBlueprintCompat.preparePlacement(new CompoundTag(), Map.of()).preparePlot(null, null);
    }
}
