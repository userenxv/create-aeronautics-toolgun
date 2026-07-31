package com.enxv.aeronauticsstructuretool.blueprint.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.joml.Vector3d;

public record BlueprintVerticalPlacement(
        Mode mode,
        double desiredMinimumBlockCenterY,
        double minimumRelativeBlockCenterYOverride
) {
    public static final double SURFACE_GAP = 1.0D / 16.0D;

    public BlueprintVerticalPlacement {
        if (mode == null) {
            throw new IllegalArgumentException("vertical placement mode is missing");
        }
        if (mode != Mode.UNCHANGED && !Double.isFinite(desiredMinimumBlockCenterY)) {
            throw new IllegalArgumentException("vertical placement minimum target must be finite");
        }
        if (!(Double.isFinite(minimumRelativeBlockCenterYOverride)
                || Double.isNaN(minimumRelativeBlockCenterYOverride))) {
            throw new IllegalArgumentException("vertical placement minimum override is invalid");
        }
    }

    public static BlueprintVerticalPlacement unchanged() {
        return new BlueprintVerticalPlacement(Mode.UNCHANGED, Double.NaN, Double.NaN);
    }

    public static BlueprintVerticalPlacement keepAboveSurface(Direction face, double surfaceY) {
        if (face != Direction.UP || !Double.isFinite(surfaceY)) {
            return unchanged();
        }
        return new BlueprintVerticalPlacement(Mode.KEEP_ABOVE, surfaceY + 0.5D + SURFACE_GAP, Double.NaN);
    }

    public static BlueprintVerticalPlacement keepAboveClickedSurface(BlockPos clickedPos, Direction face) {
        if (clickedPos == null) {
            return unchanged();
        }
        return keepAboveSurface(face, clickedPos.getY() + 1.0D);
    }

    public static BlueprintVerticalPlacement alignMinimumCenter(double desiredMinimumBlockCenterY, double minimumRelativeBlockCenterY) {
        return new BlueprintVerticalPlacement(Mode.ALIGN, desiredMinimumBlockCenterY, minimumRelativeBlockCenterY);
    }

    public boolean needsComputedMinimum() {
        return this.mode != Mode.UNCHANGED && !Double.isFinite(this.minimumRelativeBlockCenterYOverride);
    }

    public Vector3d apply(Vector3d placementTarget, double computedMinimumRelativeBlockCenterY) {
        if (placementTarget == null
                || !Double.isFinite(placementTarget.x)
                || !Double.isFinite(placementTarget.y)
                || !Double.isFinite(placementTarget.z)) {
            throw new IllegalArgumentException("placement target must be finite");
        }
        Vector3d corrected = new Vector3d(placementTarget);
        if (this.mode == Mode.UNCHANGED) {
            return corrected;
        }

        double minimumRelativeY = Double.isFinite(this.minimumRelativeBlockCenterYOverride)
                ? this.minimumRelativeBlockCenterYOverride
                : computedMinimumRelativeBlockCenterY;
        if (!Double.isFinite(minimumRelativeY)) {
            throw new IllegalArgumentException("blueprint minimum block height is unavailable");
        }

        double correction = this.desiredMinimumBlockCenterY - (corrected.y + minimumRelativeY);
        if (!Double.isFinite(correction)) {
            throw new IllegalArgumentException("vertical placement correction is not finite");
        }
        if (this.mode == Mode.ALIGN || correction > 0.0D) {
            corrected.y += correction;
        }
        return corrected;
    }

    public enum Mode {
        UNCHANGED,
        KEEP_ABOVE,
        ALIGN
    }
}
