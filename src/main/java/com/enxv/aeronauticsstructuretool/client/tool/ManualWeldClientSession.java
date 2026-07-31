package com.enxv.aeronauticsstructuretool.client.tool;

import com.enxv.aeronauticsstructuretool.client.ClientHooks;
import com.enxv.aeronauticsstructuretool.client.render.ClientConstraintVisualTracker;
import com.enxv.aeronauticsstructuretool.ClientToolState;
import com.enxv.aeronauticsstructuretool.ConnectionMode;
import com.enxv.aeronauticsstructuretool.MoveSubLevelPointPayload;
import com.enxv.aeronauticsstructuretool.WeldSubLevelsPayload;
import com.enxv.aeronauticsstructuretool.toolgun.weld.WeldAxisGeometry;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3d;

import java.util.UUID;

public final class ManualWeldClientSession {
    private static BlockPos firstBlockPos;
    private static Vec3 firstPoint;
    private static Direction firstFace;
    private static UUID firstSubLevelId;
    private static Vec3 firstLocalPoint;
    private static BlockPos secondBlockPos;
    private static Vec3 secondPoint;
    private static Direction secondFace;
    private static UUID secondSubLevelId;
    private static Vec3 secondLocalPoint;
    private static double distanceOffset;

    private ManualWeldClientSession() {
    }

    public static void select(
            InteractionHand hand,
            BlockHitResult hit,
            Level level,
            Vec3 beamTarget
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        SubLevel containing = Sable.HELPER.getContaining(level, hit.getBlockPos());
        if (containing == null) {
            minecraft.player.displayClientMessage(
                    Component.translatable("message.create_aeronautics_toolgun.weld_need_structure"),
                    true
            );
            return;
        }

        ClientHooks.showBeam(hand, beamTarget);
        if (firstPoint == null) {
            firstBlockPos = hit.getBlockPos().immutable();
            firstPoint = beamTarget;
            firstFace = hit.getDirection();
            firstSubLevelId = containing.getUniqueId();
            firstLocalPoint = containing.logicalPose().transformPositionInverse(beamTarget);
            clearSecondSelection();
            minecraft.player.displayClientMessage(
                    Component.translatable("message.create_aeronautics_toolgun.weld_first_point"),
                    true
            );
            return;
        }

        secondBlockPos = hit.getBlockPos().immutable();
        secondPoint = beamTarget;
        secondFace = hit.getDirection();
        secondSubLevelId = containing.getUniqueId();
        secondLocalPoint = containing.logicalPose().transformPositionInverse(beamTarget);
        distanceOffset = 0.0D;
        syncSecondPosition(0.0D);
        minecraft.player.displayClientMessage(
                Component.translatable("message.create_aeronautics_toolgun.weld_adjust_start"),
                true
        );
    }

    public static Vec3 firstPoint() {
        return SubLevelClientProjection.project(firstSubLevelId, firstLocalPoint, firstPoint);
    }

    public static Vec3 adjustedSecondPoint() {
        return adjustingDistance()
                ? SubLevelClientProjection.project(secondSubLevelId, secondLocalPoint, secondPoint)
                : null;
    }

    public static Direction firstFace() {
        return firstFace;
    }

    public static Direction secondFace() {
        return secondFace;
    }

    public static BlockPos firstBlockPos() {
        return firstBlockPos;
    }

    public static BlockPos secondBlockPos() {
        return secondBlockPos;
    }

    public static boolean hasFirstSelection() {
        return firstPoint != null;
    }

    public static boolean adjustingDistance() {
        return firstPoint != null
                && secondBlockPos != null
                && secondFace != null
                && secondSubLevelId != null
                && secondLocalPoint != null;
    }

    public static void adjustDistance(int delta) {
        if (!adjustingDistance()) {
            return;
        }
        int direction = Integer.signum(delta);
        if (direction == 0) {
            return;
        }
        double step = direction * ClientToolState.getWeldAdjustStep();
        distanceOffset += step;
        syncSecondPosition(step);
    }

    public static boolean confirm() {
        if (!adjustingDistance()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        Vec3 currentFirst = firstPoint();
        Vec3 currentSecond = adjustedSecondPoint();
        if (currentFirst == null || currentSecond == null) {
            return false;
        }

        PacketDistributor.sendToServer(new WeldSubLevelsPayload(
                firstSubLevelId,
                secondSubLevelId,
                firstBlockPos,
                currentFirst.x,
                currentFirst.y,
                currentFirst.z,
                firstFace,
                secondBlockPos,
                secondLocalPoint.x,
                secondLocalPoint.y,
                secondLocalPoint.z,
                currentSecond.x,
                currentSecond.y,
                currentSecond.z,
                currentSecond.x,
                currentSecond.y,
                currentSecond.z,
                secondFace,
                ClientToolState.getConnectionMode().name(),
                ClientToolState.getBearingAxisMode().name()
        ));
        registerPredictedVisual(minecraft, currentFirst, currentSecond);
        clear(false);
        minecraft.player.displayClientMessage(
                Component.translatable("message.create_aeronautics_toolgun.weld_sent"),
                true
        );
        return true;
    }

    public static void clear() {
        clear(true);
    }

    private static void clear(boolean restoreOriginalPosition) {
        if (restoreOriginalPosition
                && secondSubLevelId != null
                && secondLocalPoint != null
                && secondPoint != null
                && distanceOffset != 0.0D) {
            PacketDistributor.sendToServer(new MoveSubLevelPointPayload(
                    secondSubLevelId,
                    secondLocalPoint.x,
                    secondLocalPoint.y,
                    secondLocalPoint.z,
                    secondPoint.x,
                    secondPoint.y,
                    secondPoint.z
            ));
        }
        firstBlockPos = null;
        firstPoint = null;
        firstFace = null;
        firstSubLevelId = null;
        firstLocalPoint = null;
        clearSecondSelection();
    }

    private static void clearSecondSelection() {
        secondBlockPos = null;
        secondPoint = null;
        secondFace = null;
        secondSubLevelId = null;
        secondLocalPoint = null;
        distanceOffset = 0.0D;
    }

    private static void syncSecondPosition(double deltaDistance) {
        if (!adjustingDistance()) {
            return;
        }
        Vec3 adjusted = computeAdjustedSecondPoint(deltaDistance);
        if (adjusted == null) {
            return;
        }
        PacketDistributor.sendToServer(new MoveSubLevelPointPayload(
                secondSubLevelId,
                secondLocalPoint.x,
                secondLocalPoint.y,
                secondLocalPoint.z,
                adjusted.x,
                adjusted.y,
                adjusted.z
        ));
    }

    private static Vec3 computeAdjustedSecondPoint(double deltaDistance) {
        Vec3 currentFirst = firstPoint();
        Vec3 currentSecond = SubLevelClientProjection.project(secondSubLevelId, secondLocalPoint, secondPoint);
        if (currentFirst == null || currentSecond == null) {
            return secondPoint;
        }
        Vector3d axis = WeldAxisGeometry.distanceAdjustAxis(
                new Vector3d(currentFirst.x, currentFirst.y, currentFirst.z),
                new Vector3d(currentSecond.x, currentSecond.y, currentSecond.z),
                firstFace,
                secondFace
        );
        return currentSecond.add(new Vec3(axis.x, axis.y, axis.z).scale(deltaDistance));
    }

    private static void registerPredictedVisual(Minecraft minecraft, Vec3 currentFirst, Vec3 currentSecond) {
        if (firstSubLevelId == null
                || secondSubLevelId == null
                || firstLocalPoint == null
                || secondLocalPoint == null
                || minecraft.level == null) {
            return;
        }
        SubLevel first = Sable.HELPER.getContaining(minecraft.level, firstBlockPos);
        SubLevel second = Sable.HELPER.getContaining(minecraft.level, secondBlockPos);
        if (first == null || second == null) {
            return;
        }

        Vector3d firstAxisLocal = null;
        if (ClientToolState.getConnectionMode() == ConnectionMode.BEARING) {
            Vector3d worldAxis = ClientToolState.getBearingAxisMode().resolveWorldAxis(
                    first,
                    firstFace,
                    second,
                    secondFace
            );
            firstAxisLocal = new Vector3d(worldAxis);
            first.logicalPose().orientation().transformInverse(firstAxisLocal);
            if (firstAxisLocal.lengthSquared() <= 1.0E-6D) {
                firstAxisLocal = null;
            }
        }

        Vector3d midpoint = new Vector3d(
                (currentFirst.x + currentSecond.x) * 0.5D,
                (currentFirst.y + currentSecond.y) * 0.5D,
                (currentFirst.z + currentSecond.z) * 0.5D
        );
        ClientConstraintVisualTracker.registerConstraint(
                firstSubLevelId,
                secondSubLevelId,
                ClientToolState.getConnectionMode(),
                new Vector3d(firstLocalPoint.x, firstLocalPoint.y, firstLocalPoint.z),
                new Vector3d(secondLocalPoint.x, secondLocalPoint.y, secondLocalPoint.z),
                first.logicalPose().transformPositionInverse(midpoint, new Vector3d()),
                second.logicalPose().transformPositionInverse(midpoint, new Vector3d()),
                firstAxisLocal
        );
    }
}
