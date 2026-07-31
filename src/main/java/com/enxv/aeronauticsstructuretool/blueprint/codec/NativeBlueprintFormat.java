package com.enxv.aeronauticsstructuretool.blueprint.codec;

public final class NativeBlueprintFormat {
    public static final int LEGACY_V8_MIN_BUILD_HEIGHT = -64;

    public static final String FORMAT_TAG = "format";
    public static final String NAME_TAG = "name";
    public static final String PLOT_TAG = "plot";
    public static final String SUBLEVELS_TAG = "sublevels";
    public static final String ROOT_SUBLEVEL_TAG = "root_sublevel";
    public static final String ROOT_ORIENTATION_TAG = "root_orientation";
    public static final String ROOT_ROTATION_OFFSET_TAG = "root_rotation_offset";
    public static final String SOURCE_MIN_BUILD_HEIGHT_TAG = "source_min_build_height";
    public static final String SUBLEVEL_ID_TAG = "sublevel_id";
    public static final String ORIGINAL_SUBLEVEL_ID_TAG = "original_sublevel_id";
    public static final String RUNTIME_CONTRAPTIONS_TAG = "runtime_contraptions";
    public static final String RELATIVE_POSITION_TAG = "relative_position";
    public static final String RELATIVE_ROTATION_OFFSET_TAG = "relative_rotation_offset";
    public static final String RELATIVE_ORIENTATION_TAG = "relative_orientation";
    public static final String LOCAL_ANCHOR_TAG = "local_anchor";
    public static final String DISABLE_STRUCTURE_COLLISION_TAG = "AstDisableStructureCollision";
    public static final String SABLE_BLUEPRINT_API_SIDECAR_TAG = "sable_blueprint_api_sidecar";

    public static final String FORMAT_V8 = "enxv_aeronautics_plot_print_v8";
    public static final String FORMAT_V9 = "enxv_aeronautics_plot_print_v9";

    // Existing saves intentionally remain v8 until a format change actually requires a version bump.
    public static final String CURRENT_FORMAT = FORMAT_V8;

    private NativeBlueprintFormat() {
    }

    public static boolean isSupported(String format) {
        return FORMAT_V8.equals(format) || FORMAT_V9.equals(format);
    }
}
