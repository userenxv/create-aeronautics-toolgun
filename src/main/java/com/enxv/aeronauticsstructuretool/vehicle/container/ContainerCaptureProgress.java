package com.enxv.aeronauticsstructuretool.vehicle.container;

public final class ContainerCaptureProgress {
    private double completed;

    public static double rate(boolean sneaking, double horizontalBlocksPerTick) {
        if (!Double.isFinite(horizontalBlocksPerTick) || horizontalBlocksPerTick < 0D) {
            throw new IllegalArgumentException("invalid player movement");
        }
        return Math.max(-1D, (sneaking ? 2D : 1D) - horizontalBlocksPerTick / 0.04D);
    }

    public double advance(boolean sneaking, double horizontalBlocksPerTick, int totalTicks) {
        double rate = rate(sneaking, horizontalBlocksPerTick);
        completed = Math.max(0D, Math.min(totalTicks, completed + rate));
        return rate;
    }

    public double completed() {
        return completed;
    }
}
