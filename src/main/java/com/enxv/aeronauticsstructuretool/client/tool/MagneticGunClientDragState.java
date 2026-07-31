package com.enxv.aeronauticsstructuretool.client.tool;

import java.util.UUID;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

final class MagneticGunClientDragState {
    final InteractionHand hand;
    final UUID subLevelId;
    final Vec3 localGrabPoint;
    final boolean creative;
    double distance;
    boolean precisionMode;

    MagneticGunClientDragState(InteractionHand hand, UUID subLevelId, Vec3 localGrabPoint, double distance, boolean creative, boolean precisionMode) {
        this.hand = hand;
        this.subLevelId = subLevelId;
        this.localGrabPoint = localGrabPoint;
        this.distance = distance;
        this.creative = creative;
        this.precisionMode = precisionMode;
    }
}
