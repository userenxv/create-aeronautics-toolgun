package com.enxv.aeronauticsstructuretool;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class SurvivalToolgunConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue ALLOW_SAVE;
    private static final ModConfigSpec.BooleanValue ALLOW_QUERY;
    private static final ModConfigSpec.BooleanValue ALLOW_WELD;
    private static final ModConfigSpec.BooleanValue ALLOW_SIMPLE_WELD;
    private static final ModConfigSpec.BooleanValue ALLOW_TRANSLATE;
    private static final ModConfigSpec.BooleanValue ALLOW_ROTATE;
    private static final ModConfigSpec.BooleanValue ALLOW_DISCONNECT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("survivalToolgun");
        ALLOW_SAVE = builder.comment("Allow the survival structure tool gun to save physical structures as blueprints.")
                .define("allowSave", true);
        ALLOW_QUERY = builder.comment("Allow the survival structure tool gun to query nearby physical structures.")
                .define("allowQuery", true);
        ALLOW_WELD = builder.comment("Allow the survival structure tool gun to create constraints with the weld mode.")
                .define("allowWeld", true);
        ALLOW_SIMPLE_WELD = builder.comment("Allow the survival structure tool gun to use weld and rotate mode.")
                .define("allowSimpleWeld", true);
        ALLOW_TRANSLATE = builder.comment("Allow the survival structure tool gun to translate physical structures.")
                .define("allowTranslate", true);
        ALLOW_ROTATE = builder.comment("Allow the survival structure tool gun to rotate physical structures.")
                .define("allowRotate", true);
        ALLOW_DISCONNECT = builder.comment("Allow the survival structure tool gun to remove tracked toolgun constraints.")
                .define("allowDisconnect", true);
        builder.pop();
        SPEC = builder.build();
    }

    private SurvivalToolgunConfig() {
    }

    public static boolean allowSave() {
        return ALLOW_SAVE.getAsBoolean();
    }

    public static boolean allowQuery() {
        return ALLOW_QUERY.getAsBoolean();
    }

    public static boolean allowWeld() {
        return ALLOW_WELD.getAsBoolean();
    }

    public static boolean allowSimpleWeld() {
        return ALLOW_SIMPLE_WELD.getAsBoolean();
    }

    public static boolean allowTranslate() {
        return ALLOW_TRANSLATE.getAsBoolean();
    }

    public static boolean allowRotate() {
        return ALLOW_ROTATE.getAsBoolean();
    }

    public static boolean allowDisconnect() {
        return ALLOW_DISCONNECT.getAsBoolean();
    }
}
