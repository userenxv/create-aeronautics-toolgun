package com.enxv.aeronauticsstructuretool.client.tool;

import com.enxv.aeronauticsstructuretool.AdjustRotateSubLevelPayload;
import com.enxv.aeronauticsstructuretool.AdjustTranslateSubLevelPayload;
import com.enxv.aeronauticsstructuretool.BeginRotateSubLevelPayload;
import com.enxv.aeronauticsstructuretool.BeginTranslateSubLevelPayload;
import com.enxv.aeronauticsstructuretool.client.ClientHooks;
import com.enxv.aeronauticsstructuretool.ClientToolState;
import com.enxv.aeronauticsstructuretool.FinishRotateSubLevelPayload;
import com.enxv.aeronauticsstructuretool.FinishTranslateSubLevelPayload;
import com.enxv.aeronauticsstructuretool.RotationAxisMode;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.UUID;

public final class TransformClientSession {
    private static UUID rotationSubLevelId;
    private static Vec3 rotationPivotLocalPoint;
    private static RotationAxisMode rotationAxis = RotationAxisMode.X;
    private static Quaterniond localRotation = new Quaterniond();

    private static UUID translationSubLevelId;
    private static Vec3 translationPivotLocalPoint;
    private static RotationAxisMode translationAxis = RotationAxisMode.X;
    private static Vec3 localTranslation = Vec3.ZERO;

    private TransformClientSession() {
    }

    public static void selectRotation(
            InteractionHand hand,
            BlockHitResult hit,
            Level level,
            Vec3 beamTarget
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        ClientHooks.showBeam(hand, beamTarget);
        if (!rotating()) {
            SubLevel containing = Sable.HELPER.getContaining(level, hit.getBlockPos());
            if (containing == null) {
                minecraft.player.displayClientMessage(
                        Component.translatable("message.create_aeronautics_toolgun.rotate_need_structure"),
                        true
                );
                return;
            }
            rotationSubLevelId = containing.getUniqueId();
            rotationPivotLocalPoint = SubLevelClientProjection.plotCenter(containing);
            rotationAxis = RotationAxisMode.X;
            localRotation = new Quaterniond();
            PacketDistributor.sendToServer(new BeginRotateSubLevelPayload(
                    rotationSubLevelId,
                    rotationPivotLocalPoint.x,
                    rotationPivotLocalPoint.y,
                    rotationPivotLocalPoint.z
            ));
            minecraft.player.displayClientMessage(
                    Component.translatable("message.create_aeronautics_toolgun.rotate_selected"),
                    true
            );
            return;
        }
        rotationAxis = rotationAxis.next();
        minecraft.player.displayClientMessage(
                Component.translatable("message.create_aeronautics_toolgun.rotate_axis_changed", rotationAxis.title()),
                true
        );
    }

    public static void selectTranslation(
            InteractionHand hand,
            BlockHitResult hit,
            Level level,
            Vec3 beamTarget
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        ClientHooks.showBeam(hand, beamTarget);
        if (!translating()) {
            SubLevel containing = Sable.HELPER.getContaining(level, hit.getBlockPos());
            if (containing == null) {
                minecraft.player.displayClientMessage(
                        Component.translatable("message.create_aeronautics_toolgun.translate_need_structure"),
                        true
                );
                return;
            }
            translationSubLevelId = containing.getUniqueId();
            translationPivotLocalPoint = SubLevelClientProjection.plotCenter(containing);
            translationAxis = RotationAxisMode.X;
            localTranslation = Vec3.ZERO;
            PacketDistributor.sendToServer(new BeginTranslateSubLevelPayload(
                    translationSubLevelId,
                    translationPivotLocalPoint.x,
                    translationPivotLocalPoint.y,
                    translationPivotLocalPoint.z
            ));
            minecraft.player.displayClientMessage(
                    Component.translatable("message.create_aeronautics_toolgun.translate_selected"),
                    true
            );
            return;
        }
        translationAxis = translationAxis.next();
        minecraft.player.displayClientMessage(
                Component.translatable("message.create_aeronautics_toolgun.translate_axis_changed", translationAxis.title()),
                true
        );
    }

    public static boolean rotating() {
        return rotationSubLevelId != null && rotationPivotLocalPoint != null;
    }

    public static boolean translating() {
        return translationSubLevelId != null && translationPivotLocalPoint != null;
    }

    public static void adjustRotation(int delta) {
        if (!rotating()) {
            return;
        }
        int direction = Integer.signum(delta);
        if (direction == 0) {
            return;
        }
        double degrees = ClientToolState.getRotationStep() * direction;
        localRotation.mul(rotationDelta(rotationAxis, degrees)).normalize();
        PacketDistributor.sendToServer(new AdjustRotateSubLevelPayload(
                rotationSubLevelId,
                rotationAxis.name(),
                degrees
        ));
    }

    public static void adjustTranslation(int delta) {
        if (!translating()) {
            return;
        }
        int direction = Integer.signum(delta);
        if (direction == 0) {
            return;
        }
        double distance = ClientToolState.getTranslateStep() * direction;
        Vector3d deltaLocal = translationAxis.localAxis().mul(distance, new Vector3d());
        localTranslation = localTranslation.add(deltaLocal.x, deltaLocal.y, deltaLocal.z);
        PacketDistributor.sendToServer(new AdjustTranslateSubLevelPayload(
                translationSubLevelId,
                translationAxis.name(),
                distance
        ));
    }

    public static boolean confirmRotation() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!rotating() || minecraft.player == null) {
            return false;
        }
        PacketDistributor.sendToServer(new FinishRotateSubLevelPayload(true));
        resetRotation();
        minecraft.player.displayClientMessage(
                Component.translatable("message.create_aeronautics_toolgun.rotate_confirmed"),
                true
        );
        return true;
    }

    public static boolean confirmTranslation() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!translating() || minecraft.player == null) {
            return false;
        }
        PacketDistributor.sendToServer(new FinishTranslateSubLevelPayload(true));
        resetTranslation();
        minecraft.player.displayClientMessage(
                Component.translatable("message.create_aeronautics_toolgun.translate_confirmed"),
                true
        );
        return true;
    }

    public static void cancelRotation() {
        if (rotating()) {
            PacketDistributor.sendToServer(new FinishRotateSubLevelPayload(false));
            resetRotation();
        }
    }

    public static void cancelTranslation() {
        if (translating()) {
            PacketDistributor.sendToServer(new FinishTranslateSubLevelPayload(false));
            resetTranslation();
        }
    }

    public static Vec3 rotationPivotWorld() {
        return SubLevelClientProjection.project(rotationSubLevelId, rotationPivotLocalPoint, null);
    }

    public static Vec3 translationPivotWorld() {
        return SubLevelClientProjection.project(
                translationSubLevelId,
                translationPivotLocalPoint,
                null,
                localTranslation
        );
    }

    public static UUID rotationSubLevelId() {
        return rotationSubLevelId;
    }

    public static UUID translationSubLevelId() {
        return translationSubLevelId;
    }

    public static Vec3 rotationPivotLocalPoint() {
        return rotationPivotLocalPoint;
    }

    public static Vec3 translationPivotLocalPoint() {
        return translationPivotLocalPoint;
    }

    public static RotationAxisMode rotationAxis() {
        return rotationAxis;
    }

    public static RotationAxisMode translationAxis() {
        return translationAxis;
    }

    public static Quaterniond localRotation() {
        return new Quaterniond(localRotation);
    }

    public static Vec3 localTranslation() {
        return localTranslation;
    }

    private static Quaterniond rotationDelta(RotationAxisMode axisMode, double degrees) {
        Vector3d axis = axisMode.localAxis();
        if (Math.abs(degrees) <= 1.0E-6D || axis.lengthSquared() <= 1.0E-6D) {
            return new Quaterniond();
        }
        return new Quaterniond().fromAxisAngleRad(axis.x, axis.y, axis.z, Math.toRadians(degrees));
    }

    private static void resetRotation() {
        rotationSubLevelId = null;
        rotationPivotLocalPoint = null;
        rotationAxis = RotationAxisMode.X;
        localRotation = new Quaterniond();
    }

    private static void resetTranslation() {
        translationSubLevelId = null;
        translationPivotLocalPoint = null;
        translationAxis = RotationAxisMode.X;
        localTranslation = Vec3.ZERO;
    }
}
