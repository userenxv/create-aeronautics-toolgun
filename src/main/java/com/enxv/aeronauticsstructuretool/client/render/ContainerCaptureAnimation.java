package com.enxv.aeronauticsstructuretool.client.render;

public final class ContainerCaptureAnimation {
    private double angle;
    private double energyTime;
    private double speed = 360D;
    private double activity;

    public void advance(double seconds, boolean capturing, boolean filled) {
        double dt = Math.max(0D, Math.min(0.1D, seconds));
        double target = capturing ? 2160D : filled ? 720D : 360D;
        double damping = capturing ? 12D : 5D;
        double decay = Math.exp(-damping * dt);
        // Integrate speed, rather than multiplying wall time by speed: changing state preserves phase.
        angle = (angle + target * dt + (speed - target) * (1D - decay) / damping) % 360D;
        speed = target + (speed - target) * decay;
        activity += ((capturing ? 1D : 0D) - activity) * (1D - decay);
        energyTime += dt * (1D + 5D * activity);
    }

    public float angle() {
        return (float) angle;
    }

    public float energyTime() {
        return (float) energyTime;
    }

    public float activity() {
        return (float) activity;
    }

    public double rpm() {
        return speed / 6D;
    }
}
