package com.enxv.aeronauticsstructuretool.compat.sableschematicapi;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public final class SableBlueprintFrameCoordinatesRegressionCheck {
    private SableBlueprintFrameCoordinatesRegressionCheck() {
    }

    public static void main(String[] args) {
        require(
                decode(
                        new int[]{20_481_032, 128, 20_507_656, 20_481_032, 129, 20_507_656},
                        new int[]{20_481_032, 128, 20_507_656},
                        7
                ).equals(new BlockPos(1_032, 128, 1_032)),
                "format 1 frame origin was not recovered from its source Plot"
        );
        require(
                decode(
                        new int[]{20_481_026, 127, 20_524_037, 20_481_039, 130, 20_524_043},
                        new int[]{20_481_026, 127, 20_524_037},
                        7
                ).equals(new BlockPos(1_026, 127, 1_029)),
                "format 2 frame origin was not recovered from its source Plot"
        );
        require(
                decode(
                        new int[]{1_032, -64, 1_032, 1_040, 80, 1_040},
                        new int[]{1_032, -64, 1_032},
                        7
                ).equals(new BlockPos(1_032, -64, 1_032)),
                "already-local frame coordinates or source build height changed"
        );
        require(
                decode(
                        new int[]{-1, -64, -2_049, 5, 10, -2_040},
                        new int[]{-1, -64, -2_049},
                        7
                ).equals(new BlockPos(2_047, -64, 2_047)),
                "negative Plot coordinates did not use floor modulo"
        );
        require(
                decode(
                        new int[]{40_963, 32, 49_157, 40_970, 48, 49_164},
                        new int[]{40_963, 32, 49_157},
                        8
                ).equals(new BlockPos(3, 32, 5)),
                "larger Plot size was not read from its saved metadata"
        );

        expectMalformed(frame(new int[5], new int[3]), plot(7));
        expectMalformed(frame(new int[6], new int[2]), plot(7));
    }

    private static BlockPos decode(int[] bounds, int[] origin, int logSize) {
        return SableBlueprintFrameCoordinates.savedLocalOrigin(frame(bounds, origin), plot(logSize));
    }

    private static CompoundTag frame(int[] bounds, int[] origin) {
        CompoundTag frame = new CompoundTag();
        frame.putIntArray("storage_bounds", bounds);
        frame.putIntArray("blocks_origin", origin);
        return frame;
    }

    private static CompoundTag plot(int logSize) {
        CompoundTag plot = new CompoundTag();
        plot.putInt("log_size", logSize);
        return plot;
    }

    private static void expectMalformed(CompoundTag frame, CompoundTag plot) {
        try {
            SableBlueprintFrameCoordinates.savedLocalOrigin(frame, plot);
            throw new IllegalStateException("malformed Sable frame was accepted");
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
