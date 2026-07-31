package com.enxv.aeronauticsstructuretool.verification;

import com.enxv.aeronauticsstructuretool.DedicatedServerBlueprintAnchorCheck;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.PlotBlockEntityPositionRemapper;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.PlotVerticalLayout;
import com.enxv.aeronauticsstructuretool.blueprint.placement.BlueprintVerticalPlacement;
import com.enxv.aeronauticsstructuretool.blueprint.placement.PlacementTargetMath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class BlueprintPlacementRegressionCheck {
    private static final double EPSILON = 1.0E-9D;

    private BlueprintPlacementRegressionCheck() {
    }

    public static void main(String[] args) throws Exception {
        verifyGroundClearance();
        verifyPlacementTargetMath();
        verifyPlotVerticalLayout();
        verifyMechanicalArmRelativeTargetsSurvivePositionRemap();
        DedicatedServerBlueprintAnchorCheck.run();
    }

    private static void verifyPlotVerticalLayout() throws Exception {
        CompoundTag plot = plotWithSectionRange(32, 32);
        PlotVerticalLayout tallTarget = PlotVerticalLayout.plan(-4, 64, plot, -64);
        require(tallTarget.sectionIndexShift() == 0, "a target containing the source Y must preserve section indices");
        require(tallTarget.blockYShift() == 0, "a target containing the source Y must preserve absolute block Y");

        PlotVerticalLayout narrowTarget = PlotVerticalLayout.plan(-4, 24, plot, -64);
        require(narrowTarget.sectionIndexShift() == -9, "an out-of-range plot must move into the target section range as one unit");
        require(narrowTarget.blockYShift() == -144, "plot blocks and saved local coordinates must receive the same Y shift");

        boolean rejected = false;
        try {
            PlotVerticalLayout.plan(-4, 24, plotWithSectionRange(0, 24), -64);
        } catch (java.io.IOException expected) {
            rejected = true;
        }
        require(rejected, "a plot taller than the target world must fail instead of dropping sections");

        CompoundTag shiftedPlot = plotWithPositionLists();
        new PlotVerticalLayout(2, 32).apply(shiftedPlot, -4);
        CompoundTag shiftedChunk = shiftedPlot.getCompound("chunks").getCompound("0");
        require(shiftedChunk.getCompound("sections").contains("2"), "section keys must receive the planned shift");
        require(!shiftedChunk.getCompound("sections").contains("0"), "old section keys must not survive remapping");
        require(shiftedChunk.getList("block_entities", Tag.TAG_COMPOUND).getCompound(0).getInt("y") == 42,
                "block-entity Y must receive the same vertical shift");
        require(shiftedChunk.getList("block_ticks", Tag.TAG_COMPOUND).getCompound(0).getInt("Y") == 43,
                "block tick Y must receive the same vertical shift");
        require(shiftedChunk.getList("fluid_ticks", Tag.TAG_COMPOUND).getCompound(0).getInt("y") == 44,
                "fluid tick Y must receive the same vertical shift");
        require(shiftedChunk.getInt("yPos") == -4, "chunk yPos must match the target plot world");
        require(!shiftedChunk.contains("Heightmaps"), "source heightmaps must not be loaded into the target plot");
    }

    private static CompoundTag plotWithSectionRange(int first, int last) {
        CompoundTag sections = new CompoundTag();
        for (int index = first; index <= last; index++) {
            sections.put(String.valueOf(index), new CompoundTag());
        }
        CompoundTag chunk = new CompoundTag();
        chunk.put("sections", sections);
        CompoundTag chunks = new CompoundTag();
        chunks.put("0", chunk);
        CompoundTag plot = new CompoundTag();
        plot.put("chunks", chunks);
        return plot;
    }

    private static CompoundTag plotWithPositionLists() {
        CompoundTag plot = plotWithSectionRange(0, 0);
        CompoundTag chunk = plot.getCompound("chunks").getCompound("0");
        chunk.put("block_entities", positionList("y", 10));
        chunk.put("block_ticks", positionList("Y", 11));
        chunk.put("fluid_ticks", positionList("y", 12));
        chunk.put("Heightmaps", new CompoundTag());
        return plot;
    }

    private static ListTag positionList(String yKey, int y) {
        CompoundTag position = new CompoundTag();
        position.putInt(yKey, y);
        ListTag positions = new ListTag();
        positions.add(position);
        return positions;
    }

    private static void verifyGroundClearance() {
        BlockPos floor = new BlockPos(10, 20, 30);
        Vector3d corrected = BlueprintVerticalPlacement
                .keepAboveClickedSurface(floor, Direction.UP)
                .apply(new Vector3d(10.5D, 24.0D, 30.5D), -10.0D);
        requireClose(
                31.5D + BlueprintVerticalPlacement.SURFACE_GAP,
                corrected.y,
                "clicked-block placement must use the top surface instead of the block center"
        );

        Vector3d alreadyClear = BlueprintVerticalPlacement
                .keepAboveClickedSurface(floor, Direction.UP)
                .apply(new Vector3d(10.5D, 24.0D, 30.5D), -2.0D);
        requireClose(24.0D, alreadyClear.y, "already-clear blueprints must keep legacy height");

        Vector3d sidePlacement = BlueprintVerticalPlacement
                .keepAboveClickedSurface(floor, Direction.NORTH)
                .apply(new Vector3d(10.5D, 24.0D, 30.5D), -10.0D);
        requireClose(24.0D, sidePlacement.y, "side placement must not receive upward floor correction");

        Vector3d exactAlignment = BlueprintVerticalPlacement
                .alignMinimumCenter(50.0D, -10.0D)
                .apply(new Vector3d(10.5D, 100.0D, 30.5D), Double.NaN);
        requireClose(60.0D, exactAlignment.y, "exact alignment must correct both low and excessively high targets");
    }

    private static void verifyPlacementTargetMath() {
        require(
                PlacementTargetMath.resolveClickedBlock(
                        new Vec3(10.5D, 21.0D, 30.5D),
                        Direction.UP
                ).equals(new BlockPos(10, 20, 30)),
                "top-face placement must resolve the block below the hit plane"
        );
        require(
                PlacementTargetMath.resolveClickedBlock(
                        new Vec3(10.5D, 20.5D, 30.0D),
                        Direction.NORTH
                ).equals(new BlockPos(10, 20, 30)),
                "side-face placement must resolve the block behind the hit plane"
        );
        require(
                PlacementTargetMath.projectFace(new Quaterniond(), Direction.SOUTH) == Direction.SOUTH,
                "identity placement projection must preserve the clicked face"
        );
        require(
                PlacementTargetMath.projectFace(
                        new Quaterniond().rotateY(Math.PI * 0.5D),
                        Direction.NORTH
                ) == Direction.WEST,
                "rotated sublevel placement must project the local face into world space"
        );

        boolean rejectedHit = false;
        try {
            PlacementTargetMath.resolveClickedBlock(
                    new Vec3(Double.NaN, 0.0D, 0.0D),
                    Direction.UP
            );
        } catch (IllegalArgumentException expected) {
            rejectedHit = true;
        }
        require(rejectedHit, "non-finite placement hits must be rejected");

        boolean rejectedOrientation = false;
        try {
            PlacementTargetMath.projectFace(
                    new Quaterniond(Double.NaN, 0.0D, 0.0D, 1.0D),
                    Direction.UP
            );
        } catch (IllegalArgumentException expected) {
            rejectedOrientation = true;
        }
        require(rejectedOrientation, "non-finite placement orientations must be rejected");
    }

    private static void verifyMechanicalArmRelativeTargetsSurvivePositionRemap() {
        CompoundTag armTag = new CompoundTag();
        armTag.putString("id", "create:mechanical_arm");
        armTag.putInt("x", 100);
        armTag.putInt("y", 70);
        armTag.putInt("z", 200);

        CompoundTag interactionPoint = new CompoundTag();
        interactionPoint.putString("Type", "create:depot");
        interactionPoint.putString("Mode", "TAKE");
        interactionPoint.put("Pos", NbtUtils.writeBlockPos(new BlockPos(-7, 3, 11)));
        ListTag interactionPoints = new ListTag();
        interactionPoints.add(interactionPoint);
        armTag.put("InteractionPoints", interactionPoints);

        Tag relativeTargetsBefore = armTag.get("InteractionPoints").copy();
        PlotBlockEntityPositionRemapper.setHorizontalPosition(armTag, -12, 44);

        require(armTag.getInt("x") == -12 && armTag.getInt("z") == 44, "top-level plot coordinates were not remapped");
        require(armTag.getInt("y") == 70, "horizontal remapping changed the block entity Y coordinate");
        require(relativeTargetsBefore.equals(armTag.get("InteractionPoints")), "mechanical arm relative targets were changed");
    }

    private static void requireClose(double expected, double actual, String message) {
        require(Math.abs(expected - actual) <= EPSILON, message + ": expected=" + expected + ", actual=" + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
