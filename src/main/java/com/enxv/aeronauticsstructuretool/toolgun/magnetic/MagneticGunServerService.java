package com.enxv.aeronauticsstructuretool.toolgun.magnetic;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.MagneticGunLaunchPayload;
import com.enxv.aeronauticsstructuretool.MagneticGunRotatePayload;
import com.enxv.aeronauticsstructuretool.MagneticGunStartPayload;
import com.enxv.aeronauticsstructuretool.ModItems;
import com.enxv.aeronauticsstructuretool.compat.simulated.SimulatedPhysicsStaffLockBridge;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class MagneticGunServerService {
    private static final double MAX_DISTANCE_ADJUSTMENT = 1.0D;
    private static final double MAX_ROTATION_DEGREES_PER_PACKET = 45.0D;
    private static final long STANDARD_DURABILITY_INTERVAL_TICKS = 5L;
    private static final int STANDARD_LAUNCH_DURABILITY_COST = 30;
    private static final Map<UUID, ActiveDrag> ACTIVE_DRAGS = new ConcurrentHashMap<>();

    public static ActionResult start(ServerPlayer player, MagneticGunStartPayload payload) {
        removeActiveDrag(player.getUUID());
        if (!(player.level() instanceof ServerLevel level)) {
            return failure(player, "start", ActionResult.INVALID_LEVEL);
        }
        if (!isValidStartPayload(payload)) {
            return failure(player, "start", ActionResult.INVALID_REQUEST);
        }
        MagneticDragPhysics.Profile profile = getHeldProfile(player);
        if (profile == null) {
            return failure(player, "start", ActionResult.NOT_HOLDING_GUN);
        }

        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (!(container.getSubLevel(payload.subLevelId()) instanceof ServerSubLevel subLevel)) {
            return failure(player, "start", ActionResult.TARGET_NOT_FOUND);
        }
        SimulatedPhysicsStaffLockBridge.LockState lockState =
                SimulatedPhysicsStaffLockBridge.lockState(level, subLevel);
        if (lockState == SimulatedPhysicsStaffLockBridge.LockState.FAILED) {
            return failure(player, "start", ActionResult.LOCK_OPERATION_FAILED);
        }
        if (lockState == SimulatedPhysicsStaffLockBridge.LockState.LOCKED
                && (!SimulatedPhysicsStaffLockBridge.removeLock(level, subLevel)
                || SimulatedPhysicsStaffLockBridge.lockState(level, subLevel)
                != SimulatedPhysicsStaffLockBridge.LockState.UNLOCKED)) {
            return failure(player, "start", ActionResult.LOCK_OPERATION_FAILED);
        }

        Vector3d hitWorld = new Vector3d(payload.hitX(), payload.hitY(), payload.hitZ());
        double maximumReach = MagneticDragPhysics.clampDistance(Double.POSITIVE_INFINITY, profile);
        if (hitWorld.distance(new Vector3d(player.getEyePosition().x, player.getEyePosition().y, player.getEyePosition().z))
                > maximumReach + 2.0D) {
            return failure(player, "start", ActionResult.INVALID_REQUEST);
        }

        Pose3dc pose = subLevel.logicalPose();
        Vector3d localGrabPoint = pose.transformPositionInverse(hitWorld, new Vector3d());
        if (!isFinite(localGrabPoint)) {
            return failure(player, "start", ActionResult.INVALID_REQUEST);
        }
        double initialDistance = MagneticDragPhysics.clampDistance(payload.initialDistance(), profile);
        Vector3d initialTarget = desiredTarget(player, initialDistance, 1.0D);
        ACTIVE_DRAGS.put(player.getUUID(), new ActiveDrag(
                player.getUUID(),
                level.dimension().location().toString(),
                payload.subLevelId(),
                localGrabPoint,
                initialDistance,
                MagneticDragControlMath.uprightOrientation(pose.orientation(), new Quaterniond()),
                level.getGameTime(),
                profile,
                initialTarget,
                player.getYRot(),
                profile == MagneticDragPhysics.Profile.CREATIVE && payload.precisionMode()
        ));
        return ActionResult.SUCCESS;
    }

    public static ActionResult stop(ServerPlayer player) {
        removeActiveDrag(player.getUUID());
        return ActionResult.SUCCESS;
    }

    public static ActionResult adjustDistance(ServerPlayer player, double delta) {
        if (!Double.isFinite(delta) || Math.abs(delta) < 1.0E-4D
                || Math.abs(delta) > MAX_DISTANCE_ADJUSTMENT) {
            return failure(player, "adjust distance", ActionResult.INVALID_REQUEST);
        }
        ActiveDragLookup lookup = activeDragFor(player, "adjust distance");
        if (!lookup.successful()) {
            return lookup.result;
        }
        ActiveDrag drag = lookup.drag;
        drag.distance = MagneticDragPhysics.clampDistance(drag.distance + delta, drag.profile);
        return ActionResult.SUCCESS;
    }

    public static ActionResult rotate(ServerPlayer player, MagneticGunRotatePayload payload) {
        if (payload == null
                || !validRotationDelta(payload.yawDegrees())
                || !validRotationDelta(payload.pitchDegrees())
                || !validRotationDelta(payload.rollDegrees())) {
            return failure(player, "rotate", ActionResult.INVALID_REQUEST);
        }
        ActiveDragLookup lookup = activeDragFor(player, "rotate");
        if (!lookup.successful()) {
            return lookup.result;
        }
        ActiveDrag drag = lookup.drag;

        CameraAxes axes = cameraAxes(player);
        drag.applyManualRotation(axes, payload);
        return ActionResult.SUCCESS;
    }

    public static ActionResult setPrecisionMode(ServerPlayer player, boolean enabled) {
        ActiveDragLookup lookup = activeDragFor(player, "precision mode");
        if (!lookup.successful()) {
            return lookup.result;
        }
        ActiveDrag drag = lookup.drag;
        drag.precisionMode = drag.profile == MagneticDragPhysics.Profile.CREATIVE && enabled;
        return ActionResult.SUCCESS;
    }

    public static ActionResult launch(ServerPlayer player, MagneticGunLaunchPayload payload) {
        if (!(player.level() instanceof ServerLevel level) || !isValidLaunchPayload(payload)) {
            return failure(player, "launch", ActionResult.INVALID_REQUEST);
        }
        if (getHeldProfile(player) != MagneticDragPhysics.Profile.STANDARD) {
            return failure(player, "launch", ActionResult.INVALID_REQUEST);
        }

        ActiveDrag drag = removeActiveDrag(player.getUUID());
        if (drag != null && (!drag.dimensionId.equals(level.dimension().location().toString())
                || drag.profile != MagneticDragPhysics.Profile.STANDARD
                || !drag.subLevelId.equals(payload.subLevelId()))) {
            return failure(player, "launch", ActionResult.INVALID_REQUEST);
        }
        Vector3d hitWorld = new Vector3d(payload.hitX(), payload.hitY(), payload.hitZ());
        double maximumReach = MagneticDragPhysics.clampDistance(
                Double.POSITIVE_INFINITY,
                MagneticDragPhysics.Profile.STANDARD
        );
        if (drag == null && hitWorld.distance(new Vector3d(
                player.getEyePosition().x,
                player.getEyePosition().y,
                player.getEyePosition().z
        )) > maximumReach + 2.0D) {
            return failure(player, "launch", ActionResult.INVALID_REQUEST);
        }

        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (!(container.getSubLevel(payload.subLevelId()) instanceof ServerSubLevel subLevel)) {
            return failure(player, "launch", ActionResult.TARGET_NOT_FOUND);
        }
        if (!ensureUnlocked(level, subLevel)) {
            return failure(player, "launch", ActionResult.LOCK_OPERATION_FAILED);
        }

        MagneticDragPhysics.launch(
                subLevel,
                player.getLookAngle().normalize(),
                MagneticDragPhysics.Profile.STANDARD
        );
        damageStandardGun(player, STANDARD_LAUNCH_DURABILITY_COST);
        return ActionResult.SUCCESS;
    }

    public static ActionResult setFrozen(ServerPlayer player, boolean frozen) {
        if (!frozen || !(player.level() instanceof ServerLevel level)) {
            return failure(player, "freeze", ActionResult.INVALID_REQUEST);
        }
        ActiveDragLookup lookup = activeDragFor(player, "freeze");
        if (!lookup.successful()) {
            return lookup.result;
        }
        ActiveDrag drag = lookup.drag;
        if (drag.profile != MagneticDragPhysics.Profile.CREATIVE) {
            return failure(player, "freeze", ActionResult.CREATIVE_REQUIRED);
        }

        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (!(container.getSubLevel(drag.subLevelId) instanceof ServerSubLevel subLevel)) {
            removeActiveDrag(player.getUUID());
            return failure(player, "freeze", ActionResult.TARGET_NOT_FOUND);
        }
        SimulatedPhysicsStaffLockBridge.LockState lockState =
                SimulatedPhysicsStaffLockBridge.lockState(level, subLevel);
        if (lockState == SimulatedPhysicsStaffLockBridge.LockState.UNAVAILABLE) {
            return failure(player, "freeze", ActionResult.LOCK_INTEGRATION_UNAVAILABLE);
        }
        if (lockState == SimulatedPhysicsStaffLockBridge.LockState.FAILED) {
            return failure(player, "freeze", ActionResult.LOCK_OPERATION_FAILED);
        }

        removeActiveDrag(player.getUUID());
        MagneticDragPhysics.stopMotion(subLevel);
        if (lockState == SimulatedPhysicsStaffLockBridge.LockState.UNLOCKED
                && (!SimulatedPhysicsStaffLockBridge.toggleLock(level, subLevel.getUniqueId())
                || SimulatedPhysicsStaffLockBridge.lockState(level, subLevel)
                != SimulatedPhysicsStaffLockBridge.LockState.LOCKED)) {
            return failure(player, "freeze", ActionResult.LOCK_OPERATION_FAILED);
        }
        return ActionResult.SUCCESS;
    }

    @SubscribeEvent
    public void onPrePhysicsTick(ForgeSablePrePhysicsTickEvent event) {
        ServerLevel level = event.getPhysicsSystem().getLevel();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        PhysicsPipeline pipeline = event.getPhysicsSystem().getPipeline();
        double timeStep = event.getTimeStep();
        double partialPhysicsTick = event.getPhysicsSystem().getPartialPhysicsTick();

        Iterator<ActiveDrag> iterator = ACTIVE_DRAGS.values().iterator();
        while (iterator.hasNext()) {
            ActiveDrag drag = iterator.next();
            if (!drag.dimensionId.equals(level.dimension().location().toString())) {
                continue;
            }
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(drag.playerId);
            if (player == null || player.level() != level || getHeldProfile(player) != drag.profile) {
                removeActiveDrag(drag);
                continue;
            }
            if (!(container.getSubLevel(drag.subLevelId) instanceof ServerSubLevel subLevel)) {
                removeActiveDrag(drag);
                continue;
            }
            if (drag.profile == MagneticDragPhysics.Profile.STANDARD
                    && level.getGameTime() >= drag.nextDurabilityGameTime) {
                drag.nextDurabilityGameTime = level.getGameTime() + STANDARD_DURABILITY_INTERVAL_TICKS;
                if (damageStandardGun(player, 1)) {
                    removeActiveDrag(drag);
                    continue;
                }
            }

            Vector3d rawTarget = desiredTarget(player, drag.distance, partialPhysicsTick);
            Vector3d target = drag.updateCommandedTarget(rawTarget, timeStep);
            drag.followCameraYaw(player.getYRot());
            Vector3d currentGrabPoint = subLevel.logicalPose()
                    .transformPosition(drag.localGrabPoint, new Vector3d());
            double maximumTrackingError = MagneticDragPhysics.maxTrackingError(drag.profile);
            if (currentGrabPoint.distanceSquared(target)
                    > maximumTrackingError * maximumTrackingError) {
                removeActiveDrag(drag);
                ActionResult result = failure(player, "tracking", ActionResult.TRACKING_LOST);
                player.sendSystemMessage(Component.translatable(
                        "message.create_aeronautics_toolgun.magnetic_gun_failed",
                        Component.translatable(result.failureTranslationKey())
                ));
                continue;
            }
            try {
                drag.replaceConstraint(pipeline, subLevel, target);
            } catch (IOException | RuntimeException exception) {
                removeActiveDrag(drag);
                AeronauticsStructureToolMod.LOGGER.error(
                        "Magnetic gun constraint failed for player {} and structure {}",
                        player.getGameProfile().getName(),
                        drag.subLevelId,
                        exception
                );
                ActionResult result = failure(player, "constraint", ActionResult.CONSTRAINT_FAILED);
                player.sendSystemMessage(Component.translatable(
                        "message.create_aeronautics_toolgun.magnetic_gun_failed",
                        Component.translatable(result.failureTranslationKey())
                ));
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        removeActiveDrag(event.getEntity().getUUID());
    }

    private static ActiveDragLookup activeDragFor(ServerPlayer player, String action) {
        ActiveDrag drag = ACTIVE_DRAGS.get(player.getUUID());
        if (drag == null) {
            return new ActiveDragLookup(null, failure(player, action, ActionResult.NO_ACTIVE_DRAG));
        }
        if (!(player.level() instanceof ServerLevel level)
                || !drag.dimensionId.equals(level.dimension().location().toString())) {
            removeActiveDrag(player.getUUID());
            return new ActiveDragLookup(null, failure(player, action, ActionResult.WRONG_DIMENSION));
        }
        if (getHeldProfile(player) != drag.profile) {
            removeActiveDrag(player.getUUID());
            return new ActiveDragLookup(null, failure(player, action, ActionResult.NOT_HOLDING_GUN));
        }
        return new ActiveDragLookup(drag, ActionResult.SUCCESS);
    }

    private static ActiveDrag removeActiveDrag(UUID playerId) {
        ActiveDrag removed = ACTIVE_DRAGS.remove(playerId);
        if (removed != null) {
            removed.close();
        }
        return removed;
    }

    private static void removeActiveDrag(ActiveDrag drag) {
        ACTIVE_DRAGS.remove(drag.playerId, drag);
        drag.close();
    }

    private static Vector3d desiredTarget(ServerPlayer player, double distance, double partialPhysicsTick) {
        double eyeX = Mth.lerp(partialPhysicsTick, player.xOld, player.getX());
        double eyeY = Mth.lerp(partialPhysicsTick, player.yOld, player.getY()) + player.getEyeHeight();
        double eyeZ = Mth.lerp(partialPhysicsTick, player.zOld, player.getZ());
        Vec3 look = player.getLookAngle().normalize();
        return new Vector3d(
                eyeX + look.x * distance,
                eyeY + look.y * distance,
                eyeZ + look.z * distance
        );
    }

    private static CameraAxes cameraAxes(ServerPlayer player) {
        Vec3 look = player.getLookAngle().normalize();
        Vector3d forward = new Vector3d(look.x, look.y, look.z).normalize();
        Vector3d right = new Vector3d(forward).cross(0.0D, 1.0D, 0.0D);
        if (right.lengthSquared() < 1.0E-8D) {
            double yaw = Math.toRadians(player.getYRot());
            right.set(-Math.cos(yaw), 0.0D, -Math.sin(yaw));
        } else {
            right.normalize();
        }
        return new CameraAxes(right);
    }

    private static MagneticDragPhysics.Profile getHeldProfile(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (ModItems.isCreativeMagneticGun(main)) {
            return MagneticDragPhysics.Profile.CREATIVE;
        }
        if (main.is(ModItems.MAGNETIC_GUN.get())) {
            return MagneticDragPhysics.Profile.STANDARD;
        }
        ItemStack off = player.getOffhandItem();
        if (ModItems.isCreativeMagneticGun(off)) {
            return MagneticDragPhysics.Profile.CREATIVE;
        }
        if (off.is(ModItems.MAGNETIC_GUN.get())) {
            return MagneticDragPhysics.Profile.STANDARD;
        }
        return null;
    }

    private static boolean damageStandardGun(ServerPlayer player, int amount) {
        if (damageGunInHand(player, player.getMainHandItem(), InteractionHand.MAIN_HAND, amount)) {
            return true;
        }
        return damageGunInHand(player, player.getOffhandItem(), InteractionHand.OFF_HAND, amount);
    }

    private static boolean damageGunInHand(
            ServerPlayer player,
            ItemStack stack,
            InteractionHand hand,
            int amount
    ) {
        if (!stack.is(ModItems.MAGNETIC_GUN.get())) {
            return false;
        }
        EquipmentSlot slot = hand == InteractionHand.MAIN_HAND
                ? EquipmentSlot.MAINHAND
                : EquipmentSlot.OFFHAND;
        stack.hurtAndBreak(amount, player, slot);
        return stack.isEmpty();
    }

    private static boolean isValidStartPayload(MagneticGunStartPayload payload) {
        return payload != null
                && payload.subLevelId() != null
                && Double.isFinite(payload.hitX())
                && Double.isFinite(payload.hitY())
                && Double.isFinite(payload.hitZ())
                && Double.isFinite(payload.initialDistance());
    }

    private static boolean isValidLaunchPayload(
            MagneticGunLaunchPayload payload
    ) {
        return payload != null
                && payload.subLevelId() != null
                && Double.isFinite(payload.hitX())
                && Double.isFinite(payload.hitY())
                && Double.isFinite(payload.hitZ());
    }

    private static boolean ensureUnlocked(ServerLevel level, ServerSubLevel subLevel) {
        SimulatedPhysicsStaffLockBridge.LockState lockState =
                SimulatedPhysicsStaffLockBridge.lockState(level, subLevel);
        if (lockState == SimulatedPhysicsStaffLockBridge.LockState.FAILED) {
            return false;
        }
        return lockState != SimulatedPhysicsStaffLockBridge.LockState.LOCKED
                || SimulatedPhysicsStaffLockBridge.removeLock(level, subLevel)
                && SimulatedPhysicsStaffLockBridge.lockState(level, subLevel)
                == SimulatedPhysicsStaffLockBridge.LockState.UNLOCKED;
    }

    private static boolean validRotationDelta(double value) {
        return Double.isFinite(value) && Math.abs(value) <= MAX_ROTATION_DEGREES_PER_PACKET;
    }

    private static boolean isFinite(Vector3d value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    private static ActionResult failure(ServerPlayer player, String action, ActionResult result) {
        if (result == ActionResult.NO_ACTIVE_DRAG || result == ActionResult.INVALID_REQUEST) {
            AeronauticsStructureToolMod.LOGGER.debug(
                    "Magnetic gun {} rejected for player {}: {}",
                    action,
                    player.getGameProfile().getName(),
                    result
            );
        } else {
            AeronauticsStructureToolMod.LOGGER.warn(
                    "Magnetic gun {} failed for player {}: {}",
                    action,
                    player.getGameProfile().getName(),
                    result
            );
        }
        return result;
    }

    public enum ActionResult {
        SUCCESS(null),
        INVALID_LEVEL("message.create_aeronautics_toolgun.magnetic_gun_reason.invalid_level"),
        INVALID_REQUEST("message.create_aeronautics_toolgun.magnetic_gun_reason.invalid_request"),
        NOT_HOLDING_GUN("message.create_aeronautics_toolgun.magnetic_gun_reason.not_holding"),
        TARGET_NOT_FOUND("message.create_aeronautics_toolgun.magnetic_gun_reason.target_missing"),
        NO_ACTIVE_DRAG("message.create_aeronautics_toolgun.magnetic_gun_reason.no_active_drag"),
        WRONG_DIMENSION("message.create_aeronautics_toolgun.magnetic_gun_reason.wrong_dimension"),
        TRACKING_LOST("message.create_aeronautics_toolgun.magnetic_gun_reason.tracking_lost"),
        CONSTRAINT_FAILED("message.create_aeronautics_toolgun.magnetic_gun_reason.constraint_failed"),
        CREATIVE_REQUIRED("message.create_aeronautics_toolgun.magnetic_gun_reason.creative_required"),
        LOCK_INTEGRATION_UNAVAILABLE("message.create_aeronautics_toolgun.magnetic_gun_reason.lock_unavailable"),
        LOCK_OPERATION_FAILED("message.create_aeronautics_toolgun.magnetic_gun_reason.lock_failed");

        private final String failureTranslationKey;

        ActionResult(String failureTranslationKey) {
            this.failureTranslationKey = failureTranslationKey;
        }

        public boolean successful() {
            return this == SUCCESS;
        }

        public String failureTranslationKey() {
            if (this.failureTranslationKey == null) {
                throw new IllegalStateException("successful magnetic gun result has no failure message");
            }
            return this.failureTranslationKey;
        }
    }

    private record ActiveDragLookup(ActiveDrag drag, ActionResult result) {
        private boolean successful() {
            return this.drag != null && this.result.successful();
        }
    }

    private static final class ActiveDrag {
        private final UUID playerId;
        private final String dimensionId;
        private final UUID subLevelId;
        private final Vector3d localGrabPoint;
        private final MagneticDragPhysics.Profile profile;
        private final Vector3d commandedTarget;
        private final Quaterniond uprightOrientation;
        private final Quaterniond manualRotation;
        private double lastCameraYaw;
        private long nextDurabilityGameTime;
        private double distance;
        private boolean precisionMode;
        private PhysicsConstraintHandle constraint;

        private ActiveDrag(
                UUID playerId,
                String dimensionId,
                UUID subLevelId,
                Vector3d localGrabPoint,
                double distance,
                Quaterniond uprightOrientation,
                long startGameTime,
                MagneticDragPhysics.Profile profile,
                Vector3d initialTarget,
                double initialCameraYaw,
                boolean precisionMode
        ) {
            this.playerId = playerId;
            this.dimensionId = dimensionId;
            this.subLevelId = subLevelId;
            this.localGrabPoint = localGrabPoint;
            this.distance = distance;
            this.uprightOrientation = new Quaterniond(uprightOrientation).normalize();
            this.manualRotation = new Quaterniond();
            this.profile = profile;
            this.commandedTarget = new Vector3d(initialTarget);
            this.lastCameraYaw = initialCameraYaw;
            this.nextDurabilityGameTime = startGameTime + STANDARD_DURABILITY_INTERVAL_TICKS;
            this.precisionMode = precisionMode;
        }

        private Quaterniond targetOrientation(Quaterniond destination) {
            return this.uprightOrientation.mul(this.manualRotation, destination).normalize();
        }

        private void followCameraYaw(float currentCameraYaw) {
            double deltaDegrees = currentCameraYaw - this.lastCameraYaw;
            while (deltaDegrees > 180.0D) {
                deltaDegrees -= 360.0D;
            }
            while (deltaDegrees < -180.0D) {
                deltaDegrees += 360.0D;
            }
            this.lastCameraYaw = currentCameraYaw;
            if (Math.abs(deltaDegrees) < 1.0E-7D) {
                return;
            }
            // Camera yaw and the drag frame use opposite signs.
            this.uprightOrientation.rotateY(Math.toRadians(-deltaDegrees)).normalize();
        }

        private void applyManualRotation(CameraAxes axes, MagneticGunRotatePayload payload) {
            Quaterniond orientation = targetOrientation(new Quaterniond());
            if (Math.abs(payload.yawDegrees()) > 1.0E-9D) {
                // Physics Staff horizontal input rotates around local Y.
                orientation.rotateLocalY(Math.toRadians(payload.yawDegrees()));
            }
            if (Math.abs(payload.pitchDegrees()) > 1.0E-9D) {
                Quaterniond pitch = new Quaterniond().fromAxisAngleRad(
                        axes.right.x, axes.right.y, axes.right.z,
                        Math.toRadians(payload.pitchDegrees())
                );
                pitch.mul(orientation, orientation);
            }
            if (Math.abs(payload.rollDegrees()) > 1.0E-9D) {
                orientation.rotateLocalZ(Math.toRadians(payload.rollDegrees()));
            }
            new Quaterniond(this.uprightOrientation)
                    .conjugate()
                    .mul(orientation, this.manualRotation)
                    .normalize();
        }

        private Vector3d updateCommandedTarget(Vector3d rawTarget, double timeStep) {
            if (!this.precisionMode) {
                return this.commandedTarget.set(rawTarget);
            }
            Vector3d movement = rawTarget.sub(this.commandedTarget, new Vector3d());
            double maximumMovement = MagneticDragPhysics.precisionTargetSpeed(this.profile)
                    * Math.max(timeStep, 0.0D);
            if (movement.lengthSquared() > maximumMovement * maximumMovement) {
                movement.normalize(maximumMovement);
            }
            return this.commandedTarget.add(movement);
        }

        private void replaceConstraint(
                PhysicsPipeline pipeline,
                ServerSubLevel subLevel,
                Vector3d target
        ) throws IOException {
            if (!removeConstraint()) {
                throw new IOException("failed to replace the previous magnetic drag constraint");
            }
            this.constraint = MagneticDragPhysics.createDragConstraint(
                    pipeline,
                    subLevel,
                    this.localGrabPoint,
                    target,
                    targetOrientation(new Quaterniond()),
                    this.profile
            );
        }

        private void close() {
            removeConstraint();
        }

        private boolean removeConstraint() {
            PhysicsConstraintHandle current = this.constraint;
            if (current == null) {
                return true;
            }
            try {
                current.remove();
                this.constraint = null;
                return true;
            } catch (RuntimeException exception) {
                AeronauticsStructureToolMod.LOGGER.warn(
                        "Failed to remove magnetic drag constraint for structure {}",
                        this.subLevelId,
                        exception
                );
                return false;
            }
        }
    }

    private record CameraAxes(Vector3d right) {
    }
}
