package com.enxv.aeronauticsstructuretool.compat.mianbao;

import net.minecraft.resources.ResourceLocation;

public final class MianbaoModernWarfareCompatRegressionCheck {
    private MianbaoModernWarfareCompatRegressionCheck() {
    }

    public static void main(String[] args) {
        requireDelay("grenadecannon", 5);
        requireDelay("mortar", 5);
        requireDelay("autocannonbreech", 3);
        requireDelay("rotarycannonbreech", 1);
        requireDelay("flamelauncher", 1);
        requireDelay("radar", 1);
        requireDelay("portable_aps", 1);
        requireDelay("smokelaunchersixing_h_6", 20);
        if (MianbaoModernWarfareBlueprintCompat.supportedRecurringBlockCount() != 61) {
            throw new AssertionError("Unexpected ModernWarfare recurring-block compatibility count");
        }
        if (MianbaoModernWarfareBlueprintCompat.initialTickDelay(
                ResourceLocation.fromNamespaceAndPath("minecraft", "tnt")
        ) != null) {
            throw new AssertionError("Compatibility must not schedule blocks outside ModernWarfare");
        }
        if (MianbaoModernWarfareBlueprintCompat.initialTickDelay(
                ResourceLocation.fromNamespaceAndPath("mianbaos_modernwarfare", "unknown")
        ) != null) {
            throw new AssertionError("Compatibility must not guess unknown ModernWarfare blocks");
        }
    }

    private static void requireDelay(String path, int expected) {
        Integer actual = MianbaoModernWarfareBlueprintCompat.initialTickDelay(
                ResourceLocation.fromNamespaceAndPath("mianbaos_modernwarfare", path)
        );
        if (actual == null || actual != expected) {
            throw new AssertionError(path + " expected delay " + expected + ", got " + actual);
        }
    }
}
