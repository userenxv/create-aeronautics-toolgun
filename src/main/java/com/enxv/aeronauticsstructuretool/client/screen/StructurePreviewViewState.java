package com.enxv.aeronauticsstructuretool.client.screen;

import net.minecraft.util.Mth;

import java.util.HashSet;
import java.util.Set;

public final class StructurePreviewViewState {
    private final Set<String> skippedBlockEntityTypes = new HashSet<>();
    private float yaw = -35.0F;
    private float pitch = 25.0F;
    private float zoom = 1.0F;
    private boolean dragging;

    public float yaw() {
        return this.yaw;
    }

    public float pitch() {
        return this.pitch;
    }

    public float zoom() {
        return this.zoom;
    }

    public Set<String> skippedBlockEntityTypes() {
        return this.skippedBlockEntityTypes;
    }

    public void beginDrag() {
        this.dragging = true;
    }

    public void endDrag() {
        this.dragging = false;
    }

    public boolean drag(double dragX, double dragY) {
        if (!this.dragging) {
            return false;
        }
        this.yaw += (float) dragX * 0.8F;
        this.pitch = Mth.clamp(this.pitch + (float) dragY * 0.5F, -70.0F, 70.0F);
        return true;
    }

    public void zoom(double scrollY) {
        this.zoom = Mth.clamp(this.zoom + (float) scrollY * 0.08F, 0.45F, 2.2F);
    }

    public void clearRendererFailures() {
        this.skippedBlockEntityTypes.clear();
    }
}
