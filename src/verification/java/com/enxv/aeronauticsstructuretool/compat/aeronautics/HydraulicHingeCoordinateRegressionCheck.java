package com.enxv.aeronauticsstructuretool.compat.aeronautics;

import com.enxv.aeronauticsstructuretool.blueprint.geometry.LegacyPlotBlockCoordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public final class HydraulicHingeCoordinateRegressionCheck {
    private HydraulicHingeCoordinateRegressionCheck() {
    }

    public static void main(String[] args) {
        CompoundTag standardPlot = plot(7);
        require(
                HydraulicHingeBlueprintCompat.decodeLegacyPosition(
                        new BlockPos(20_481_032, 128, 20_503_560),
                        standardPlot
                ).equals(new BlockPos(1_032, 128, 1_032)),
                "legacy absolute hinge position was not localized"
        );
        require(
                HydraulicHingeBlueprintCompat.decodeLegacyPosition(
                        new BlockPos(1_032, 128, 1_032),
                        standardPlot
                ).equals(new BlockPos(1_032, 128, 1_032)),
                "already-local legacy hinge position must remain unchanged"
        );
        require(
                HydraulicHingeBlueprintCompat.decodeLegacyPosition(
                        new BlockPos(-1, -64, -2_049),
                        standardPlot
                ).equals(new BlockPos(2_047, -64, 2_047)),
                "negative legacy coordinates must use floor modulo"
        );
        require(
                LegacyPlotBlockCoordinates.containsSavedLocal(
                        new BlockPos(2_047, 128, 0),
                        standardPlot
                ),
                "last block in a Plot must be accepted"
        );
        require(
                !LegacyPlotBlockCoordinates.containsSavedLocal(
                        new BlockPos(2_048, 128, 0),
                        standardPlot
                ),
                "first block beyond a Plot must be rejected"
        );
        require(
                LegacyPlotBlockCoordinates.sideLengthBlocks(plot(6)) == 1_024,
                "Plot size must follow the saved log_size"
        );
        expectIllegalArgument(() -> LegacyPlotBlockCoordinates.sideLengthBlocks(new CompoundTag()));
        CompoundTag invalid = new CompoundTag();
        invalid.putInt("log_size", 27);
        expectIllegalArgument(() -> LegacyPlotBlockCoordinates.sideLengthBlocks(invalid));
    }

    private static CompoundTag plot(int logSize) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("log_size", logSize);
        return tag;
    }

    private static void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            throw new IllegalStateException("expected IllegalArgumentException");
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
