package com.enxv.aeronauticsstructuretool.client.render;

import com.enxv.aeronauticsstructuretool.ClientToolState;
import com.enxv.aeronauticsstructuretool.client.tool.MagneticGunClientController;
import com.enxv.aeronauticsstructuretool.PreviewBlueprintData;
import com.enxv.aeronauticsstructuretool.StructureToolItem;
import com.enxv.aeronauticsstructuretool.blueprint.placement.PlacementTargetMath;
import com.enxv.aeronauticsstructuretool.ToolMode;
import com.enxv.aeronauticsstructuretool.blueprint.placement.BlueprintVerticalPlacement;
import com.enxv.aeronauticsstructuretool.blueprint.placement.BlueprintPlacementTargetResolver;
import com.enxv.aeronauticsstructuretool.client.tool.ClientHeldToolState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import static com.enxv.aeronauticsstructuretool.client.render.ClientWorldRenderPrimitives.*;

public final class ClientPlacementPreviewRenderer {
    private ClientPlacementPreviewRenderer() {
    }

    public static void renderPlacementPreview(RenderLevelStageEvent event, boolean finalMainTarget) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            ClientPlacementPreviewMesh.clearCache();
            return;
        }
        if (!ClientToolState.isPreviewEnabled()
                || ClientToolState.getMode() != ToolMode.LOAD
                || !ClientHeldToolState.holdsStructureTool(minecraft.player)) {
            ClientPlacementPreviewMesh.clearCache();
            return;
        }

        PreviewBlueprintData preview = ClientToolState.getOrLoadPreview();
        PreviewPlacement placement = computePreviewPlacement(minecraft, preview);
        if (placement == null) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(PREVIEW_RENDER_TYPE);
        VertexConsumer fillConsumer = bufferSource.getBuffer(RenderType.debugQuads());

        if (preview == null || !preview.hasRenderableSamples()) {
            renderPreviewFallback(poseStack, consumer, fillConsumer, placement.target, placement.rootOrientation);
            poseStack.popPose();
            bufferSource.endBatch(PREVIEW_RENDER_TYPE);
            bufferSource.endBatch(RenderType.debugQuads());
            return;
        }

        Vector3d liftedTarget = new Vector3d(placement.target);
        Quaterniond previewRootOrientation = new Quaterniond(preview.rootOrientation()).mul(placement.rootOrientation);
        Vector3d previewRootRelativePosition = preview.rootRelativePosition();
        if (!ClientPlacementPreviewMesh.render(
                poseStack,
                event.getModelViewMatrix(),
                event.getProjectionMatrix(),
                preview,
                liftedTarget,
                previewRootOrientation,
                placement.scaleFactor,
                finalMainTarget
        )) {
            renderPreviewFallback(poseStack, consumer, fillConsumer, liftedTarget, placement.rootOrientation);
            renderUncachedPreview(
                    poseStack,
                    consumer,
                    fillConsumer,
                    preview,
                    liftedTarget,
                    previewRootOrientation,
                    previewRootRelativePosition,
                    placement.scaleFactor
            );
        }

        poseStack.popPose();
        bufferSource.endBatch(PREVIEW_RENDER_TYPE);
        bufferSource.endBatch(RenderType.debugQuads());
    }

    private static void renderUncachedPreview(
            PoseStack poseStack,
            VertexConsumer consumer,
            VertexConsumer fillConsumer,
            PreviewBlueprintData preview,
            Vector3d liftedTarget,
            Quaterniond previewRootOrientation,
            Vector3d previewRootRelativePosition,
            double scaleFactor
    ) {
        for (PreviewBlueprintData.PreviewSubLevel subLevel : preview.subLevels()) {
            Vector3d subAnchor = new Vector3d(subLevel.relativePosition())
                    .sub(previewRootRelativePosition)
                    .mul(scaleFactor);
            previewRootOrientation.transform(subAnchor);
            subAnchor.add(liftedTarget);
            Quaterniond subOrientation = new Quaterniond(previewRootOrientation)
                    .mul(subLevel.relativeOrientation());
            Vector3d subRotationOffset = new Vector3d(subLevel.localPlotAnchor());
            for (Vector3d sample : subLevel.sampleBlocks()) {
                Vector3d localOffset = new Vector3d(sample)
                        .sub(subRotationOffset)
                        .mul(scaleFactor);
                subOrientation.transform(localOffset);
                Vector3d worldSample = new Vector3d(subAnchor).add(localOffset);
                renderPreviewCube(
                        poseStack,
                        consumer,
                        fillConsumer,
                        worldSample,
                        subOrientation,
                        scaleFactor,
                        0.95F,
                        0.78F,
                        0.45F,
                        0.9F
                );
                renderPreviewPoint(
                        poseStack,
                        fillConsumer,
                        fillConsumer,
                        worldSample,
                        1.0F,
                        0.9F,
                        0.5F,
                        0.95F
                );
            }
        }
    }

    public static void renderMagneticMarkers(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || !ClientToolState.isMagneticMarkersEnabled() || !MagneticGunClientController.hasActiveDrag()) {
            return;
        }

        Vec3 grab = MagneticGunClientController.getCurrentGrabWorldPoint(minecraft.level);
        Vec3 target = MagneticGunClientController.getCurrentTargetWorldPoint(minecraft.player);
        if (grab == null && target == null) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(PREVIEW_RENDER_TYPE);
        VertexConsumer fillConsumer = bufferSource.getBuffer(RenderType.debugQuads());
        if (grab != null) {
            renderPreviewPoint(poseStack, consumer, fillConsumer, new Vector3d(grab.x, grab.y, grab.z), 0.45F, 0.90F, 1.0F, 0.9F);
        }
        if (target != null) {
            renderPreviewPoint(poseStack, consumer, fillConsumer, new Vector3d(target.x, target.y, target.z), 1.0F, 0.55F, 0.30F, 0.9F);
        }
        if (grab != null && target != null) {
            renderPreviewLine(
                    poseStack,
                    fillConsumer,
                    new Vector3d(grab.x, grab.y, grab.z),
                    new Vector3d(target.x, target.y, target.z),
                    0.72F,
                    0.96F,
                    1.0F,
                    0.82F
            );
        }
        poseStack.popPose();
        bufferSource.endBatch(PREVIEW_RENDER_TYPE);
        bufferSource.endBatch(RenderType.debugQuads());
    }

    private static PreviewPlacement computePreviewPlacement(Minecraft minecraft, PreviewBlueprintData preview) {
        HitResult hit = minecraft.player.pick(StructureToolItem.MAX_USE_DISTANCE, 0.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlueprintPlacementTargetResolver.Target placementTarget =
                BlueprintPlacementTargetResolver.resolve(
                        minecraft.level,
                        blockHit.getBlockPos(),
                        blockHit.getDirection(),
                        blockHit.getLocation()
                );
        Direction face = placementTarget.face();
        Vector3d worldHit = placementTarget.hit();
        Vector3d target = PlacementTargetMath.computePlacementTarget(
                placementTarget.clickedPos(),
                face,
                worldHit.x,
                worldHit.y,
                worldHit.z,
                ClientToolState.getSnapMode(),
                ClientToolState.getOffsetX(),
                ClientToolState.getOffsetY(),
                ClientToolState.getOffsetZ()
        );
        Quaterniond rootOrientation = PlacementTargetMath.computeExtraRotation(
                face,
                ClientToolState.getRotationDegrees()
        );
        double scaleFactor = Math.max(0.25D, ClientToolState.getScalePercent() / 100.0D);
        if (preview != null) {
            target = BlueprintVerticalPlacement
                    .keepAboveSurface(face, worldHit.y)
                    .apply(target, preview.minimumRelativeBlockCenterY(rootOrientation, scaleFactor));
        }
        return new PreviewPlacement(target, rootOrientation, face, scaleFactor);
    }

    private record PreviewPlacement(Vector3d target, Quaterniond rootOrientation, Direction face, double scaleFactor) {
    }
}
