package com.enxv.aeronauticsstructuretool.client.screen;

import com.enxv.aeronauticsstructuretool.PortableStructurePreviewData;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;

import java.util.Set;

public final class StructurePreviewRenderer {
    private static PortableStructurePreviewData cachedPreview;
    private static Object cachedLevel;
    private static StructurePreviewRenderCache renderCache;

    private StructurePreviewRenderer() {
    }

    public static void render(
            GuiGraphics guiGraphics,
            int x,
            int y,
            int width,
            int height,
            PortableStructurePreviewData preview,
            float yaw,
            float pitch,
            float zoom,
            Set<String> skippedBlockEntityTypes
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || preview == null) {
            return;
        }
        StructurePreviewRenderCache cache = cacheFor(minecraft, preview);
        if (cache == null) {
            return;
        }

        guiGraphics.enableScissor(x + 2, y + 2, x + width - 2, y + height - 2);
        guiGraphics.flush();
        RenderSystem.enableDepthTest();
        Lighting.setupFor3DItems();
        guiGraphics.pose().pushPose();
        float fitScale = computeScale(width, height, preview);
        guiGraphics.pose().translate(x + width / 2.0F, y + height * 0.62F, 180.0F);
        guiGraphics.pose().scale(fitScale * zoom, -fitScale * zoom, fitScale * zoom);
        guiGraphics.pose().mulPose(Axis.XP.rotationDegrees(pitch));
        guiGraphics.pose().mulPose(Axis.YP.rotationDegrees(yaw));

        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix())
                .mul(guiGraphics.pose().last().pose());
        cache.renderModels(modelView, RenderSystem.getProjectionMatrix());
        cache.renderBlockEntities(guiGraphics, minecraft, skippedBlockEntityTypes);

        guiGraphics.pose().popPose();
        guiGraphics.flush();
        guiGraphics.disableScissor();
    }

    public static void clearCache() {
        StructurePreviewRenderCache previous = renderCache;
        renderCache = null;
        cachedPreview = null;
        cachedLevel = null;
        if (previous != null) {
            previous.closeOnRenderThread();
        }
    }

    private static StructurePreviewRenderCache cacheFor(
            Minecraft minecraft,
            PortableStructurePreviewData preview
    ) {
        if (renderCache != null && cachedPreview == preview && cachedLevel == minecraft.level) {
            renderCache.advance(minecraft);
            return renderCache;
        }
        clearCache();
        try {
            renderCache = StructurePreviewRenderCache.begin(preview);
            cachedPreview = preview;
            cachedLevel = minecraft.level;
            renderCache.advance(minecraft);
            return renderCache;
        } catch (RuntimeException | Error throwable) {
            com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod.LOGGER.warn(
                    "Failed to build cached blueprint GUI preview for '{}'",
                    preview.name(),
                    throwable
            );
            return null;
        }
    }

    private static float computeScale(
            int panelWidth,
            int panelHeight,
            PortableStructurePreviewData preview
    ) {
        double span = Math.max(1.0D, preview.maxSpan());
        float usable = Math.min(panelWidth, panelHeight) * 0.46F;
        return (float) (usable / span);
    }
}
