package com.enxv.aeronauticsstructuretool.vehicle.container;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ContainerCaptureConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue TIMED_CAPTURE;
    private static final ModConfigSpec.DoubleValue BLOCKS_PER_SECOND;
    private static final ModConfigSpec.BooleanValue BACKUP_CAPTURED_VEHICLES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("containerCapture");
        TIMED_CAPTURE = builder.comment("Require holding use to pack a vehicle. Sneaking speeds up packing; movement slows or reverses progress. Disabling the timer makes packing instant; placing remains instant.")
                .define("timedCapture", true);
        BLOCKS_PER_SECOND = builder.comment("Physical blocks packed per second while standing still, including connected structures and moving Create contraptions. Sneaking doubles this speed.")
                .defineInRange("blocksPerSecond", 50.0D, 0.1D, 1000000.0D);
        BACKUP_CAPTURED_VEHICLES = builder.comment("Back up packed vehicles asynchronously to container-backups/<vehicle UUID>.excraft in the game/server directory. The latest capture replaces the same UUID. A full backup queue rejects capture before any originals are removed.")
                .define("backupCapturedVehicles", true);
        builder.pop();
        SPEC = builder.build();
    }

    private ContainerCaptureConfig() {
    }

    public static boolean backupsEnabled() {
        return BACKUP_CAPTURED_VEHICLES.getAsBoolean();
    }

    public static int durationTicks(long blocks) {
        return durationTicks(blocks, TIMED_CAPTURE.getAsBoolean(), BLOCKS_PER_SECOND.get());
    }

    public static int durationTicks(long blocks, boolean enabled, double blocksPerSecond) {
        if (!Double.isFinite(blocksPerSecond) || blocksPerSecond <= 0 || blocks < 0) {
            throw new IllegalArgumentException("invalid container capture speed or block count");
        }
        if (!enabled) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE - 1L, Math.max(1D, Math.ceil(blocks * 20D / blocksPerSecond)));
    }
}
