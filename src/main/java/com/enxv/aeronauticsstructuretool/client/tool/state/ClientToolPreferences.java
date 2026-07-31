package com.enxv.aeronauticsstructuretool.client.tool.state;

import com.enxv.aeronauticsstructuretool.BearingAxisMode;
import com.enxv.aeronauticsstructuretool.ConnectionMode;
import com.enxv.aeronauticsstructuretool.PlacementSnapMode;
import com.enxv.aeronauticsstructuretool.ToolMode;
import com.enxv.aeronauticsstructuretool.ToolPanel;
import com.enxv.aeronauticsstructuretool.WeldSelectionMode;

import net.minecraft.util.Mth;

public final class ClientToolPreferences {
    public static final int INFINITE_NEARBY_QUERY_RANGE = 0;
    public static final int DEFAULT_CREATIVE_NEARBY_QUERY_RANGE = INFINITE_NEARBY_QUERY_RANGE;
    public static final int DEFAULT_SURVIVAL_NEARBY_QUERY_RANGE = 64;

    static final int[] ROTATION_STEPS = {1, 5, 10, 15, 30, 45, 90};
    static final double[] TRANSLATE_STEPS = {0.1D, 0.25D, 0.5D, 1.0D, 2.0D, 4.0D};
    static final double[] WELD_ADJUST_STEPS = {0.05D, 0.1D, 0.25D, 0.5D, 1.0D};

    ToolMode mode = ToolMode.SAVE;
    ToolPanel panel = ToolPanel.BLUEPRINTS;
    String selectedFile = "";
    boolean previewEnabled = true;
    boolean hudEnabled = true;
    boolean magneticMarkersEnabled = true;
    boolean bearingAxisVisualsEnabled = true;
    boolean autoWeldEnabled;
    boolean rangeDeleteEnabled;
    ConnectionMode connectionMode = ConnectionMode.FIXED;
    BearingAxisMode bearingAxisMode = BearingAxisMode.AUTO;
    WeldSelectionMode weldSelectionMode = WeldSelectionMode.FACE_POINTS;
    PlacementSnapMode snapMode = PlacementSnapMode.LEGACY;
    int rotationStep = 15;
    double translateStep = 0.25D;
    int rotationDegrees;
    double weldAdjustStep = 0.25D;
    int scalePercent = 100;
    int offsetX;
    int offsetY;
    int offsetZ;
    int deleteRange = 8;
    int nearbyQueryRange = DEFAULT_CREATIVE_NEARBY_QUERY_RANGE;
    double connectedSublevelProximity = 0.5D;

    public ToolMode mode() {
        return this.mode;
    }

    public void setMode(ToolMode mode) {
        this.mode = mode;
    }

    public ToolPanel panel() {
        return this.panel;
    }

    public void setPanel(ToolPanel panel) {
        this.panel = panel;
    }

    public String selectedFile() {
        return this.selectedFile;
    }

    public void setSelectedFile(String selectedFile) {
        this.selectedFile = selectedFile == null ? "" : selectedFile;
    }

    public boolean previewEnabled() {
        return this.previewEnabled;
    }

    public void togglePreviewEnabled() {
        this.previewEnabled = !this.previewEnabled;
    }

    public boolean hudEnabled() {
        return this.hudEnabled;
    }

    public void toggleHudEnabled() {
        this.hudEnabled = !this.hudEnabled;
    }

    public boolean magneticMarkersEnabled() {
        return this.magneticMarkersEnabled;
    }

    public void toggleMagneticMarkersEnabled() {
        this.magneticMarkersEnabled = !this.magneticMarkersEnabled;
    }

    public boolean bearingAxisVisualsEnabled() {
        return this.bearingAxisVisualsEnabled;
    }

    public void toggleBearingAxisVisualsEnabled() {
        this.bearingAxisVisualsEnabled = !this.bearingAxisVisualsEnabled;
    }

    public boolean autoWeldEnabled() {
        return this.autoWeldEnabled;
    }

    public void toggleAutoWeldEnabled() {
        this.autoWeldEnabled = !this.autoWeldEnabled;
    }

    public boolean rangeDeleteEnabled() {
        return this.rangeDeleteEnabled;
    }

    public void toggleRangeDeleteEnabled() {
        this.rangeDeleteEnabled = !this.rangeDeleteEnabled;
    }

    public ConnectionMode connectionMode() {
        return this.connectionMode;
    }

    public void cycleConnectionMode(int delta) {
        this.connectionMode = delta >= 0 ? this.connectionMode.next() : this.connectionMode.previous();
    }

    public BearingAxisMode bearingAxisMode() {
        return this.bearingAxisMode;
    }

    public void cycleBearingAxisMode(int delta) {
        this.bearingAxisMode = delta >= 0 ? this.bearingAxisMode.next() : this.bearingAxisMode.previous();
    }

    public WeldSelectionMode weldSelectionMode() {
        return this.weldSelectionMode;
    }

    public void cycleWeldSelectionMode(int delta) {
        this.weldSelectionMode = delta >= 0 ? this.weldSelectionMode.next() : this.weldSelectionMode.previous();
    }

    public PlacementSnapMode snapMode() {
        return this.snapMode;
    }

    public void cycleSnapMode(int delta) {
        this.snapMode = delta >= 0 ? this.snapMode.next() : this.snapMode.previous();
    }

    public int rotationStep() {
        return this.rotationStep;
    }

    public void cycleRotationStep(int delta) {
        int index = indexOf(ROTATION_STEPS, this.rotationStep);
        this.rotationStep = ROTATION_STEPS[Math.floorMod(index + direction(delta), ROTATION_STEPS.length)];
    }

    public double translateStep() {
        return this.translateStep;
    }

    public void cycleTranslateStep(int delta) {
        this.translateStep = cycle(TRANSLATE_STEPS, this.translateStep, delta);
    }

    public int rotationDegrees() {
        return this.rotationDegrees;
    }

    public void rotatePlacement(int direction) {
        this.rotationDegrees = normalizeRotation(this.rotationDegrees + this.rotationStep * Integer.signum(direction));
    }

    public double weldAdjustStep() {
        return this.weldAdjustStep;
    }

    public void cycleWeldAdjustStep(int delta) {
        this.weldAdjustStep = cycle(WELD_ADJUST_STEPS, this.weldAdjustStep, delta);
    }

    public int scalePercent() {
        return this.scalePercent;
    }

    public void setScalePercent(int scalePercent) {
        this.scalePercent = Mth.clamp(scalePercent, 25, 400);
    }

    public void adjustScalePercent(int delta) {
        setScalePercent(this.scalePercent + (delta > 0 ? 25 : -25));
    }

    public int offsetX() {
        return this.offsetX;
    }

    public int offsetY() {
        return this.offsetY;
    }

    public int offsetZ() {
        return this.offsetZ;
    }

    public void adjustOffsetX(int delta) {
        this.offsetX += delta;
    }

    public void adjustOffsetY(int delta) {
        this.offsetY += delta;
    }

    public void adjustOffsetZ(int delta) {
        this.offsetZ += delta;
    }

    public int deleteRange() {
        return this.deleteRange;
    }

    public void setDeleteRange(int deleteRange) {
        this.deleteRange = Mth.clamp(deleteRange, 0, 128);
    }

    public void adjustDeleteRange(int delta) {
        setDeleteRange(this.deleteRange + (delta > 0 ? 1 : -1));
    }

    public int nearbyQueryRange() {
        return this.nearbyQueryRange;
    }

    public void setNearbyQueryRange(int nearbyQueryRange) {
        this.nearbyQueryRange = Math.max(INFINITE_NEARBY_QUERY_RANGE, nearbyQueryRange);
    }

    public int nearbyQueryRangeForTool(boolean survivalRestricted) {
        if (survivalRestricted && this.nearbyQueryRange == INFINITE_NEARBY_QUERY_RANGE) {
            return DEFAULT_SURVIVAL_NEARBY_QUERY_RANGE;
        }
        return this.nearbyQueryRange;
    }

    public void sanitizeNearbyQueryRangeForTool(boolean survivalRestricted) {
        if (survivalRestricted && this.nearbyQueryRange == INFINITE_NEARBY_QUERY_RANGE) {
            this.nearbyQueryRange = DEFAULT_SURVIVAL_NEARBY_QUERY_RANGE;
        }
    }

    public double connectedSublevelProximity() {
        return this.connectedSublevelProximity;
    }

    public void adjustConnectedSublevelProximity(int delta) {
        this.connectedSublevelProximity = clampConnectedSublevelProximity(
                this.connectedSublevelProximity + (delta > 0 ? 0.5D : -0.5D)
        );
    }

    public void resetPlacementTransform() {
        this.rotationDegrees = 0;
        this.scalePercent = 100;
        this.offsetX = 0;
        this.offsetY = 0;
        this.offsetZ = 0;
    }

    static int normalizeRotation(int degrees) {
        int normalized = degrees % 360;
        return normalized < 0 ? normalized + 360 : normalized;
    }

    static int clampRotationStep(int raw) {
        for (int step : ROTATION_STEPS) {
            if (step == raw) {
                return step;
            }
        }
        return 15;
    }

    static double clampConnectedSublevelProximity(double raw) {
        return Mth.clamp(raw, 0.0D, 16.0D);
    }

    static double clampWeldAdjustStep(double raw) {
        return supportedStep(WELD_ADJUST_STEPS, raw, 0.25D);
    }

    static double clampTranslateStep(double raw) {
        return supportedStep(TRANSLATE_STEPS, raw, 0.25D);
    }

    private static int direction(int delta) {
        return delta >= 0 ? 1 : -1;
    }

    private static int indexOf(int[] values, int value) {
        for (int index = 0; index < values.length; index++) {
            if (values[index] == value) {
                return index;
            }
        }
        return 0;
    }

    private static double cycle(double[] values, double value, int delta) {
        int index = 0;
        for (int current = 0; current < values.length; current++) {
            if (Math.abs(values[current] - value) < 1.0E-6D) {
                index = current;
                break;
            }
        }
        return values[Math.floorMod(index + direction(delta), values.length)];
    }

    private static double supportedStep(double[] values, double raw, double fallback) {
        for (double value : values) {
            if (Math.abs(value - raw) < 1.0E-6D) {
                return value;
            }
        }
        return fallback;
    }
}
