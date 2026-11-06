package com.enxv.aeronauticsstructuretool.vehicle.container;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ContainerCaptureConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue TIMED_CAPTURE;
    private static final ModConfigSpec.DoubleValue BLOCKS_PER_SECOND;
    private static final ModConfigSpec.BooleanValue BACKUP_CAPTURED_VEHICLES;
    private static final ModConfigSpec.IntValue BACKUP_MAX_SIZE_MB;
    private static final ModConfigSpec.BooleanValue LIMIT_CAPTURE_BLOCKS;
    private static final ModConfigSpec.IntValue MAX_CAPTURE_BLOCKS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("containerCapture");
        LIMIT_CAPTURE_BLOCKS = builder.comment("Limit how many placed blocks a reusable or single-use vehicle container can pack. Applies to all players, including creative mode. Does not restrict releasing vehicles already stored.")
                .define("limitCaptureBlocks", false);
        MAX_CAPTURE_BLOCKS = builder.comment("Maximum blocks per packed vehicle when limitCaptureBlocks is true. Includes connected sublevels and supported moving contraptions; excludes inventory contents. A vehicle exactly at the limit is allowed.")
                .defineInRange("maxCaptureBlocks", 4096, 1, Integer.MAX_VALUE);
        TIMED_CAPTURE = builder.comment("Require holding use to pack a vehicle. Sneaking speeds up packing; movement slows or reverses progress. Disabling the timer makes packing instant; placing remains instant.")
                .define("timedCapture", true);
        BLOCKS_PER_SECOND = builder.comment("Physical blocks packed per second while standing still, including connected structures and moving Create contraptions. Sneaking doubles this speed.")
                .defineInRange("blocksPerSecond", 50.0D, 0.1D, 1000000.0D);
        BACKUP_CAPTURED_VEHICLES = builder.comment("Back up packed vehicles asynchronously to container-backups/<vehicle UUID>.excraft in the game/server directory. The latest capture replaces the same UUID. A full backup queue rejects capture before any originals are removed.")
                .define("backupCapturedVehicles", true);
        BACKUP_MAX_SIZE_MB = builder.comment("Maximum total size of completed .excraft files in container-backups, in MiB. Keep old backups until the limit is exceeded, then delete oldest files first. Cleanup runs on the backup thread after writing. A single backup exceeding the limit is also removed.")
                .defineInRange("backupMaxSizeMB", 256, 1, 1048576);
        builder.pop();
        SPEC = builder.build();
    }

    private ContainerCaptureConfig() {
    }

    public static void checkBlockLimit(long blocks) throws ContainerCaptureLimitException {
        checkBlockLimit(blocks, LIMIT_CAPTURE_BLOCKS.getAsBoolean(), MAX_CAPTURE_BLOCKS.get());
    }

    static void checkBlockLimit(long blocks, boolean enabled, int maximum) throws ContainerCaptureLimitException {
        if (blocks < 0 || maximum < 1) throw new IllegalArgumentException("invalid capture block limit or count");
        if (enabled && blocks > maximum) throw new ContainerCaptureLimitException(blocks, maximum);
    }

    public static boolean backupsEnabled() {
        return BACKUP_CAPTURED_VEHICLES.getAsBoolean();
    }

    public static long backupMaximumBytes() {
        return BACKUP_MAX_SIZE_MB.get().longValue() * 1024L * 1024L;
    }

    public static int durationTicks(long blocks) {
        return durationTicks(blocks, TIMED_CAPTURE.getAsBoolean(), BLOCKS_PER_SECOND.get());
    }

    public static int durationTicks(long blocks, net.minecraft.server.level.ServerPlayer player) {
        return player.isCreative() ? 0 : durationTicks(blocks);
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
