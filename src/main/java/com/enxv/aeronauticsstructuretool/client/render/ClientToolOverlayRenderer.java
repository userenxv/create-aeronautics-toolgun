package com.enxv.aeronauticsstructuretool.client.render;

import com.enxv.aeronauticsstructuretool.BlueprintListEntry;
import com.enxv.aeronauticsstructuretool.client.tool.ClientStructureToolHandler;
import com.enxv.aeronauticsstructuretool.client.tool.ClientSaveSelectionPreview;
import com.enxv.aeronauticsstructuretool.ClientToolState;
import com.enxv.aeronauticsstructuretool.ConnectionMode;
import com.enxv.aeronauticsstructuretool.SimpleWeldAdjustMode;
import com.enxv.aeronauticsstructuretool.ToolMode;
import com.enxv.aeronauticsstructuretool.client.tool.ClientHeldToolState;
import com.enxv.aeronauticsstructuretool.toolgun.weld.WeldAxisGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.UUID;
import java.util.Locale;

import static com.enxv.aeronauticsstructuretool.client.render.ClientWorldRenderPrimitives.*;

public final class ClientToolOverlayRenderer {
    private ClientToolOverlayRenderer() {
    }

    public static void registerShaders(RegisterShadersEvent event) {
        SimpleWeldGhostRenderer.registerShaders(event);
    }

    public static void clearCachedModels() {
        SimpleWeldGhostRenderer.clear();
    }

    public static void renderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            boolean deferEngineeringGhosts = SimpleWeldGhostRenderer.shouldRenderAfterLevel();
            if (!deferEngineeringGhosts) {
                ClientPlacementPreviewRenderer.renderPlacementPreview(event, false);
            }
            if (!deferEngineeringGhosts) {
                renderSaveSelectionPreview(event);
            }
            ClientPlacementPreviewRenderer.renderMagneticMarkers(event);
            renderHeldBearingAxisPreview(event);
            renderWeldMarkers(event);
            renderSimpleWeldMarkers(event, !deferEngineeringGhosts);
            renderTranslateMarkers(event);
            renderRotateMarkers(event);
            renderTrackedBearingVisuals(event);
            ClientBeamRenderer.render(event);
            return;
        }
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL
                && SimpleWeldGhostRenderer.shouldRenderAfterLevel()) {
            ClientPlacementPreviewRenderer.renderPlacementPreview(event, true);
            renderSaveSelectionPreview(event);
            renderDeferredSimpleWeldGhost(event);
        }
    }

    public static void renderGui(RenderGuiEvent.Post event) {
        renderModeHud(event);
    }

    private static void renderModeHud(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || !ClientToolState.isHudEnabled() || !ClientHeldToolState.holdsStructureTool(minecraft.player)) {
            return;
        }
        GuiGraphics guiGraphics = event.getGuiGraphics();
        Component modeLine = Component.translatable("screen.create_aeronautics_toolgun.hud.mode", ClientToolState.getMode().title())
                .copy().withStyle(ChatFormatting.BOLD);
        Component detailLine = switch (ClientToolState.getMode()) {
            case SAVE -> Component.translatable(
                    "screen.create_aeronautics_toolgun.hud.save",
                    String.format(Locale.ROOT, "%.1f", ClientToolState.getConnectedSublevelProximity())
            );
            case LOAD -> {
                BlueprintListEntry selectedEntry = ClientToolState.getSelectedEntry();
                String selected = selectedEntry != null ? selectedEntry.displayName() : "-";
                yield Component.translatable(
                        "screen.create_aeronautics_toolgun.hud.load",
                        selected,
                        ClientToolState.getRotationDegrees(),
                        ClientToolState.getOffsetX(),
                        ClientToolState.getOffsetY(),
                        ClientToolState.getOffsetZ(),
                        ClientToolState.getSnapMode().label()
                );
            }
            case DELETE -> Component.translatable("screen.create_aeronautics_toolgun.hud.delete");
            case NO_COLLISION -> Component.translatable("screen.create_aeronautics_toolgun.hud.no_collision");
            case GHOST_VEHICLE_TEST -> Component.translatable("screen.create_aeronautics_toolgun.hud.ghost_vehicle_test");
            case WELD -> ClientStructureToolHandler.getPendingWeldPoint() == null
                    ? Component.translatable("screen.create_aeronautics_toolgun.hud.weld_idle", weldModeLabel())
                    : ClientStructureToolHandler.isAdjustingWeldDistance()
                    ? Component.translatable("screen.create_aeronautics_toolgun.hud.weld_adjust", weldModeLabel())
                    : Component.translatable("screen.create_aeronautics_toolgun.hud.weld_pending", weldModeLabel());
            case SIMPLE_WELD -> ClientStructureToolHandler.isAdjustingSimpleWeld()
                    ? Component.translatable(
                    "screen.create_aeronautics_toolgun.hud.simple_weld_adjust",
                    ClientStructureToolHandler.getPendingSimpleWeldAdjustMode().title(),
                    ClientStructureToolHandler.getPendingSimpleWeldAxis().title()
            )
                    : ClientStructureToolHandler.hasPendingSimpleWeldSelection()
                    ? Component.translatable("screen.create_aeronautics_toolgun.hud.simple_weld_pending")
                    : Component.translatable("screen.create_aeronautics_toolgun.hud.simple_weld_idle");
            case TRANSLATE -> ClientStructureToolHandler.isAdjustingTranslation()
                    ? Component.translatable("screen.create_aeronautics_toolgun.hud.translate_adjust", ClientStructureToolHandler.getPendingTranslateAxis().title())
                    : Component.translatable("screen.create_aeronautics_toolgun.hud.translate_idle");
            case ROTATE -> ClientStructureToolHandler.isAdjustingRotation()
                    ? Component.translatable("screen.create_aeronautics_toolgun.hud.rotate_adjust", ClientStructureToolHandler.getPendingRotateAxis().title())
                    : Component.translatable("screen.create_aeronautics_toolgun.hud.rotate_idle");
            case DISCONNECT -> Component.translatable("screen.create_aeronautics_toolgun.hud.disconnect");
        };
        renderHudLine(guiGraphics, minecraft, modeLine, 8.0F, 8.0F, 1.35F, 0xFFFFEE5A, 0xFF6B5400);
        renderHudLine(guiGraphics, minecraft, detailLine, 8.0F, 30.0F, 1.12F, 0xFFFFD84A, 0xFF6A4D00);
    }

    private static void renderWeldMarkers(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 pendingWeld = ClientStructureToolHandler.getPendingWeldPoint();
        if (ClientToolState.getMode() != ToolMode.WELD) {
            return;
        }
        if (minecraft.player == null || minecraft.level == null || pendingWeld == null) {
            return;
        }
        Vec3 currentTarget = ClientStructureToolHandler.isAdjustingWeldDistance()
                ? ClientStructureToolHandler.getPendingAdjustedWeldPoint()
                : minecraft.hitResult instanceof BlockHitResult blockHit && minecraft.hitResult.getType() == HitResult.Type.BLOCK
                ? ClientStructureToolHandler.resolveWeldSelectionPoint(minecraft.level, blockHit)
                : null;
        Direction currentFace = minecraft.hitResult instanceof BlockHitResult blockHit && minecraft.hitResult.getType() == HitResult.Type.BLOCK
                ? blockHit.getDirection()
                : null;

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(PREVIEW_RENDER_TYPE);
        VertexConsumer fillConsumer = bufferSource.getBuffer(RenderType.debugQuads());
        if (currentTarget != null) {
            Vector3d first = new Vector3d(pendingWeld.x, pendingWeld.y, pendingWeld.z);
            Vector3d second = new Vector3d(currentTarget.x, currentTarget.y, currentTarget.z);
            renderPreviewPoint(poseStack, consumer, fillConsumer, first, 1.0F, 0.25F, 0.25F, 1.0F);
            renderPreviewPoint(poseStack, consumer, fillConsumer, second, 1.0F, 0.55F, 0.55F, 0.9F);
            renderPreviewLine(poseStack, fillConsumer, first, second, 1.0F, 0.35F, 0.35F, 0.92F);
        }
        if (ClientToolState.isBearingAxisVisualsEnabled() && ClientToolState.getConnectionMode() == ConnectionMode.BEARING) {
            SubLevel firstSubLevel = ClientStructureToolHandler.getPendingWeldBlockPos() != null
                    ? Sable.HELPER.getContaining(minecraft.level, ClientStructureToolHandler.getPendingWeldBlockPos())
                    : null;
            SubLevel secondSubLevel = ClientStructureToolHandler.isAdjustingWeldDistance() && ClientStructureToolHandler.getPendingWeldSecondBlockPos() != null
                    ? Sable.HELPER.getContaining(minecraft.level, ClientStructureToolHandler.getPendingWeldSecondBlockPos())
                    : minecraft.hitResult instanceof BlockHitResult blockHit ? Sable.HELPER.getContaining(minecraft.level, blockHit.getBlockPos()) : null;
            Vector3d axis = firstSubLevel != null
                    ? ClientToolState.getBearingAxisMode().resolveWorldAxis(
                    firstSubLevel,
                    ClientStructureToolHandler.getPendingWeldFace(),
                    secondSubLevel != null ? secondSubLevel : firstSubLevel,
                    ClientStructureToolHandler.isAdjustingWeldDistance() ? ClientStructureToolHandler.getPendingWeldSecondFace() : currentFace
            ).normalize(1.8D)
                    : new Vector3d(0.0D, 1.0D, 0.0D).normalize(1.8D);
            Vector3d center = currentTarget != null
                    ? new Vector3d(
                    (pendingWeld.x + currentTarget.x) * 0.5D,
                    (pendingWeld.y + currentTarget.y) * 0.5D,
                    (pendingWeld.z + currentTarget.z) * 0.5D
            )
                    : new Vector3d(pendingWeld.x, pendingWeld.y, pendingWeld.z);
            renderPreviewLine(poseStack, fillConsumer, new Vector3d(center).sub(axis), new Vector3d(center).add(axis), 1.0F, 0.85F, 0.28F, 1.0F);
        }
        poseStack.popPose();
        bufferSource.endBatch(PREVIEW_RENDER_TYPE);
        bufferSource.endBatch(RenderType.debugQuads());
    }

    private static void renderSaveSelectionPreview(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || minecraft.level == null
                || !ClientHeldToolState.holdsStructureTool(minecraft.player)
                || ClientToolState.getMode() != ToolMode.SAVE) {
            return;
        }
        ClientSaveSelectionPreview.Selection selection = ClientSaveSelectionPreview.resolve(minecraft);
        if (selection == null) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer rangeConsumer = bufferSource.getBuffer(RenderType.debugQuads());
        Vector3d[] corners = createWorldBoundsCorners(selection.rangeBounds());
        renderBoundsEdges(poseStack, rangeConsumer, corners, 1.0F, 0.78F, 0.16F, 0.96F);
        poseStack.popPose();
        bufferSource.endBatch(RenderType.debugQuads());
    }

    private static Vector3d[] createWorldBoundsCorners(dev.ryanhcode.sable.companion.math.BoundingBox3d bounds) {
        return new Vector3d[]{
                new Vector3d(bounds.minX(), bounds.minY(), bounds.minZ()),
                new Vector3d(bounds.maxX(), bounds.minY(), bounds.minZ()),
                new Vector3d(bounds.maxX(), bounds.minY(), bounds.maxZ()),
                new Vector3d(bounds.minX(), bounds.minY(), bounds.maxZ()),
                new Vector3d(bounds.minX(), bounds.maxY(), bounds.minZ()),
                new Vector3d(bounds.maxX(), bounds.maxY(), bounds.minZ()),
                new Vector3d(bounds.maxX(), bounds.maxY(), bounds.maxZ()),
                new Vector3d(bounds.minX(), bounds.maxY(), bounds.maxZ())
        };
    }

    private static void renderRotateMarkers(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (ClientToolState.getMode() != ToolMode.ROTATE || minecraft.player == null || minecraft.level == null || !ClientStructureToolHandler.isAdjustingRotation()) {
            return;
        }
        Vec3 pivot = ClientStructureToolHandler.getPendingRotatePivotPoint();
        UUID subLevelId = ClientStructureToolHandler.getPendingRotateSubLevelId();
        if (pivot == null || subLevelId == null) {
            return;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(minecraft.level);
        SubLevel subLevel = container.getSubLevel(subLevelId);
        if (subLevel == null) {
            return;
        }
        Quaterniond previewRotation = ClientStructureToolHandler.getPendingRotateLocalRotation();
        Vector3d axis = ClientStructureToolHandler.getPendingRotateAxis().localAxis();
        Quaterniond previewOrientation = new Quaterniond(subLevel.logicalPose().orientation()).mul(previewRotation).normalize();
        previewOrientation.transform(axis);
        if (axis.lengthSquared() <= 1.0E-6D) {
            return;
        }
        axis.normalize(2.0D);
        Vector3d center = new Vector3d(pivot.x, pivot.y, pivot.z);

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(PREVIEW_RENDER_TYPE);
        VertexConsumer fillConsumer = bufferSource.getBuffer(RenderType.debugQuads());
        renderRotatedSubLevelBounds(poseStack, fillConsumer, minecraft, subLevel, ClientStructureToolHandler.getPendingRotatePivotLocalPoint(), previewRotation);
        renderPreviewPoint(poseStack, consumer, fillConsumer, center, 0.45F, 1.0F, 0.35F, 0.95F);
        renderPreviewLine(poseStack, fillConsumer, new Vector3d(center).sub(axis), new Vector3d(center).add(axis), 0.45F, 1.0F, 0.35F, 0.92F);
        poseStack.popPose();
        bufferSource.endBatch(PREVIEW_RENDER_TYPE);
        bufferSource.endBatch(RenderType.debugQuads());
    }

    private static void renderSimpleWeldMarkers(RenderLevelStageEvent event, boolean renderGhost) {
        Minecraft minecraft = Minecraft.getInstance();
        if (ClientToolState.getMode() != ToolMode.SIMPLE_WELD || minecraft.player == null || minecraft.level == null) {
            SimpleWeldGhostRenderer.clearSimpleWeld();
            return;
        }
        Vec3 firstPoint = ClientStructureToolHandler.getPendingSimpleWeldChildPoint();
        if (firstPoint == null) {
            SimpleWeldGhostRenderer.clearSimpleWeld();
            return;
        }
        ClientStructureToolHandler.SimpleWeldPreview preview = ClientStructureToolHandler.getPendingSimpleWeldPreview(minecraft.level);
        if (preview == null) {
            SimpleWeldGhostRenderer.clearSimpleWeld();
        }
        Vec3 currentTarget = null;
        if (preview != null) {
            currentTarget = ClientStructureToolHandler.getPendingSimpleWeldParentPoint();
        } else if (minecraft.hitResult instanceof BlockHitResult blockHit && minecraft.hitResult.getType() == HitResult.Type.BLOCK) {
            currentTarget = ClientStructureToolHandler.resolveWeldSelectionPoint(minecraft.level, blockHit);
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(PREVIEW_RENDER_TYPE);
        Vector3d first = new Vector3d(firstPoint.x, firstPoint.y, firstPoint.z);
        renderSimpleWeldPoint(poseStack, consumer, first, 1.0F, 0.25F, 0.25F, 1.0F);

        if (currentTarget != null) {
            Vector3d second = new Vector3d(currentTarget.x, currentTarget.y, currentTarget.z);
            renderSimpleWeldPoint(poseStack, consumer, second, 1.0F, 0.55F, 0.55F, 0.9F);
            renderSimpleWeldLine(poseStack, consumer, first, second, 1.0F, 0.35F, 0.35F, 0.92F);
        }

        if (preview != null) {
            SubLevelContainer container = SubLevelContainer.getContainer(minecraft.level);
            SubLevel child = container.getSubLevel(preview.childSubLevelId());
            if (child != null) {
                if (renderGhost) {
                    boolean rendered = SimpleWeldGhostRenderer.render(
                            poseStack,
                            event.getModelViewMatrix(),
                            event.getProjectionMatrix(),
                            minecraft,
                            child,
                            preview.previewPosition(),
                            preview.previewOrientation(),
                            resolveSimpleWeldScanAxisLocal(preview),
                            false
                    );
                    if (!rendered) {
                        renderSimpleWeldPreviewBounds(poseStack, consumer, minecraft, child, preview.previewPosition(), preview.previewOrientation());
                    }
                }
            } else {
                SimpleWeldGhostRenderer.clearSimpleWeld();
            }
            Vector3d axis = ClientStructureToolHandler.getPendingSimpleWeldAxisVector();
            preview.parentOrientation().transform(axis);
            if (axis.lengthSquared() > 1.0E-6D) {
                Vector3d center = new Vector3d(preview.childPointWorld().x, preview.childPointWorld().y, preview.childPointWorld().z);
                if (ClientStructureToolHandler.getPendingSimpleWeldAdjustMode() == SimpleWeldAdjustMode.ROTATE) {
                    renderSimpleWeldRotationRing(
                            poseStack,
                            consumer,
                            center,
                            axis.normalize(),
                            0.5D,
                            0.45F,
                            1.0F,
                            0.35F,
                            0.92F
                    );
                } else {
                    axis.normalize(preview.axisHalfLength());
                    renderSimpleWeldLine(
                            poseStack,
                            consumer,
                            new Vector3d(center).sub(axis),
                            new Vector3d(center).add(axis),
                            0.45F,
                            1.0F,
                            0.35F,
                            0.92F
                    );
                }
            }
        }
        poseStack.popPose();
        bufferSource.endBatch(PREVIEW_RENDER_TYPE);
    }

    private static void renderDeferredSimpleWeldGhost(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (ClientToolState.getMode() != ToolMode.SIMPLE_WELD || minecraft.player == null || minecraft.level == null
                || ClientStructureToolHandler.getPendingSimpleWeldChildPoint() == null) {
            SimpleWeldGhostRenderer.clearSimpleWeld();
            return;
        }
        ClientStructureToolHandler.SimpleWeldPreview preview = ClientStructureToolHandler.getPendingSimpleWeldPreview(minecraft.level);
        if (preview == null) {
            SimpleWeldGhostRenderer.clearSimpleWeld();
            return;
        }
        SubLevel child = SubLevelContainer.getContainer(minecraft.level).getSubLevel(preview.childSubLevelId());
        if (child == null) {
            SimpleWeldGhostRenderer.clearSimpleWeld();
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        try {
            SimpleWeldGhostRenderer.render(
                    poseStack,
                    event.getModelViewMatrix(),
                    event.getProjectionMatrix(),
                    minecraft,
                    child,
                    preview.previewPosition(),
                    preview.previewOrientation(),
                    resolveSimpleWeldScanAxisLocal(preview),
                    true
            );
        } finally {
            poseStack.popPose();
        }
    }

    private static Vector3d resolveSimpleWeldScanAxisLocal(ClientStructureToolHandler.SimpleWeldPreview preview) {
        Vector3d scanAxisLocal = ClientStructureToolHandler.getPendingSimpleWeldAxisVector();
        preview.parentOrientation().transform(scanAxisLocal);
        preview.previewOrientation().transformInverse(scanAxisLocal);
        return scanAxisLocal;
    }

    private static void renderSimpleWeldPreviewBounds(PoseStack poseStack, VertexConsumer consumer, Minecraft minecraft, SubLevel subLevel, Vector3d previewPosition, Quaterniond previewOrientation) {
        BoundingBox3ic localBounds = subLevel.getPlot() != null ? subLevel.getPlot().getBoundingBox() : null;
        if (localBounds == null) {
            return;
        }
        Vector3d[] corners = createLocalBoundsCorners(localBounds);
        for (int i = 0; i < corners.length; i++) {
            Vector3d physical = transformSubLevelPreviewPoint(subLevel, previewPosition, previewOrientation, corners[i]);
            corners[i] = projectWorldPoint(minecraft, physical);
        }
        renderSimpleWeldBoundsEdges(poseStack, consumer, corners, 1.0F, 0.90F, 0.28F, 0.92F);
    }

    private static void renderSimpleWeldBoundsEdges(PoseStack poseStack, VertexConsumer consumer, Vector3d[] corners, float red, float green, float blue, float alpha) {
        renderSimpleWeldLine(poseStack, consumer, corners[0], corners[1], red, green, blue, alpha);
        renderSimpleWeldLine(poseStack, consumer, corners[1], corners[2], red, green, blue, alpha);
        renderSimpleWeldLine(poseStack, consumer, corners[2], corners[3], red, green, blue, alpha);
        renderSimpleWeldLine(poseStack, consumer, corners[3], corners[0], red, green, blue, alpha);
        renderSimpleWeldLine(poseStack, consumer, corners[4], corners[5], red, green, blue, alpha);
        renderSimpleWeldLine(poseStack, consumer, corners[5], corners[6], red, green, blue, alpha);
        renderSimpleWeldLine(poseStack, consumer, corners[6], corners[7], red, green, blue, alpha);
        renderSimpleWeldLine(poseStack, consumer, corners[7], corners[4], red, green, blue, alpha);
        renderSimpleWeldLine(poseStack, consumer, corners[0], corners[4], red, green, blue, alpha);
        renderSimpleWeldLine(poseStack, consumer, corners[1], corners[5], red, green, blue, alpha);
        renderSimpleWeldLine(poseStack, consumer, corners[2], corners[6], red, green, blue, alpha);
        renderSimpleWeldLine(poseStack, consumer, corners[3], corners[7], red, green, blue, alpha);
    }

    private static void renderSimpleWeldPoint(PoseStack poseStack, VertexConsumer consumer, Vector3d center, float red, float green, float blue, float alpha) {
        double arm = 0.0625D;
        renderSimpleWeldLine(poseStack, consumer, new Vector3d(center.x - arm, center.y, center.z), new Vector3d(center.x + arm, center.y, center.z), red, green, blue, alpha);
        renderSimpleWeldLine(poseStack, consumer, new Vector3d(center.x, center.y - arm, center.z), new Vector3d(center.x, center.y + arm, center.z), red, green, blue, alpha);
        renderSimpleWeldLine(poseStack, consumer, new Vector3d(center.x, center.y, center.z - arm), new Vector3d(center.x, center.y, center.z + arm), red, green, blue, alpha);
    }

    private static void renderSimpleWeldLine(PoseStack poseStack, VertexConsumer consumer, Vector3d start, Vector3d end, float red, float green, float blue, float alpha) {
        Vector3d normal = new Vector3d(end).sub(start);
        if (normal.lengthSquared() <= 1.0E-8D) {
            return;
        }
        normal.normalize();
        Matrix4f matrix = poseStack.last().pose();
        int r = (int) (red * 255.0F);
        int g = (int) (green * 255.0F);
        int b = (int) (blue * 255.0F);
        int a = (int) (alpha * 255.0F);
        consumer.addVertex(matrix, (float) start.x, (float) start.y, (float) start.z)
                .setColor(r, g, b, a)
                .setNormal((float) normal.x, (float) normal.y, (float) normal.z);
        consumer.addVertex(matrix, (float) end.x, (float) end.y, (float) end.z)
                .setColor(r, g, b, a)
                .setNormal((float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static void renderSimpleWeldRotationRing(PoseStack poseStack, VertexConsumer consumer, Vector3d center, Vector3d axis, double radius, float red, float green, float blue, float alpha) {
        if (radius <= 1.0E-6D || axis.lengthSquared() <= 1.0E-6D) {
            return;
        }
        Vector3d normal = new Vector3d(axis).normalize();
        Vector3d tangent = Math.abs(normal.y) < 0.999D
                ? new Vector3d(0.0D, 1.0D, 0.0D)
                : new Vector3d(1.0D, 0.0D, 0.0D);
        tangent = tangent.sub(new Vector3d(normal).mul(normal.dot(tangent))).normalize();
        Vector3d bitangent = new Vector3d(normal).cross(tangent).normalize();
        int segments = 48;
        Vector3d previous = null;
        for (int i = 0; i <= segments; i++) {
            double angle = (Math.PI * 2.0D * i) / segments;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            Vector3d point = new Vector3d(center)
                    .add(new Vector3d(tangent).mul(cos * radius))
                    .add(new Vector3d(bitangent).mul(sin * radius));
            if (previous != null) {
                renderSimpleWeldLine(poseStack, consumer, previous, point, red, green, blue, alpha);
            }
            previous = point;
        }
        Vector3d marker = new Vector3d(center).add(new Vector3d(tangent).mul(radius));
        renderSimpleWeldLine(poseStack, consumer, center, marker, red, green, blue, Math.min(1.0F, alpha + 0.05F));
    }

    private static void renderTranslateMarkers(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (ClientToolState.getMode() != ToolMode.TRANSLATE || minecraft.player == null || minecraft.level == null || !ClientStructureToolHandler.isAdjustingTranslation()) {
            return;
        }
        Vec3 origin = resolveCurrentTranslateOrigin(minecraft);
        Vec3 target = ClientStructureToolHandler.getPendingTranslatePivotPoint();
        UUID subLevelId = ClientStructureToolHandler.getPendingTranslateSubLevelId();
        if (origin == null || target == null || subLevelId == null) {
            return;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(minecraft.level);
        SubLevel subLevel = container.getSubLevel(subLevelId);
        if (subLevel == null) {
            return;
        }

        Vector3d axis = ClientStructureToolHandler.getPendingTranslateAxis().localAxis();
        subLevel.logicalPose().orientation().transform(axis);
        if (axis.lengthSquared() <= 1.0E-6D) {
            return;
        }
        axis.normalize(1.8D);

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(PREVIEW_RENDER_TYPE);
        VertexConsumer fillConsumer = bufferSource.getBuffer(RenderType.debugQuads());
        renderTranslatedSubLevelBounds(poseStack, fillConsumer, minecraft, subLevel, ClientStructureToolHandler.getPendingTranslateLocalOffset());
        renderPreviewPoint(poseStack, consumer, fillConsumer, new Vector3d(origin.x, origin.y, origin.z), 1.0F, 0.72F, 0.22F, 0.9F);
        renderPreviewPoint(poseStack, consumer, fillConsumer, new Vector3d(target.x, target.y, target.z), 0.45F, 1.0F, 0.35F, 0.95F);
        renderPreviewLine(poseStack, fillConsumer, new Vector3d(origin.x, origin.y, origin.z), new Vector3d(target.x, target.y, target.z), 0.95F, 0.86F, 0.34F, 0.88F);
        renderPreviewLine(poseStack, fillConsumer, new Vector3d(target.x, target.y, target.z).sub(axis), new Vector3d(target.x, target.y, target.z).add(axis), 0.45F, 1.0F, 0.35F, 0.92F);
        poseStack.popPose();
        bufferSource.endBatch(PREVIEW_RENDER_TYPE);
        bufferSource.endBatch(RenderType.debugQuads());
    }

    private static Vec3 resolveCurrentTranslateOrigin(Minecraft minecraft) {
        Vec3 localPivot = ClientStructureToolHandler.getPendingTranslatePivotLocalPoint();
        UUID subLevelId = ClientStructureToolHandler.getPendingTranslateSubLevelId();
        if (minecraft.level == null || localPivot == null || subLevelId == null) {
            return null;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(minecraft.level);
        SubLevel subLevel = container.getSubLevel(subLevelId);
        if (subLevel == null) {
            return null;
        }
        Vector3d physicalPoint = subLevel.logicalPose().transformPosition(new Vector3d(localPivot.x, localPivot.y, localPivot.z), new Vector3d());
        Vec3 projected = Sable.HELPER.projectOutOfSubLevel(minecraft.level, new Vec3(physicalPoint.x, physicalPoint.y, physicalPoint.z));
        return projected;
    }

    private static void renderTranslatedSubLevelBounds(PoseStack poseStack, VertexConsumer consumer, Minecraft minecraft, SubLevel subLevel, Vec3 localOffset) {
        BoundingBox3ic localBounds = subLevel.getPlot() != null ? subLevel.getPlot().getBoundingBox() : null;
        if (localBounds == null) {
            return;
        }
        Vector3d[] corners = createLocalBoundsCorners(localBounds);
        Vector3d worldOffset = new Vector3d(localOffset.x, localOffset.y, localOffset.z);
        subLevel.logicalPose().orientation().transform(worldOffset);
        for (int i = 0; i < corners.length; i++) {
            Vector3d physical = subLevel.logicalPose().transformPosition(corners[i], new Vector3d()).add(worldOffset);
            corners[i] = projectWorldPoint(minecraft, physical);
        }
        renderBoundsEdges(poseStack, consumer, corners, 1.0F, 0.90F, 0.28F, 0.92F);
    }

    private static void renderRotatedSubLevelBounds(PoseStack poseStack, VertexConsumer consumer, Minecraft minecraft, SubLevel subLevel, Vec3 localPivot, Quaterniond localRotation) {
        if (localPivot == null || localRotation == null) {
            return;
        }
        BoundingBox3ic localBounds = subLevel.getPlot() != null ? subLevel.getPlot().getBoundingBox() : null;
        if (localBounds == null) {
            return;
        }
        Quaterniond normalizedLocalRotation = new Quaterniond(localRotation).normalize();
        Quaterniond previewOrientation = new Quaterniond(subLevel.logicalPose().orientation()).mul(normalizedLocalRotation).normalize();
        Vector3d pivotLocal = new Vector3d(localPivot.x, localPivot.y, localPivot.z);
        Vector3d pivotBefore = subLevel.logicalPose().transformPosition(new Vector3d(pivotLocal), new Vector3d());
        Vector3d pivotAfter = transformLocalPoint(subLevel.logicalPose().position(), previewOrientation, pivotLocal);
        Vector3d previewPosition = new Vector3d(subLevel.logicalPose().position()).add(new Vector3d(pivotBefore).sub(pivotAfter));

        Vector3d[] corners = createLocalBoundsCorners(localBounds);
        for (int i = 0; i < corners.length; i++) {
            Vector3d physical = transformLocalPoint(previewPosition, previewOrientation, corners[i]);
            corners[i] = projectWorldPoint(minecraft, physical);
        }
        renderBoundsEdges(poseStack, consumer, corners, 1.0F, 0.90F, 0.28F, 0.92F);
    }

    private static void renderHeldBearingAxisPreview(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || minecraft.level == null
                || !ClientHeldToolState.holdsStructureTool(minecraft.player)) {
            return;
        }
        if (ClientToolState.getMode() != ToolMode.WELD) {
            return;
        }
        if (!ClientToolState.isBearingAxisVisualsEnabled() || ClientToolState.getConnectionMode() != ConnectionMode.BEARING) {
            return;
        }
        if (ClientStructureToolHandler.getPendingWeldPoint() != null) {
            return;
        }
        if (!(minecraft.hitResult instanceof BlockHitResult blockHit) || minecraft.hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        Vec3 snapped = ClientStructureToolHandler.resolveWeldSelectionPoint(minecraft.level, blockHit);
        Vector3d center = new Vector3d(snapped.x, snapped.y, snapped.z);
        SubLevel subLevel = Sable.HELPER.getContaining(minecraft.level, blockHit.getBlockPos());
        Vector3d axis = resolveSingleFacePreviewAxis(subLevel, blockHit.getDirection()).normalize(1.4D);

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer fillConsumer = bufferSource.getBuffer(RenderType.debugQuads());
        renderPreviewPoint(poseStack, fillConsumer, fillConsumer, center, 1.0F, 0.92F, 0.35F, 0.9F);
        renderPreviewLine(poseStack, fillConsumer, new Vector3d(center).sub(axis), new Vector3d(center).add(axis), 1.0F, 0.85F, 0.28F, 0.92F);
        poseStack.popPose();
        bufferSource.endBatch(RenderType.debugQuads());
    }

    private static Vector3d resolveSingleFacePreviewAxis(SubLevel subLevel, Direction face) {
        if (subLevel == null) {
            return WeldAxisGeometry.worldAxisFromFace(face);
        }
        return ClientToolState.getBearingAxisMode().resolveSingleFaceWorldAxis(subLevel, face);
    }

    private static void renderTrackedBearingVisuals(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || minecraft.player == null
                || !ClientHeldToolState.holdsAstGun(minecraft.player)
                || !ClientToolState.isBearingAxisVisualsEnabled()) {
            return;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(minecraft.level);
        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer fillConsumer = bufferSource.getBuffer(RenderType.debugQuads());
        for (ClientConstraintVisualTracker.ConstraintVisual visual : ClientConstraintVisualTracker.constraintVisuals()) {
            SubLevel first = container.getSubLevel(visual.firstSubLevelId());
            SubLevel second = container.getSubLevel(visual.secondSubLevelId());
            if (first == null || second == null) {
                continue;
            }
            Vector3d firstDisplayPoint = projectConstraintPoint(minecraft, first, visual.firstDisplayLocalPoint());
            Vector3d secondDisplayPoint = projectConstraintPoint(minecraft, second, visual.secondDisplayLocalPoint());
            Vector3d firstConstraintPoint = projectConstraintPoint(minecraft, first, visual.firstConstraintLocalPoint());
            Vector3d secondConstraintPoint = projectConstraintPoint(minecraft, second, visual.secondConstraintLocalPoint());
            renderPreviewPoint(poseStack, fillConsumer, fillConsumer, firstDisplayPoint, 0.35F, 0.9F, 1.0F, 0.88F);
            renderPreviewPoint(poseStack, fillConsumer, fillConsumer, secondDisplayPoint, 0.35F, 0.9F, 1.0F, 0.88F);
            switch (visual.connectionMode()) {
                case FIXED -> renderPreviewLine(poseStack, fillConsumer, firstDisplayPoint, secondDisplayPoint, 0.35F, 0.85F, 1.0F, 0.90F);
                case FREE -> renderPreviewLine(poseStack, fillConsumer, firstDisplayPoint, secondDisplayPoint, 0.55F, 1.0F, 0.62F, 0.88F);
                case BEARING -> {
                    if (visual.firstAxisLocal() == null) {
                        continue;
                    }
                    Vector3d axis = new Vector3d(visual.firstAxisLocal());
                    first.logicalPose().orientation().transform(axis);
                    if (axis.lengthSquared() <= 1.0E-6D) {
                        continue;
                    }
                    axis.normalize(1.2D);
                    Vector3d centerPhysicalPoint = first.logicalPose().transformPosition(new Vector3d(visual.firstConstraintLocalPoint()), new Vector3d());
                    centerPhysicalPoint.add(second.logicalPose().transformPosition(new Vector3d(visual.secondConstraintLocalPoint()), new Vector3d())).mul(0.5D);
                    Vector3d axisStart = projectWorldPoint(minecraft, new Vector3d(centerPhysicalPoint).sub(axis));
                    Vector3d axisEnd = projectWorldPoint(minecraft, new Vector3d(centerPhysicalPoint).add(axis));
                    renderPreviewLine(poseStack, fillConsumer, axisStart, axisEnd, 1.0F, 0.88F, 0.18F, 0.95F);
                }
            }
        }
        poseStack.popPose();
        bufferSource.endBatch(RenderType.debugQuads());
    }

    private static Vector3d projectConstraintPoint(Minecraft minecraft, SubLevel subLevel, Vector3d localPoint) {
        Vector3d physicalPoint = subLevel.logicalPose().transformPosition(new Vector3d(localPoint), new Vector3d());
        return projectWorldPoint(minecraft, physicalPoint);
    }

    private static Vector3d projectWorldPoint(Minecraft minecraft, Vector3d physicalPoint) {
        Vec3 projected = Sable.HELPER.projectOutOfSubLevel(
                minecraft.level,
                new Vec3(physicalPoint.x, physicalPoint.y, physicalPoint.z)
        );
        return new Vector3d(projected.x, projected.y, projected.z);
    }

    private static Component weldModeLabel() {
        if (ClientToolState.getConnectionMode() != ConnectionMode.BEARING) {
            return ClientToolState.getConnectionMode().title();
        }
        return Component.translatable(
                "screen.create_aeronautics_toolgun.weld_mode_with_axis",
                ClientToolState.getConnectionMode().title(),
                ClientToolState.getBearingAxisMode().title()
        );
    }

    private static void renderHudLine(GuiGraphics guiGraphics, Minecraft minecraft, Component text, float x, float y, float scale, int color, int outlineColor) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(scale, scale, 1.0F);
        int scaledX = Math.round(x / scale);
        int scaledY = Math.round(y / scale);
        guiGraphics.drawString(minecraft.font, text, scaledX + 1, scaledY + 1, outlineColor, false);
        guiGraphics.drawString(minecraft.font, text, scaledX, scaledY, color, true);
        guiGraphics.pose().popPose();
    }

}
