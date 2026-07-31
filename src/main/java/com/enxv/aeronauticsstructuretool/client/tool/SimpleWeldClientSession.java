package com.enxv.aeronauticsstructuretool.client.tool;

import com.enxv.aeronauticsstructuretool.client.ClientHooks;
import com.enxv.aeronauticsstructuretool.ClientToolState;
import com.enxv.aeronauticsstructuretool.FinishSimpleWeldPayload;
import com.enxv.aeronauticsstructuretool.RotationAxisMode;
import com.enxv.aeronauticsstructuretool.SimpleWeldAdjustMode;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.UUID;

public final class SimpleWeldClientSession {
    private static UUID childSubLevelId;
    private static Vec3 childLocalPoint;
    private static Vector3d childLocalFaceNormal;
    private static UUID parentSubLevelId;
    private static Vec3 parentLocalPoint;
    private static Vector3d parentLocalFaceNormal;
    private static RotationAxisMode axis = RotationAxisMode.X;
    private static SimpleWeldAdjustMode adjustMode = SimpleWeldAdjustMode.ROTATE;
    private static Quaterniond relativeRotation = new Quaterniond();
    private static Vec3 parentLocalOffset = Vec3.ZERO;

    private SimpleWeldClientSession() {
    }

    public static void select(InteractionHand hand, BlockHitResult hit, Level level, Vec3 beamTarget) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        ClientHooks.showBeam(hand, beamTarget);
        SubLevel containing = Sable.HELPER.getContaining(level, hit.getBlockPos());
        if (adjusting()) {
            cycleAxis();
            return;
        }
        if (containing == null) {
            minecraft.player.displayClientMessage(
                    Component.translatable("message.create_aeronautics_toolgun.simple_weld_need_structure"),
                    true
            );
            return;
        }
        if (childSubLevelId == null) {
            childSubLevelId = containing.getUniqueId();
            childLocalPoint = containing.logicalPose().transformPositionInverse(beamTarget);
            childLocalFaceNormal = resolveLocalFaceNormal(containing, hit.getDirection());
            parentSubLevelId = null;
            parentLocalPoint = null;
            parentLocalFaceNormal = null;
            axis = RotationAxisMode.X;
            adjustMode = SimpleWeldAdjustMode.ROTATE;
            relativeRotation = new Quaterniond();
            parentLocalOffset = Vec3.ZERO;
            minecraft.player.displayClientMessage(
                    Component.translatable("message.create_aeronautics_toolgun.simple_weld_first_point"),
                    true
            );
            return;
        }
        if (parentSubLevelId == null) {
            if (childSubLevelId.equals(containing.getUniqueId())) {
                minecraft.player.displayClientMessage(
                        Component.translatable("message.create_aeronautics_toolgun.simple_weld_same_structure"),
                        true
                );
                return;
            }
            parentSubLevelId = containing.getUniqueId();
            parentLocalPoint = containing.logicalPose().transformPositionInverse(beamTarget);
            parentLocalFaceNormal = resolveLocalFaceNormal(containing, hit.getDirection());
            relativeRotation = snapRelativeRotation(containing, level);
            parentLocalOffset = Vec3.ZERO;
            adjustMode = SimpleWeldAdjustMode.ROTATE;
            axis = RotationAxisMode.X;
            minecraft.player.displayClientMessage(
                    Component.translatable("message.create_aeronautics_toolgun.simple_weld_adjust_start"),
                    true
            );
            return;
        }
        cycleAxis();
    }

    public static boolean hasSelection() {
        return childSubLevelId != null;
    }

    public static boolean adjusting() {
        return childSubLevelId != null
                && childLocalPoint != null
                && parentSubLevelId != null
                && parentLocalPoint != null;
    }

    public static void toggleAdjustMode() {
        if (!adjusting()) {
            return;
        }
        adjustMode = adjustMode.toggle();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    Component.translatable("message.create_aeronautics_toolgun.simple_weld_adjust_mode", adjustMode.title()),
                    true
            );
        }
    }

    public static void adjust(int delta) {
        if (!adjusting()) {
            return;
        }
        int direction = Integer.signum(delta);
        if (direction == 0) {
            return;
        }
        if (adjustMode == SimpleWeldAdjustMode.ROTATE) {
            Quaterniond deltaRotation = createRotationDelta(axis, ClientToolState.getRotationStep() * direction);
            relativeRotation = new Quaterniond(deltaRotation).mul(relativeRotation).normalize();
            return;
        }
        Vector3d deltaLocal = axis.localAxis().mul(
                ClientToolState.getTranslateStep() * direction,
                new Vector3d()
        );
        parentLocalOffset = parentLocalOffset.add(deltaLocal.x, deltaLocal.y, deltaLocal.z);
    }

    public static boolean confirm() {
        if (!adjusting()) {
            return false;
        }
        Quaterniond normalizedRotation = relativeRotation();
        PacketDistributor.sendToServer(new FinishSimpleWeldPayload(
                childSubLevelId,
                parentSubLevelId,
                childLocalPoint.x,
                childLocalPoint.y,
                childLocalPoint.z,
                parentLocalPoint.x,
                parentLocalPoint.y,
                parentLocalPoint.z,
                normalizedRotation.x,
                normalizedRotation.y,
                normalizedRotation.z,
                normalizedRotation.w,
                parentLocalOffset.x,
                parentLocalOffset.y,
                parentLocalOffset.z
        ));
        clearState();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    Component.translatable("message.create_aeronautics_toolgun.simple_weld_sent"),
                    true
            );
        }
        return true;
    }

    public static void clear() {
        if (hasSelection()) {
            clearState();
        }
    }

    public static RotationAxisMode axis() {
        return axis;
    }

    public static Vector3d axisVector() {
        return axis.localAxis();
    }

    public static void cycleAxis() {
        if (!hasSelection()) {
            return;
        }
        axis = axis.next();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    Component.translatable("message.create_aeronautics_toolgun.simple_weld_axis_changed", axis.title()),
                    true
            );
        }
    }

    public static SimpleWeldAdjustMode adjustMode() {
        return adjustMode;
    }

    public static UUID childSubLevelId() {
        return childSubLevelId;
    }

    public static Vec3 childLocalPoint() {
        return childLocalPoint;
    }

    public static Vec3 childWorldPoint() {
        return SubLevelClientProjection.project(childSubLevelId, childLocalPoint, null);
    }

    public static Vec3 parentWorldPoint() {
        return SubLevelClientProjection.project(parentSubLevelId, parentLocalPoint, null, parentLocalOffset);
    }

    public static Quaterniond relativeRotation() {
        return new Quaterniond(relativeRotation).normalize();
    }

    public static Vec3 parentLocalOffset() {
        return parentLocalOffset;
    }

    public static Preview preview(Level level) {
        if (!adjusting()) {
            return null;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }
        SubLevel child = container.getSubLevel(childSubLevelId);
        SubLevel parent = container.getSubLevel(parentSubLevelId);
        if (child == null || parent == null) {
            return null;
        }
        BoundingBox3ic childBounds = child.getPlot() != null ? child.getPlot().getBoundingBox() : null;
        Quaterniond previewOrientation = new Quaterniond(parent.logicalPose().orientation())
                .mul(relativeRotation())
                .normalize();
        Vector3d parentPointWorld = parent.logicalPose().transformPosition(
                new Vector3d(parentLocalPoint.x, parentLocalPoint.y, parentLocalPoint.z),
                new Vector3d()
        );
        Vector3d worldOffset = new Vector3d(parentLocalOffset.x, parentLocalOffset.y, parentLocalOffset.z);
        parent.logicalPose().orientation().transform(worldOffset);
        Vector3d childPointWorld = new Vector3d(parentPointWorld).add(worldOffset);
        Vector3d childPointFromRotationCenter = new Vector3d(
                childLocalPoint.x,
                childLocalPoint.y,
                childLocalPoint.z
        )
                .sub(child.logicalPose().rotationPoint())
                .mul(child.logicalPose().scale());
        previewOrientation.transform(childPointFromRotationCenter);
        Vector3d previewPosition = new Vector3d(childPointWorld).sub(childPointFromRotationCenter);
        double axisHalfLength = 1.8D;
        if (childBounds != null) {
            double sizeX = (childBounds.maxX() - childBounds.minX() + 1.0D) * Math.abs(child.logicalPose().scale().x());
            double sizeY = (childBounds.maxY() - childBounds.minY() + 1.0D) * Math.abs(child.logicalPose().scale().y());
            double sizeZ = (childBounds.maxZ() - childBounds.minZ() + 1.0D) * Math.abs(child.logicalPose().scale().z());
            axisHalfLength = Math.max(axisHalfLength, Math.max(sizeX, Math.max(sizeY, sizeZ)) + 0.75D);
        }
        return new Preview(
                childSubLevelId,
                previewPosition,
                previewOrientation,
                new Quaterniond(parent.logicalPose().orientation()),
                axisHalfLength,
                new Vec3(parentPointWorld.x, parentPointWorld.y, parentPointWorld.z),
                new Vec3(childPointWorld.x, childPointWorld.y, childPointWorld.z)
        );
    }

    private static void clearState() {
        childSubLevelId = null;
        childLocalPoint = null;
        childLocalFaceNormal = null;
        parentSubLevelId = null;
        parentLocalPoint = null;
        parentLocalFaceNormal = null;
        axis = RotationAxisMode.X;
        adjustMode = SimpleWeldAdjustMode.ROTATE;
        relativeRotation = new Quaterniond();
        parentLocalOffset = Vec3.ZERO;
    }

    private static Quaterniond snapRelativeRotation(SubLevel parent, Level level) {
        if (childSubLevelId == null) {
            return new Quaterniond();
        }
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return new Quaterniond();
        }
        SubLevel child = container.getSubLevel(childSubLevelId);
        if (child == null) {
            return new Quaterniond();
        }
        Quaterniond relative = new Quaterniond(parent.logicalPose().orientation())
                .conjugate()
                .mul(child.logicalPose().orientation())
                .normalize();
        double step = ClientToolState.getRotationStep();
        Quaterniond snapped = snapQuaternionToStep(
                relative,
                step,
                childLocalFaceNormal,
                parentLocalFaceNormal
        );
        if (snapped != null) {
            return snapped;
        }
        Vector3d eulerRadians = relative.getEulerAnglesXYZ(new Vector3d());
        return new Quaterniond().rotationXYZ(
                Math.toRadians(snapDegrees(Math.toDegrees(eulerRadians.x), step)),
                Math.toRadians(snapDegrees(Math.toDegrees(eulerRadians.y), step)),
                Math.toRadians(snapDegrees(Math.toDegrees(eulerRadians.z), step))
        );
    }

    private static double snapDegrees(double degrees, double step) {
        if (Math.abs(step) <= 1.0E-6D) {
            return degrees;
        }
        return Math.round(degrees / step) * step;
    }

    private static Quaterniond snapQuaternionToStep(
            Quaterniond relative,
            double step,
            Vector3d childFaceNormalLocal,
            Vector3d parentFaceNormalLocal
    ) {
        if (Math.abs(step) <= 1.0E-6D) {
            return new Quaterniond(relative).normalize();
        }
        int stepsPerCircle = (int) Math.round(360.0D / step);
        if (stepsPerCircle <= 0
                || stepsPerCircle > 24
                || Math.abs(stepsPerCircle * step - 360.0D) > 1.0E-3D) {
            return null;
        }

        Quaterniond normalized = new Quaterniond(relative).normalize();
        Vector3d desiredChildFaceInParentLocal = null;
        if (childFaceNormalLocal != null
                && parentFaceNormalLocal != null
                && childFaceNormalLocal.lengthSquared() > 1.0E-6D
                && parentFaceNormalLocal.lengthSquared() > 1.0E-6D) {
            desiredChildFaceInParentLocal = new Vector3d(parentFaceNormalLocal).normalize().negate();
        }
        double bestFaceScore = -Double.MAX_VALUE;
        double bestRotationScore = -1.0D;
        Quaterniond best = null;
        for (int xi = 0; xi < stepsPerCircle; xi++) {
            double xDegrees = xi * step;
            for (int yi = 0; yi < stepsPerCircle; yi++) {
                double yDegrees = yi * step;
                for (int zi = 0; zi < stepsPerCircle; zi++) {
                    double zDegrees = zi * step;
                    Quaterniond candidate = new Quaterniond().rotationXYZ(
                            Math.toRadians(xDegrees),
                            Math.toRadians(yDegrees),
                            Math.toRadians(zDegrees)
                    ).normalize();
                    double faceScore = 0.0D;
                    if (desiredChildFaceInParentLocal != null) {
                        Vector3d rotatedChildFace = new Quaterniond(candidate)
                                .transform(new Vector3d(childFaceNormalLocal))
                                .normalize();
                        faceScore = rotatedChildFace.dot(desiredChildFaceInParentLocal);
                    }
                    double rotationScore = Math.abs(normalized.dot(candidate));
                    if (faceScore > bestFaceScore + 1.0E-6D
                            || (Math.abs(faceScore - bestFaceScore) <= 1.0E-6D
                            && rotationScore > bestRotationScore)) {
                        bestFaceScore = faceScore;
                        bestRotationScore = rotationScore;
                        best = candidate;
                    }
                }
            }
        }
        return best != null ? best : new Quaterniond(normalized);
    }

    private static Vector3d resolveLocalFaceNormal(SubLevel subLevel, Direction worldFace) {
        Vector3d localNormal = new Vector3d(worldFace.getStepX(), worldFace.getStepY(), worldFace.getStepZ());
        subLevel.logicalPose().orientation().transformInverse(localNormal);
        if (localNormal.lengthSquared() <= 1.0E-6D) {
            return new Vector3d(worldFace.getStepX(), worldFace.getStepY(), worldFace.getStepZ());
        }
        return localNormal.normalize();
    }

    private static Quaterniond createRotationDelta(RotationAxisMode axisMode, double degreesDelta) {
        Vector3d localAxis = axisMode.localAxis();
        if (Math.abs(degreesDelta) <= 1.0E-6D || localAxis.lengthSquared() <= 1.0E-6D) {
            return new Quaterniond();
        }
        return new Quaterniond().fromAxisAngleRad(
                localAxis.x,
                localAxis.y,
                localAxis.z,
                Math.toRadians(degreesDelta)
        );
    }

    public record Preview(
            UUID childSubLevelId,
            Vector3d previewPosition,
            Quaterniond previewOrientation,
            Quaterniond parentOrientation,
            double axisHalfLength,
            Vec3 parentPointWorld,
            Vec3 childPointWorld
    ) {
    }
}
