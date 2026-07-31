package com.enxv.aeronauticsstructuretool;

import com.enxv.aeronauticsstructuretool.client.tool.state.ClientBlueprintSelectionState;
import com.enxv.aeronauticsstructuretool.client.tool.state.ClientToolPreferences;
import com.enxv.aeronauticsstructuretool.client.tool.state.ClientToolSettingsStore;
import com.enxv.aeronauticsstructuretool.client.tool.ClientStructureToolHandler;

import java.util.List;

public final class ClientToolState {
    public static final int INFINITE_NEARBY_QUERY_RANGE = ClientToolPreferences.INFINITE_NEARBY_QUERY_RANGE;
    public static final int DEFAULT_CREATIVE_NEARBY_QUERY_RANGE = ClientToolPreferences.DEFAULT_CREATIVE_NEARBY_QUERY_RANGE;
    public static final int DEFAULT_SURVIVAL_NEARBY_QUERY_RANGE = ClientToolPreferences.DEFAULT_SURVIVAL_NEARBY_QUERY_RANGE;

    private static final ClientToolPreferences PREFERENCES = ClientToolSettingsStore.load();
    private static final ClientBlueprintSelectionState BLUEPRINTS = new ClientBlueprintSelectionState();

    private ClientToolState() {
    }

    public static ToolMode getMode() {
        return PREFERENCES.mode();
    }

    public static void setMode(ToolMode newMode) {
        ToolMode oldMode = PREFERENCES.mode();
        if (oldMode == ToolMode.WELD && newMode != ToolMode.WELD) {
            ClientStructureToolHandler.clearPendingWeld();
        }
        if (oldMode == ToolMode.SIMPLE_WELD && newMode != ToolMode.SIMPLE_WELD) {
            ClientStructureToolHandler.clearPendingSimpleWeld();
        }
        if (oldMode == ToolMode.ROTATE && newMode != ToolMode.ROTATE) {
            ClientStructureToolHandler.clearPendingRotation();
        }
        if (oldMode == ToolMode.TRANSLATE && newMode != ToolMode.TRANSLATE) {
            ClientStructureToolHandler.clearPendingTranslation();
        }
        PREFERENCES.setMode(newMode);
        persist();
    }

    public static ToolPanel getPanel() {
        return PREFERENCES.panel();
    }

    public static void setPanel(ToolPanel newPanel) {
        PREFERENCES.setPanel(newPanel);
        persist();
    }

    public static String getSelectedFile() {
        return PREFERENCES.selectedFile();
    }

    public static void setSelectedFile(String name) {
        PREFERENCES.setSelectedFile(name);
        BLUEPRINTS.selectionChanged(PREFERENCES.selectedFile());
        persist();
    }

    public static List<BlueprintListEntry> listFiles() {
        return BLUEPRINTS.listFiles();
    }

    public static BlueprintListEntry getSelectedEntry() {
        return BLUEPRINTS.selectedEntry(PREFERENCES.selectedFile());
    }

    public static void refreshFileCache() {
        BLUEPRINTS.refresh(PREFERENCES.selectedFile());
    }

    public static boolean isPreviewEnabled() {
        return PREFERENCES.previewEnabled();
    }

    public static void togglePreviewEnabled() {
        PREFERENCES.togglePreviewEnabled();
        persist();
    }

    public static boolean isHudEnabled() {
        return PREFERENCES.hudEnabled();
    }

    public static void toggleHudEnabled() {
        PREFERENCES.toggleHudEnabled();
        persist();
    }

    public static boolean isMagneticMarkersEnabled() {
        return PREFERENCES.magneticMarkersEnabled();
    }

    public static void toggleMagneticMarkersEnabled() {
        PREFERENCES.toggleMagneticMarkersEnabled();
        persist();
    }

    public static boolean isAutoWeldEnabled() {
        return PREFERENCES.autoWeldEnabled();
    }

    public static void toggleAutoWeldEnabled() {
        PREFERENCES.toggleAutoWeldEnabled();
        persist();
    }

    public static boolean isBearingAxisVisualsEnabled() {
        return PREFERENCES.bearingAxisVisualsEnabled();
    }

    public static void toggleBearingAxisVisualsEnabled() {
        PREFERENCES.toggleBearingAxisVisualsEnabled();
        persist();
    }

    public static boolean isRangeDeleteEnabled() {
        return PREFERENCES.rangeDeleteEnabled();
    }

    public static void toggleRangeDeleteEnabled() {
        PREFERENCES.toggleRangeDeleteEnabled();
        persist();
    }

    public static ConnectionMode getConnectionMode() {
        return PREFERENCES.connectionMode();
    }

    public static void cycleConnectionMode(int delta) {
        PREFERENCES.cycleConnectionMode(delta);
        persist();
    }

    public static BearingAxisMode getBearingAxisMode() {
        return PREFERENCES.bearingAxisMode();
    }

    public static void cycleBearingAxisMode(int delta) {
        PREFERENCES.cycleBearingAxisMode(delta);
        persist();
    }

    public static WeldSelectionMode getWeldSelectionMode() {
        return PREFERENCES.weldSelectionMode();
    }

    public static void cycleWeldSelectionMode(int delta) {
        PREFERENCES.cycleWeldSelectionMode(delta);
        persist();
    }

    public static PlacementSnapMode getSnapMode() {
        return PREFERENCES.snapMode();
    }

    public static void cycleSnapMode(int delta) {
        PREFERENCES.cycleSnapMode(delta);
        persist();
    }

    public static int getRotationStep() {
        return PREFERENCES.rotationStep();
    }

    public static double getTranslateStep() {
        return PREFERENCES.translateStep();
    }

    public static void cycleRotationStep(int delta) {
        PREFERENCES.cycleRotationStep(delta);
        persist();
    }

    public static void cycleTranslateStep(int delta) {
        PREFERENCES.cycleTranslateStep(delta);
        persist();
    }

    public static int getRotationDegrees() {
        return PREFERENCES.rotationDegrees();
    }

    public static void rotatePlacement(int direction) {
        PREFERENCES.rotatePlacement(direction);
        persist();
    }

    public static double getWeldAdjustStep() {
        return PREFERENCES.weldAdjustStep();
    }

    public static void cycleWeldAdjustStep(int delta) {
        PREFERENCES.cycleWeldAdjustStep(delta);
        persist();
    }

    public static int getScalePercent() {
        return PREFERENCES.scalePercent();
    }

    public static void setScalePercent(int value) {
        PREFERENCES.setScalePercent(value);
        persist();
    }

    public static void adjustScalePercent(int delta) {
        PREFERENCES.adjustScalePercent(delta);
        persist();
    }

    public static int getOffsetX() {
        return PREFERENCES.offsetX();
    }

    public static int getOffsetY() {
        return PREFERENCES.offsetY();
    }

    public static int getOffsetZ() {
        return PREFERENCES.offsetZ();
    }

    public static void adjustOffsetX(int delta) {
        PREFERENCES.adjustOffsetX(delta);
        persist();
    }

    public static void adjustOffsetY(int delta) {
        PREFERENCES.adjustOffsetY(delta);
        persist();
    }

    public static void adjustOffsetZ(int delta) {
        PREFERENCES.adjustOffsetZ(delta);
        persist();
    }

    public static int getDeleteRange() {
        return PREFERENCES.deleteRange();
    }

    public static void adjustDeleteRange(int delta) {
        PREFERENCES.adjustDeleteRange(delta);
        persist();
    }

    public static void setDeleteRange(int value) {
        PREFERENCES.setDeleteRange(value);
        persist();
    }

    public static int getNearbyQueryRange() {
        return PREFERENCES.nearbyQueryRange();
    }

    public static void setNearbyQueryRange(int value) {
        PREFERENCES.setNearbyQueryRange(value);
        persist();
    }

    public static int nearbyQueryRangeForTool(boolean survivalRestricted) {
        return PREFERENCES.nearbyQueryRangeForTool(survivalRestricted);
    }

    public static void sanitizeNearbyQueryRangeForTool(boolean survivalRestricted) {
        PREFERENCES.sanitizeNearbyQueryRangeForTool(survivalRestricted);
        persist();
    }

    public static double getConnectedSublevelProximity() {
        return PREFERENCES.connectedSublevelProximity();
    }

    public static void adjustConnectedSublevelProximity(int delta) {
        PREFERENCES.adjustConnectedSublevelProximity(delta);
        persist();
    }

    public static void resetPlacementTransform() {
        PREFERENCES.resetPlacementTransform();
        persist();
    }

    public static PreviewBlueprintData getOrLoadPreview() {
        return BLUEPRINTS.getOrLoadPreview(PREFERENCES.selectedFile());
    }

    public static String getPreviewError() {
        return BLUEPRINTS.previewError();
    }

    private static void persist() {
        ClientToolSettingsStore.save(PREFERENCES);
    }
}
