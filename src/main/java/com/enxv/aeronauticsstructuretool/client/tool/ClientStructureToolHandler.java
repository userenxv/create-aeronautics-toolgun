package com.enxv.aeronauticsstructuretool.client.tool;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.BlueprintListEntry;
import com.enxv.aeronauticsstructuretool.ClientToolState;
import com.enxv.aeronauticsstructuretool.DeleteSubLevelPayload;
import com.enxv.aeronauticsstructuretool.DisconnectSubLevelPayload;
import com.enxv.aeronauticsstructuretool.LoadSubLevelPayload;
import com.enxv.aeronauticsstructuretool.LoadSubLevelChunkPayload;
import com.enxv.aeronauticsstructuretool.CompleteLoadSubLevelPayload;
import com.enxv.aeronauticsstructuretool.ModItems;
import com.enxv.aeronauticsstructuretool.QueryVehicleActionPayload;
import com.enxv.aeronauticsstructuretool.RotationAxisMode;
import com.enxv.aeronauticsstructuretool.SimpleWeldAdjustMode;
import com.enxv.aeronauticsstructuretool.ToggleSubLevelCollisionPayload;
import com.enxv.aeronauticsstructuretool.ToolMode;
import com.enxv.aeronauticsstructuretool.WeldSelectionMode;
import com.enxv.aeronauticsstructuretool.blueprint.storage.ClientBlueprintCatalog;
import com.enxv.aeronauticsstructuretool.blueprint.placement.BlueprintPlacementTargetResolver;
import com.enxv.aeronauticsstructuretool.client.ClientHooks;
import com.enxv.aeronauticsstructuretool.client.render.ClientConstraintVisualTracker;
import com.enxv.aeronauticsstructuretool.client.screen.SaveNameScreen;
import com.enxv.aeronauticsstructuretool.core.FailureMessages;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.UUID;
import java.util.Arrays;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class ClientStructureToolHandler {
    private ClientStructureToolHandler() {
    }

    public static void handleBlockHit(InteractionHand hand, BlockHitResult hit, Level level) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        BlockPos clickedPos = hit.getBlockPos();
        Vec3 beamTarget = resolveWeldSelectionPoint(level, hit);
        ToolMode mode = ClientToolState.getMode();
        if (mode == ToolMode.WELD) {
            handleManualWeld(hand, hit, level, beamTarget);
            return;
        }
        if (mode == ToolMode.SIMPLE_WELD) {
            handleSimpleWeld(hand, hit, level, beamTarget);
            return;
        }
        if (mode == ToolMode.ROTATE) {
            handleRotate(hand, hit, level, beamTarget);
            return;
        }
        if (mode == ToolMode.TRANSLATE) {
            handleTranslate(hand, hit, level, beamTarget);
            return;
        }
        if (mode == ToolMode.DISCONNECT) {
            handleDisconnect(hand, hit, level, beamTarget);
            return;
        }
        if (mode == ToolMode.NO_COLLISION) {
            if (isRestrictedStructureTool(minecraft.player)) {
                minecraft.player.displayClientMessage(Component.translatable("message.create_aeronautics_toolgun.survival_tool_restricted", Component.translatable("screen.create_aeronautics_toolgun.tool.no_collision_mode")), true);
                return;
            }
            handleNoCollision(hand, hit, level, beamTarget);
            return;
        }
        if (mode == ToolMode.GHOST_VEHICLE_TEST) {
            if (isRestrictedStructureTool(minecraft.player)) {
                minecraft.player.displayClientMessage(Component.translatable(
                        "message.create_aeronautics_toolgun.survival_tool_restricted",
                        Component.translatable("screen.create_aeronautics_toolgun.tool.ghost_vehicle_test")
                ), true);
                return;
            }
            handleGhostVehicleTest(hand, hit, level, beamTarget);
            return;
        }
        String selectedFile = ClientToolState.getSelectedFile();
        AeronauticsStructureToolMod.LOGGER.debug(
                "Client tool interaction: mode={}, file='{}', pos={}, face={}",
                mode,
                selectedFile,
                clickedPos,
                hit.getDirection()
        );
        if (mode == ToolMode.SAVE) {
            ClientHooks.showBeam(hand, beamTarget);
            if (Sable.HELPER.getContaining(level, clickedPos) == null) {
                return;
            }
            minecraft.setScreen(new SaveNameScreen(clickedPos));
            return;
        }

        if (mode == ToolMode.LOAD) {
            if (isRestrictedStructureTool(minecraft.player)) {
                minecraft.player.displayClientMessage(Component.translatable("message.create_aeronautics_toolgun.survival_tool_restricted", Component.translatable("screen.create_aeronautics_toolgun.mode.load")), true);
                return;
            }
            BlueprintListEntry entry = ClientToolState.getSelectedEntry();
            if (entry == null) {
                minecraft.player.displayClientMessage(Component.translatable("message.create_aeronautics_toolgun.need_load_file"), true);
                return;
            }
            try {
                byte[] fileContents = ClientBlueprintCatalog.read(entry);
                BlueprintPlacementTargetResolver.Target placementTarget =
                        BlueprintPlacementTargetResolver.resolve(
                                level,
                                hit.getBlockPos(),
                                hit.getDirection(),
                                hit.getLocation()
                        );
                Vector3d placementHit = placementTarget.hit();
                ClientHooks.showBeam(hand, new Vec3(placementHit.x, placementHit.y, placementHit.z));
                UUID transferId = UUID.randomUUID();
                PacketDistributor.sendToServer(new LoadSubLevelPayload(
                        transferId,
                        placementTarget.clickedPos(),
                        placementTarget.face(),
                        placementHit.x,
                        placementHit.y,
                        placementHit.z,
                        ClientToolState.getRotationDegrees(),
                        ClientToolState.getScalePercent(),
                        ClientToolState.getOffsetX(),
                        ClientToolState.getOffsetY(),
                        ClientToolState.getOffsetZ(),
                        ClientToolState.isAutoWeldEnabled(),
                        ClientToolState.getConnectionMode().name(),
                        ClientToolState.getSnapMode().name(),
                        entry.fileName(),
                        fileContents.length,
                        sha256(fileContents)
                ));
                for (int offset = 0; offset < fileContents.length; offset += LoadSubLevelChunkPayload.MAX_CHUNK_BYTES) {
                    int end = Math.min(fileContents.length, offset + LoadSubLevelChunkPayload.MAX_CHUNK_BYTES);
                    PacketDistributor.sendToServer(new LoadSubLevelChunkPayload(
                            transferId,
                            offset,
                            Arrays.copyOfRange(fileContents, offset, end)
                    ));
                }
                PacketDistributor.sendToServer(new CompleteLoadSubLevelPayload(transferId));
            } catch (Exception exception) {
                AeronauticsStructureToolMod.LOGGER.warn("Client load dispatch failed for '{}'", entry.displayName(), exception);
                minecraft.player.displayClientMessage(Component.translatable(
                        "message.create_aeronautics_toolgun.load_failed",
                        FailureMessages.describe(exception, "blueprint load dispatch failed")
                ), true);
            }
            return;
        }

        ClientHooks.showBeam(hand, beamTarget);
        if (Sable.HELPER.getContaining(level, clickedPos) == null) {
            return;
        }
        if (isRestrictedStructureTool(minecraft.player)) {
            minecraft.player.displayClientMessage(Component.translatable("message.create_aeronautics_toolgun.survival_tool_restricted", Component.translatable("screen.create_aeronautics_toolgun.mode.delete")), true);
            return;
        }

        PacketDistributor.sendToServer(new DeleteSubLevelPayload(
                clickedPos,
                ClientToolState.isRangeDeleteEnabled(),
                ClientToolState.getDeleteRange()
        ));
    }

    private static byte[] sha256(byte[] contents) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(contents);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public static void handleMiss(InteractionHand hand, Level level, Vec3 target) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        if (ClientToolState.getMode() == ToolMode.WELD && ManualWeldClientSession.hasFirstSelection()) {
            clearPendingWeld();
            minecraft.player.displayClientMessage(Component.translatable("message.create_aeronautics_toolgun.weld_cancelled"), true);
            return;
        }
        if (ClientToolState.getMode() == ToolMode.SIMPLE_WELD && hasPendingSimpleWeldSelection()) {
            if (isAdjustingSimpleWeld()) {
                cyclePendingSimpleWeldAxis();
            } else {
                clearPendingSimpleWeld();
                minecraft.player.displayClientMessage(Component.translatable("message.create_aeronautics_toolgun.simple_weld_cancelled"), true);
            }
            return;
        }
        if (ClientToolState.getMode() == ToolMode.ROTATE && isAdjustingRotation()) {
            clearPendingRotation();
            minecraft.player.displayClientMessage(Component.translatable("message.create_aeronautics_toolgun.rotate_cancelled"), true);
            return;
        }
        if (ClientToolState.getMode() == ToolMode.TRANSLATE && isAdjustingTranslation()) {
            clearPendingTranslation();
            minecraft.player.displayClientMessage(Component.translatable("message.create_aeronautics_toolgun.translate_cancelled"), true);
            return;
        }

        Vec3 beamTarget = Sable.HELPER.projectOutOfSubLevel(level, target);
        ClientHooks.showBeam(hand, beamTarget);
    }

    private static boolean isRestrictedStructureTool(net.minecraft.client.player.LocalPlayer player) {
        return player != null && (ModItems.isSurvivalStructureTool(player.getMainHandItem()) || ModItems.isSurvivalStructureTool(player.getOffhandItem()));
    }

    public static Vec3 resolveWeldSelectionPoint(Level level, BlockHitResult hit) {
        Vec3 projectedHit = Sable.HELPER.projectOutOfSubLevel(level, hit.getLocation());
        if (ClientToolState.getMode() != ToolMode.WELD && ClientToolState.getMode() != ToolMode.SIMPLE_WELD) {
            return projectedHit;
        }
        WeldSelectionMode selectionMode = ClientToolState.getWeldSelectionMode();
        Minecraft minecraft = Minecraft.getInstance();
        return WeldSelectionGeometry.resolve(level, hit, minecraft.player, selectionMode, projectedHit);
    }

    private static void handleManualWeld(InteractionHand hand, BlockHitResult hit, Level level, Vec3 beamTarget) {
        ManualWeldClientSession.select(hand, hit, level, beamTarget);
    }

    private static void handleDisconnect(InteractionHand hand, BlockHitResult hit, Level level, Vec3 beamTarget) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        if (Sable.HELPER.getContaining(level, hit.getBlockPos()) == null) {
            minecraft.player.displayClientMessage(Component.translatable("message.create_aeronautics_toolgun.disconnect_need_structure"), true);
            return;
        }
        ClientHooks.showBeam(hand, beamTarget);
        SubLevel containing = Sable.HELPER.getContaining(level, hit.getBlockPos());
        if (containing != null) {
            ClientConstraintVisualTracker.removeForSubLevel(containing.getUniqueId());
        }
        PacketDistributor.sendToServer(new DisconnectSubLevelPayload(hit.getBlockPos()));
    }

    private static void handleSimpleWeld(InteractionHand hand, BlockHitResult hit, Level level, Vec3 beamTarget) {
        SimpleWeldClientSession.select(hand, hit, level, beamTarget);
    }

    private static void handleRotate(InteractionHand hand, BlockHitResult hit, Level level, Vec3 beamTarget) {
        TransformClientSession.selectRotation(hand, hit, level, beamTarget);
    }

    private static void handleTranslate(InteractionHand hand, BlockHitResult hit, Level level, Vec3 beamTarget) {
        TransformClientSession.selectTranslation(hand, hit, level, beamTarget);
    }

    private static void handleNoCollision(InteractionHand hand, BlockHitResult hit, Level level, Vec3 beamTarget) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        if (Sable.HELPER.getContaining(level, hit.getBlockPos()) == null) {
            minecraft.player.displayClientMessage(Component.translatable("message.create_aeronautics_toolgun.no_collision_need_structure"), true);
            return;
        }
        ClientHooks.showBeam(hand, beamTarget);
        PacketDistributor.sendToServer(new ToggleSubLevelCollisionPayload(hit.getBlockPos()));
    }

    private static void handleGhostVehicleTest(
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
            minecraft.player.displayClientMessage(Component.translatable(
                    "message.create_aeronautics_toolgun.query_ghost_need_structure"
            ), true);
            return;
        }
        ClientHooks.showBeam(hand, beamTarget);
        PacketDistributor.sendToServer(new QueryVehicleActionPayload(
                containing.getUniqueId(),
                QueryVehicleActionPayload.ACTION_CREATE_GHOST,
                0.0D,
                0.0D,
                0.0D,
                ""
        ));
    }

    public static Vec3 getPendingWeldPoint() {
        return ManualWeldClientSession.firstPoint();
    }

    public static net.minecraft.core.Direction getPendingWeldFace() {
        return ManualWeldClientSession.firstFace();
    }

    public static net.minecraft.core.Direction getPendingWeldSecondFace() {
        return ManualWeldClientSession.secondFace();
    }

    public static BlockPos getPendingWeldBlockPos() {
        return ManualWeldClientSession.firstBlockPos();
    }

    public static BlockPos getPendingWeldSecondBlockPos() {
        return ManualWeldClientSession.secondBlockPos();
    }

    public static Vec3 getPendingAdjustedWeldPoint() {
        return ManualWeldClientSession.adjustedSecondPoint();
    }

    public static boolean isAdjustingWeldDistance() {
        return ManualWeldClientSession.adjustingDistance();
    }

    public static void adjustPendingWeldDistance(int delta) {
        ManualWeldClientSession.adjustDistance(delta);
    }

    public static boolean confirmPendingWeld() {
        return ManualWeldClientSession.confirm();
    }

    public static boolean isAdjustingRotation() {
        return TransformClientSession.rotating();
    }

    public static boolean isAdjustingTranslation() {
        return TransformClientSession.translating();
    }

    public static boolean hasPendingSimpleWeldSelection() {
        return SimpleWeldClientSession.hasSelection();
    }

    public static boolean isAdjustingSimpleWeld() {
        return SimpleWeldClientSession.adjusting();
    }

    public static void togglePendingSimpleWeldAdjustMode() {
        SimpleWeldClientSession.toggleAdjustMode();
    }

    public static void adjustPendingSimpleWeld(int delta) {
        SimpleWeldClientSession.adjust(delta);
    }

    public static boolean confirmPendingSimpleWeld() {
        return SimpleWeldClientSession.confirm();
    }

    public static void adjustPendingRotation(int delta) {
        TransformClientSession.adjustRotation(delta);
    }

    public static void adjustPendingTranslation(int delta) {
        TransformClientSession.adjustTranslation(delta);
    }

    public static boolean confirmPendingRotation() {
        return TransformClientSession.confirmRotation();
    }

    public static boolean confirmPendingTranslation() {
        return TransformClientSession.confirmTranslation();
    }

    public static void clearPendingRotation() {
        TransformClientSession.cancelRotation();
    }

    public static void clearPendingTranslation() {
        TransformClientSession.cancelTranslation();
    }

    public static void clearPendingWeld() {
        ManualWeldClientSession.clear();
    }

    public static Vec3 getPendingRotatePivotPoint() {
        return TransformClientSession.rotationPivotWorld();
    }

    public static RotationAxisMode getPendingRotateAxis() {
        return TransformClientSession.rotationAxis();
    }

    public static RotationAxisMode getPendingTranslateAxis() {
        return TransformClientSession.translationAxis();
    }

    public static UUID getPendingRotateSubLevelId() {
        return TransformClientSession.rotationSubLevelId();
    }

    public static UUID getPendingTranslateSubLevelId() {
        return TransformClientSession.translationSubLevelId();
    }

    public static Quaterniond getPendingRotateLocalRotation() {
        return TransformClientSession.localRotation();
    }

    public static Vec3 getPendingRotatePivotLocalPoint() {
        return TransformClientSession.rotationPivotLocalPoint();
    }

    public static Vec3 getPendingTranslatePivotPoint() {
        return TransformClientSession.translationPivotWorld();
    }

    public static Vec3 getPendingTranslatePivotLocalPoint() {
        return TransformClientSession.translationPivotLocalPoint();
    }

    public static Vec3 getPendingTranslateLocalOffset() {
        return TransformClientSession.localTranslation();
    }

    public static void clearPendingSimpleWeld() {
        SimpleWeldClientSession.clear();
    }

    public static RotationAxisMode getPendingSimpleWeldAxis() {
        return SimpleWeldClientSession.axis();
    }

    public static Vector3d getPendingSimpleWeldAxisVector() {
        return SimpleWeldClientSession.axisVector();
    }

    public static void cyclePendingSimpleWeldAxis() {
        SimpleWeldClientSession.cycleAxis();
    }

    public static SimpleWeldAdjustMode getPendingSimpleWeldAdjustMode() {
        return SimpleWeldClientSession.adjustMode();
    }

    public static UUID getPendingSimpleWeldChildSubLevelId() {
        return SimpleWeldClientSession.childSubLevelId();
    }

    public static Vec3 getPendingSimpleWeldChildLocalPoint() {
        return SimpleWeldClientSession.childLocalPoint();
    }

    public static Vec3 getPendingSimpleWeldChildPoint() {
        return SimpleWeldClientSession.childWorldPoint();
    }

    public static Vec3 getPendingSimpleWeldParentPoint() {
        return SimpleWeldClientSession.parentWorldPoint();
    }

    public static Quaterniond getPendingSimpleWeldRelativeRotation() {
        return SimpleWeldClientSession.relativeRotation();
    }

    public static Vec3 getPendingSimpleWeldParentLocalOffset() {
        return SimpleWeldClientSession.parentLocalOffset();
    }

    public static SimpleWeldPreview getPendingSimpleWeldPreview(Level level) {
        SimpleWeldClientSession.Preview preview = SimpleWeldClientSession.preview(level);
        if (preview == null) {
            return null;
        }
        return new SimpleWeldPreview(
                preview.childSubLevelId(),
                preview.previewPosition(),
                preview.previewOrientation(),
                preview.parentOrientation(),
                preview.axisHalfLength(),
                preview.parentPointWorld(),
                preview.childPointWorld()
        );
    }

    public record SimpleWeldPreview(
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
