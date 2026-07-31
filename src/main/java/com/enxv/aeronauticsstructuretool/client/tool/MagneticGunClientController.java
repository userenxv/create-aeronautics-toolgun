package com.enxv.aeronauticsstructuretool.client.tool;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.MagneticGunAdjustDistancePayload;
import com.enxv.aeronauticsstructuretool.MagneticGunFreezePayload;
import com.enxv.aeronauticsstructuretool.MagneticGunLaunchPayload;
import com.enxv.aeronauticsstructuretool.MagneticGunPrecisionPayload;
import com.enxv.aeronauticsstructuretool.MagneticGunRotatePayload;
import com.enxv.aeronauticsstructuretool.MagneticGunStartPayload;
import com.enxv.aeronauticsstructuretool.MagneticGunStartResultPayload;
import com.enxv.aeronauticsstructuretool.MagneticGunStopPayload;
import com.enxv.aeronauticsstructuretool.ModItems;
import com.enxv.aeronauticsstructuretool.client.ClientHooks;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = AeronauticsStructureToolMod.MOD_ID, value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class MagneticGunClientController {
    private static final String KEY_CATEGORY = "key.categories.create_aeronautics_toolgun";
    private static final KeyMapping ROTATE_OBJECT = new KeyMapping(
            "key.create_aeronautics_toolgun.rotate_magnetic_object",
            GLFW.GLFW_KEY_TAB,
            KEY_CATEGORY
    );
    private static final double CREATIVE_MAX_USE_DISTANCE = 256.0D;
    private static final double STANDARD_MAX_USE_DISTANCE = 24.0D;
    private static final double MIN_DISTANCE = 2.5D;
    private static final double NORMAL_DISTANCE_STEP = 0.5D;
    private static final double PRECISION_DISTANCE_STEP = 0.15D;
    private static final double ROLL_STEP_DEGREES = 5.0D;
    private static final double ENTITY_TURN_SCALE = 0.15D;
    private static final double ZERO_TURN_SENSITIVITY = -1.0D / 3.0D;
    private static final double MAX_ROTATION_DEGREES_PER_PACKET = 45.0D;
    private static final int MAX_ROTATION_PACKETS_PER_TICK = 8;

    private static boolean wasUseDown;
    private static MagneticGunClientDragState activeDrag;
    private static double pendingYawDegrees;
    private static double pendingPitchDegrees;
    private static double pendingRollDegrees;

    private MagneticGunClientController() {
    }

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ROTATE_OBJECT);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            resetClientState();
            return;
        }
        if (minecraft.screen != null) {
            clearPendingRotation();
            return;
        }

        InteractionHand hand = findActiveHand(minecraft.player);
        boolean useDown = hand != null && minecraft.options.keyUse.isDown();

        if (useDown && !wasUseDown) {
            tryStartDrag(minecraft, hand);
        } else if (!useDown && wasUseDown) {
            stopDrag();
        } else if (useDown && activeDrag != null) {
            renderActiveBeam(minecraft);
        }

        flushPendingRotation();
        updatePrecisionMode(minecraft);
        if (hand == null && activeDrag != null) {
            stopDrag();
        }
        wasUseDown = useDown;
    }

    @SubscribeEvent
    public static void onCalculatePlayerTurn(CalculatePlayerTurnEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (activeDrag == null
                || minecraft.player == null
                || minecraft.screen != null
                || !isRotateKeyDown(minecraft)) {
            return;
        }

        double sensitivity = event.getMouseSensitivity();
        double scaledSensitivity = sensitivity * 0.6D + 0.2D;
        double mouseScale = scaledSensitivity * scaledSensitivity * scaledSensitivity * 8.0D;
        double invertY = Boolean.TRUE.equals(minecraft.options.invertYMouse().get()) ? -1.0D : 1.0D;
        pendingYawDegrees += minecraft.mouseHandler.getXVelocity() * mouseScale * ENTITY_TURN_SCALE;
        pendingPitchDegrees += minecraft.mouseHandler.getYVelocity() * mouseScale * ENTITY_TURN_SCALE * invertY;

        event.setMouseSensitivity(ZERO_TURN_SENSITIVITY);
        event.setCinematicCameraEnabled(false);
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (activeDrag == null || !event.isRightDown()) {
            return;
        }
        double scroll = event.getScrollDeltaY();
        if (Math.abs(scroll) < 1.0E-4D) {
            return;
        }

        if (isRotateKeyDown(minecraft)) {
            pendingRollDegrees += Math.signum(scroll) * ROLL_STEP_DEGREES;
            event.setCanceled(true);
            return;
        }

        double step = minecraft.options.keyShift.isDown()
                ? PRECISION_DISTANCE_STEP
                : NORMAL_DISTANCE_STEP;
        double delta = Math.signum(scroll) * step;
        double maximum = activeDrag.creative ? CREATIVE_MAX_USE_DISTANCE : STANDARD_MAX_USE_DISTANCE;
        double nextDistance = Math.max(MIN_DISTANCE, Math.min(maximum, activeDrag.distance + delta));
        double appliedDelta = nextDistance - activeDrag.distance;
        if (Math.abs(appliedDelta) > 1.0E-4D) {
            activeDrag.distance = nextDistance;
            PacketDistributor.sendToServer(new MagneticGunAdjustDistancePayload(appliedDelta));
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onInteractionKeyTriggered(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !isHoldingMagneticGun(minecraft.player)) {
            return;
        }

        InteractionHand hand = findActiveHand(minecraft.player);
        if (activeDrag != null) {
            if (activeDrag.creative) {
                ClientHooks.stopMagneticBeam(activeDrag.hand);
                PacketDistributor.sendToServer(new MagneticGunFreezePayload(true));
            } else {
                launchActiveDrag(minecraft);
            }
            activeDrag = null;
            clearPendingRotation();
        } else if (hand != null && minecraft.player.getItemInHand(hand).is(ModItems.MAGNETIC_GUN.get())) {
            launchAimedStructure(minecraft, hand);
        }
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    private static void tryStartDrag(Minecraft minecraft, InteractionHand hand) {
        if (hand == null || minecraft.player == null || minecraft.level == null) {
            return;
        }
        ItemStack heldStack = minecraft.player.getItemInHand(hand);
        boolean creative = ModItems.isCreativeMagneticGun(heldStack);
        boolean precisionMode = creative && minecraft.options.keyShift.isDown();
        double maximumDistance = creative ? CREATIVE_MAX_USE_DISTANCE : STANDARD_MAX_USE_DISTANCE;
        HitResult hit = minecraft.player.pick(maximumDistance, 0.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        ClientLevel level = minecraft.level;
        SubLevel containing = Sable.HELPER.getContaining(level, blockHit.getBlockPos());
        if (containing == null) {
            return;
        }

        Vec3 worldHit = Sable.HELPER.projectOutOfSubLevel(level, blockHit.getLocation());
        Vec3 localGrabPoint = containing.logicalPose().transformPositionInverse(worldHit);
        double distance = minecraft.player.getEyePosition().distanceTo(worldHit);
        double initialDistance = Math.max(MIN_DISTANCE, Math.min(maximumDistance, distance));
        activeDrag = new MagneticGunClientDragState(
                hand,
                containing.getUniqueId(),
                localGrabPoint,
                initialDistance,
                creative,
                precisionMode
        );
        PacketDistributor.sendToServer(new MagneticGunStartPayload(
                containing.getUniqueId(),
                worldHit.x,
                worldHit.y,
                worldHit.z,
                initialDistance,
                precisionMode
        ));
        ClientHooks.showSustainedBeam(hand, worldHit);
    }

    private static void renderActiveBeam(Minecraft minecraft) {
        SubLevelContainer container = SubLevelContainer.getContainer(minecraft.level);
        SubLevel subLevel = container.getSubLevel(activeDrag.subLevelId);
        if (subLevel != null) {
            Vec3 worldGrabPoint = subLevel.logicalPose().transformPosition(activeDrag.localGrabPoint);
            ClientHooks.showSustainedBeam(activeDrag.hand, worldGrabPoint);
        } else {
            Vec3 fallback = minecraft.player.getEyePosition()
                    .add(minecraft.player.getLookAngle().normalize().scale(activeDrag.distance));
            ClientHooks.showSustainedBeam(activeDrag.hand, fallback);
        }
    }

    private static void launchActiveDrag(Minecraft minecraft) {
        Vec3 worldHit = getCurrentGrabWorldPoint(minecraft.level);
        if (worldHit == null) {
            worldHit = getCurrentTargetWorldPoint(minecraft.player);
        }
        if (worldHit == null) {
            ClientHooks.stopMagneticBeam(activeDrag.hand);
            return;
        }
        PacketDistributor.sendToServer(new MagneticGunLaunchPayload(
                activeDrag.subLevelId,
                worldHit.x,
                worldHit.y,
                worldHit.z
        ));
        ClientHooks.showMagneticLaunch(activeDrag.hand, worldHit);
    }

    private static void launchAimedStructure(Minecraft minecraft, InteractionHand hand) {
        HitResult hit = minecraft.player.pick(STANDARD_MAX_USE_DISTANCE, 0.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        SubLevel containing = Sable.HELPER.getContaining(minecraft.level, blockHit.getBlockPos());
        if (containing == null) {
            return;
        }
        Vec3 worldHit = Sable.HELPER.projectOutOfSubLevel(minecraft.level, blockHit.getLocation());
        PacketDistributor.sendToServer(new MagneticGunLaunchPayload(
                containing.getUniqueId(),
                worldHit.x,
                worldHit.y,
                worldHit.z
        ));
        ClientHooks.showMagneticLaunch(hand, worldHit);
    }

    private static void flushPendingRotation() {
        if (activeDrag == null) {
            clearPendingRotation();
            return;
        }
        if (Math.abs(pendingYawDegrees) < 1.0E-4D
                && Math.abs(pendingPitchDegrees) < 1.0E-4D
                && Math.abs(pendingRollDegrees) < 1.0E-4D) {
            return;
        }
        int packetsSent = 0;
        while (hasPendingRotation() && packetsSent < MAX_ROTATION_PACKETS_PER_TICK) {
            double yaw = takeRotationPacket(pendingYawDegrees);
            double pitch = takeRotationPacket(pendingPitchDegrees);
            double roll = takeRotationPacket(pendingRollDegrees);
            PacketDistributor.sendToServer(new MagneticGunRotatePayload(yaw, pitch, roll));
            pendingYawDegrees -= yaw;
            pendingPitchDegrees -= pitch;
            pendingRollDegrees -= roll;
            packetsSent++;
        }
        discardNegligibleRotation();
    }

    private static void updatePrecisionMode(Minecraft minecraft) {
        if (activeDrag == null || !activeDrag.creative) {
            return;
        }
        boolean precisionMode = minecraft.options.keyShift.isDown();
        if (precisionMode == activeDrag.precisionMode) {
            return;
        }
        activeDrag.precisionMode = precisionMode;
        PacketDistributor.sendToServer(new MagneticGunPrecisionPayload(precisionMode));
    }

    private static boolean hasPendingRotation() {
        return Math.abs(pendingYawDegrees) >= 1.0E-4D
                || Math.abs(pendingPitchDegrees) >= 1.0E-4D
                || Math.abs(pendingRollDegrees) >= 1.0E-4D;
    }

    private static double takeRotationPacket(double value) {
        return Math.max(-MAX_ROTATION_DEGREES_PER_PACKET,
                Math.min(MAX_ROTATION_DEGREES_PER_PACKET, value));
    }

    private static void discardNegligibleRotation() {
        if (Math.abs(pendingYawDegrees) < 1.0E-4D) {
            pendingYawDegrees = 0.0D;
        }
        if (Math.abs(pendingPitchDegrees) < 1.0E-4D) {
            pendingPitchDegrees = 0.0D;
        }
        if (Math.abs(pendingRollDegrees) < 1.0E-4D) {
            pendingRollDegrees = 0.0D;
        }
    }

    private static void stopDrag() {
        if (activeDrag == null) {
            return;
        }
        PacketDistributor.sendToServer(new MagneticGunStopPayload());
        activeDrag = null;
        clearPendingRotation();
    }

    private static void resetClientState() {
        wasUseDown = false;
        activeDrag = null;
        clearPendingRotation();
    }

    private static void clearPendingRotation() {
        pendingYawDegrees = 0.0D;
        pendingPitchDegrees = 0.0D;
        pendingRollDegrees = 0.0D;
    }

    // Tab also opens the player list, so honor both mappings.
    private static boolean isRotateKeyDown(Minecraft minecraft) {
        return ROTATE_OBJECT.isDown() || minecraft.options.keyPlayerList.isDown();
    }

    public static boolean hasActiveDrag() {
        return activeDrag != null;
    }

    public static void handleStartResult(MagneticGunStartResultPayload payload) {
        if (!payload.accepted()
                && activeDrag != null
                && activeDrag.subLevelId.equals(payload.subLevelId())) {
            activeDrag = null;
            clearPendingRotation();
        }
    }

    public static Vec3 getCurrentGrabWorldPoint(ClientLevel level) {
        if (activeDrag == null) {
            return null;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        SubLevel subLevel = container.getSubLevel(activeDrag.subLevelId);
        return subLevel != null ? subLevel.logicalPose().transformPosition(activeDrag.localGrabPoint) : null;
    }

    public static Vec3 getCurrentTargetWorldPoint(LocalPlayer player) {
        if (activeDrag == null || player == null) {
            return null;
        }
        return player.getEyePosition().add(player.getLookAngle().normalize().scale(activeDrag.distance));
    }

    private static InteractionHand findActiveHand(LocalPlayer player) {
        if (ModItems.isAnyMagneticGun(player.getMainHandItem())) {
            return InteractionHand.MAIN_HAND;
        }
        if (ModItems.isAnyMagneticGun(player.getOffhandItem())) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    private static boolean isHoldingMagneticGun(LocalPlayer player) {
        return ModItems.isAnyMagneticGun(player.getMainHandItem())
                || ModItems.isAnyMagneticGun(player.getOffhandItem());
    }
}
