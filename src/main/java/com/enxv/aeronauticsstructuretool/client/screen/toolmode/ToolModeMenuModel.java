package com.enxv.aeronauticsstructuretool.client.screen.toolmode;

import com.enxv.aeronauticsstructuretool.ClientToolState;
import com.enxv.aeronauticsstructuretool.ToolMode;
import com.enxv.aeronauticsstructuretool.ToolPanel;

import net.minecraft.network.chat.Component;

import java.util.Locale;

public final class ToolModeMenuModel {
    private static final LeftTab[] SURVIVAL_TABS = {
            LeftTab.SAVE,
            LeftTab.QUERY,
            LeftTab.TOOLS
    };
    private static final ToolEntry[] SURVIVAL_TOOLS = {
            ToolEntry.SIMPLE_WELD_MODE,
            ToolEntry.CONNECTION_MODE,
            ToolEntry.WELD_SELECTION_MODE,
            ToolEntry.WELD_MODE,
            ToolEntry.TRANSLATE_MODE,
            ToolEntry.ROTATE_MODE,
            ToolEntry.DISCONNECT_MODE,
            ToolEntry.HUD,
            ToolEntry.MAGNETIC_MARKERS,
            ToolEntry.BEARING_AXIS_VISUALS,
            ToolEntry.WELD_ADJUST_STEP,
            ToolEntry.TRANSLATE_STEP,
            ToolEntry.ROTATE_STEP
    };

    private ToolModeMenuModel() {
    }

    public static LeftTab currentTab() {
        if (ClientToolState.getPanel() == ToolPanel.TOOLS) {
            return LeftTab.TOOLS;
        }
        if (ClientToolState.getPanel() == ToolPanel.QUERY) {
            return LeftTab.QUERY;
        }
        return switch (ClientToolState.getMode()) {
            case LOAD -> LeftTab.LOAD;
            case DELETE -> LeftTab.DELETE;
            default -> LeftTab.SAVE;
        };
    }

    public static LeftTab[] availableTabs(boolean survivalRestricted) {
        return survivalRestricted ? SURVIVAL_TABS.clone() : LeftTab.values();
    }

    public static ToolEntry[] availableTools(boolean survivalRestricted) {
        return survivalRestricted ? SURVIVAL_TOOLS.clone() : ToolEntry.values();
    }

    public static boolean canScroll(ToolEntry entry) {
        return switch (entry) {
            case CONNECTION_MODE,
                    WELD_SELECTION_MODE,
                    SNAP,
                    CONNECTED_SUBLEVEL_PROXIMITY,
                    WELD_ADJUST_STEP,
                    TRANSLATE_STEP,
                    ROTATE_STEP,
                    OFFSET_X,
                    OFFSET_Y,
                    OFFSET_Z -> true;
            default -> false;
        };
    }

    public static String value(ToolEntry entry) {
        return switch (entry) {
            case CONNECTION_MODE -> ClientToolState.getConnectionMode().title().getString();
            case WELD_SELECTION_MODE -> ClientToolState.getWeldSelectionMode().title().getString();
            case WELD_MODE -> ClientToolState.getMode() == ToolMode.WELD ? active() : enter();
            case SIMPLE_WELD_MODE -> ClientToolState.getMode() == ToolMode.SIMPLE_WELD ? active() : enter();
            case TRANSLATE_MODE -> ClientToolState.getMode() == ToolMode.TRANSLATE ? active() : enter();
            case ROTATE_MODE -> ClientToolState.getMode() == ToolMode.ROTATE ? active() : enter();
            case DISCONNECT_MODE -> ClientToolState.getMode() == ToolMode.DISCONNECT ? active() : enter();
            case NO_COLLISION_MODE -> ClientToolState.getMode() == ToolMode.NO_COLLISION ? active() : enter();
            case GHOST_VEHICLE_TEST -> ClientToolState.getMode() == ToolMode.GHOST_VEHICLE_TEST ? active() : enter();
            case PREVIEW -> toggleValue(ClientToolState.isPreviewEnabled());
            case HUD -> toggleValue(ClientToolState.isHudEnabled());
            case MAGNETIC_MARKERS -> toggleValue(ClientToolState.isMagneticMarkersEnabled());
            case BEARING_AXIS_VISUALS -> toggleValue(ClientToolState.isBearingAxisVisualsEnabled());
            case AUTO_WELD -> toggleValue(ClientToolState.isAutoWeldEnabled());
            case SNAP -> ClientToolState.getSnapMode().label();
            case CONNECTED_SUBLEVEL_PROXIMITY -> stepValue(ClientToolState.getConnectedSublevelProximity());
            case WELD_ADJUST_STEP -> stepValue(ClientToolState.getWeldAdjustStep());
            case TRANSLATE_STEP -> stepValue(ClientToolState.getTranslateStep());
            case ROTATE_STEP -> ClientToolState.getRotationStep() + "\u00B0";
            case OFFSET_X -> signed(ClientToolState.getOffsetX());
            case OFFSET_Y -> signed(ClientToolState.getOffsetY());
            case OFFSET_Z -> signed(ClientToolState.getOffsetZ());
            case RESET -> Component.translatable("screen.create_aeronautics_toolgun.tool.reset_value").getString();
        };
    }

    public static boolean apply(ToolEntry entry, int delta) {
        return switch (entry) {
            case CONNECTION_MODE -> {
                ClientToolState.cycleConnectionMode(delta);
                yield false;
            }
            case WELD_SELECTION_MODE -> {
                ClientToolState.cycleWeldSelectionMode(delta);
                yield false;
            }
            case WELD_MODE -> enterMode(ToolMode.WELD);
            case SIMPLE_WELD_MODE -> enterMode(ToolMode.SIMPLE_WELD);
            case TRANSLATE_MODE -> enterMode(ToolMode.TRANSLATE);
            case ROTATE_MODE -> enterMode(ToolMode.ROTATE);
            case DISCONNECT_MODE -> enterMode(ToolMode.DISCONNECT);
            case NO_COLLISION_MODE -> enterMode(ToolMode.NO_COLLISION);
            case GHOST_VEHICLE_TEST -> enterMode(ToolMode.GHOST_VEHICLE_TEST);
            case PREVIEW -> {
                ClientToolState.togglePreviewEnabled();
                yield false;
            }
            case HUD -> {
                ClientToolState.toggleHudEnabled();
                yield false;
            }
            case MAGNETIC_MARKERS -> {
                ClientToolState.toggleMagneticMarkersEnabled();
                yield false;
            }
            case BEARING_AXIS_VISUALS -> {
                ClientToolState.toggleBearingAxisVisualsEnabled();
                yield false;
            }
            case AUTO_WELD -> {
                ClientToolState.toggleAutoWeldEnabled();
                yield false;
            }
            case SNAP -> {
                ClientToolState.cycleSnapMode(delta);
                yield false;
            }
            case CONNECTED_SUBLEVEL_PROXIMITY -> {
                ClientToolState.adjustConnectedSublevelProximity(delta);
                yield false;
            }
            case WELD_ADJUST_STEP -> {
                ClientToolState.cycleWeldAdjustStep(delta);
                yield false;
            }
            case TRANSLATE_STEP -> {
                ClientToolState.cycleTranslateStep(delta);
                yield false;
            }
            case ROTATE_STEP -> {
                ClientToolState.cycleRotationStep(delta);
                yield false;
            }
            case OFFSET_X -> {
                ClientToolState.adjustOffsetX(direction(delta));
                yield false;
            }
            case OFFSET_Y -> {
                ClientToolState.adjustOffsetY(direction(delta));
                yield false;
            }
            case OFFSET_Z -> {
                ClientToolState.adjustOffsetZ(direction(delta));
                yield false;
            }
            case RESET -> {
                ClientToolState.resetPlacementTransform();
                yield false;
            }
        };
    }

    private static boolean enterMode(ToolMode mode) {
        ClientToolState.setMode(mode);
        return true;
    }

    private static int direction(int delta) {
        return delta > 0 ? 1 : -1;
    }

    private static String enter() {
        return Component.translatable("screen.create_aeronautics_toolgun.tool.enter").getString();
    }

    private static String active() {
        return Component.translatable("screen.create_aeronautics_toolgun.tool.active").getString();
    }

    public static String toggleValue(boolean enabled) {
        return Component.translatable(enabled
                ? "screen.create_aeronautics_toolgun.toggle.on"
                : "screen.create_aeronautics_toolgun.toggle.off").getString();
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    public static String stepValue(double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0E-6D) {
            return (int) Math.rint(value) + "b";
        }
        String raw = String.format(Locale.ROOT, "%.2f", value);
        while (raw.endsWith("0")) {
            raw = raw.substring(0, raw.length() - 1);
        }
        if (raw.endsWith(".")) {
            raw = raw.substring(0, raw.length() - 1);
        }
        return raw + "b";
    }

    public enum LeftTab {
        SAVE("screen.create_aeronautics_toolgun.mode.save"),
        LOAD("screen.create_aeronautics_toolgun.mode.load"),
        DELETE("screen.create_aeronautics_toolgun.mode.delete"),
        QUERY("screen.create_aeronautics_toolgun.mode.query"),
        TOOLS("screen.create_aeronautics_toolgun.panel.tools");

        private final String translationKey;

        LeftTab(String translationKey) {
            this.translationKey = translationKey;
        }

        public Component label() {
            return Component.translatable(this.translationKey);
        }
    }

    public enum ToolEntry {
        SIMPLE_WELD_MODE("screen.create_aeronautics_toolgun.tool.simple_weld_mode"),
        CONNECTION_MODE("screen.create_aeronautics_toolgun.tool.connection_mode"),
        WELD_SELECTION_MODE("screen.create_aeronautics_toolgun.tool.weld_selection_mode"),
        WELD_MODE("screen.create_aeronautics_toolgun.tool.weld_mode"),
        NO_COLLISION_MODE("screen.create_aeronautics_toolgun.tool.no_collision_mode"),
        TRANSLATE_MODE("screen.create_aeronautics_toolgun.tool.translate_mode"),
        ROTATE_MODE("screen.create_aeronautics_toolgun.tool.rotate_mode"),
        DISCONNECT_MODE("screen.create_aeronautics_toolgun.tool.disconnect_mode"),
        PREVIEW("screen.create_aeronautics_toolgun.tool.preview"),
        HUD("screen.create_aeronautics_toolgun.tool.hud"),
        MAGNETIC_MARKERS("screen.create_aeronautics_toolgun.tool.magnetic_markers"),
        BEARING_AXIS_VISUALS("screen.create_aeronautics_toolgun.tool.bearing_axis_visuals"),
        AUTO_WELD("screen.create_aeronautics_toolgun.tool.auto_weld"),
        SNAP("screen.create_aeronautics_toolgun.tool.snap"),
        CONNECTED_SUBLEVEL_PROXIMITY("screen.create_aeronautics_toolgun.tool.connected_sublevel_proximity"),
        WELD_ADJUST_STEP("screen.create_aeronautics_toolgun.tool.weld_adjust_step"),
        TRANSLATE_STEP("screen.create_aeronautics_toolgun.tool.translate_step"),
        ROTATE_STEP("screen.create_aeronautics_toolgun.tool.rotate_step"),
        OFFSET_X("screen.create_aeronautics_toolgun.tool.offset_x"),
        OFFSET_Y("screen.create_aeronautics_toolgun.tool.offset_y"),
        OFFSET_Z("screen.create_aeronautics_toolgun.tool.offset_z"),
        RESET("screen.create_aeronautics_toolgun.tool.reset"),
        GHOST_VEHICLE_TEST("screen.create_aeronautics_toolgun.tool.ghost_vehicle_test");

        private final String translationKey;

        ToolEntry(String translationKey) {
            this.translationKey = translationKey;
        }

        public String label() {
            return Component.translatable(this.translationKey).getString();
        }
    }
}
