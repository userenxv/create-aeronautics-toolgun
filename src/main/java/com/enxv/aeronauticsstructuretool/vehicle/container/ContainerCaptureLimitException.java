package com.enxv.aeronauticsstructuretool.vehicle.container;

/** Expected server policy rejection, before storing data or removing any original bodies. */
public final class ContainerCaptureLimitException extends Exception {
    private final long blocks;
    private final int maximum;

    public ContainerCaptureLimitException(long blocks, int maximum) {
        super("Vehicle has " + blocks + " blocks; container capture limit is " + maximum);
        this.blocks = blocks;
        this.maximum = maximum;
    }

    public long blocks() { return blocks; }
    public int maximum() { return maximum; }
}
