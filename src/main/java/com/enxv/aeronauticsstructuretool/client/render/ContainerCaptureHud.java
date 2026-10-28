package com.enxv.aeronauticsstructuretool.client.render;

import com.enxv.aeronauticsstructuretool.PortableStructureContainerItem;
import com.enxv.aeronauticsstructuretool.SyncContainerCapturePayload;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

public final class ContainerCaptureHud {
    private static SyncContainerCapturePayload progress;
    private static long lastUpdate;
    private static boolean requireRelease;

    private ContainerCaptureHud() {
    }

    public static boolean isCapturing() {
        return progress != null;
    }

    public static void receive(SyncContainerCapturePayload payload) {
        progress = payload.totalTicks() > 0 ? payload : null;
        lastUpdate = System.nanoTime();
        if (progress == null && Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.stopUsingItem();
        }
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            progress = null;
            requireRelease = false;
            return;
        }
        if (!mc.options.keyUse.isDown()) {
            requireRelease = false;
        }
        if (progress != null && (!mc.player.isUsingItem()
                || !(mc.player.getUseItem().getItem() instanceof PortableStructureContainerItem))) {
            progress = null;
        }
    }

    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isUseItem() || mc.player == null) {
            return;
        }
        if (requireRelease) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        if (mc.player.getItemInHand(event.getHand()).getItem() instanceof PortableStructureContainerItem) {
            requireRelease = true;
        }
    }

    public static void render(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (progress == null || mc.player == null || mc.screen != null || mc.options.hideGui) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int x = (graphics.guiWidth() - AllGuiTextures.TRAIN_HUD_FRAME.getWidth()) / 2;
        // Keep the hint above the survival status bars and experience bar.
        int y = graphics.guiHeight() - 78;
        // The same frame and striped fill used by Create's train throttle HUD.
        AllGuiTextures.TRAIN_HUD_SPEED_BG.render(graphics, x + 2, y + 1);
        AllGuiTextures fill = AllGuiTextures.TRAIN_HUD_THROTTLE;
        double interpolated = Math.max(0D, Math.min(progress.totalTicks(), progress.progressTicks()
                + progress.rate() * Math.min(4D, (System.nanoTime() - lastUpdate) / 50000000D)));
        float fraction = (float) (interpolated / progress.totalTicks());
        graphics.blit(fill.location, x + 2, y + 1, fill.getStartX(), fill.getStartY(),
                (int) (fill.getWidth() * fraction), fill.getHeight());
        AllGuiTextures.TRAIN_HUD_FRAME.render(graphics, x, y);
        graphics.drawCenteredString(mc.font, Component.translatable(
                "hud.create_aeronautics_toolgun.container_capture", progress.blockCount(), (int) (fraction * 100)),
                graphics.guiWidth() / 2, y - 13, 0xFFFFFF);
        graphics.drawCenteredString(mc.font, Component.translatable("hud.create_aeronautics_toolgun.container_capture_hold"),
                graphics.guiWidth() / 2, y + 12, 0xFFFFFF);
    }
}
