package com.enxv.aeronauticsstructuretool.client.screen;

import com.enxv.aeronauticsstructuretool.QueryVehicleActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

public final class QueryVehicleDeleteScreen extends Screen {
    private static final int WINDOW_WIDTH = 300;
    private static final int WINDOW_HEIGHT = 118;
    private static final int PANEL = 0xD018140F;
    private static final int PANEL_DARK = 0xCC0E0A08;
    private static final int BRASS = 0xFFD3B06A;
    private static final int BRASS_SOFT = 0xFF9E7B3D;
    private static final int TEXT_PRIMARY = 0xFFF6E7C2;
    private static final int TEXT_MUTED = 0xFFB8A27B;
    private static final int TEXT_WARN = 0xFFFFD58A;

    private final Screen parent;
    private final UUID subLevelId;
    private final String displayName;

    public QueryVehicleDeleteScreen(Screen parent, UUID subLevelId, String displayName) {
        super(Component.translatable("screen.create_aeronautics_toolgun.query.delete_title"));
        this.parent = parent;
        this.subLevelId = subLevelId;
        this.displayName = displayName == null ? "" : displayName;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int top = this.height / 2 - WINDOW_HEIGHT / 2;
        this.addRenderableWidget(new PanelButton(centerX - 124, top + 76, 118, 20, Component.translatable("screen.create_aeronautics_toolgun.confirm"), button -> confirm()));
        this.addRenderableWidget(new PanelButton(centerX + 6, top + 76, 118, 20, Component.translatable("screen.create_aeronautics_toolgun.cancel"), button -> this.onClose()));
    }

    private void confirm() {
        PacketDistributor.sendToServer(new QueryVehicleActionPayload(
                this.subLevelId,
                QueryVehicleActionPayload.ACTION_DELETE,
                0.0D,
                0.0D,
                0.0D,
                ""
        ));
        if (this.parent instanceof ToolModeScreen toolModeScreen) {
            toolModeScreen.afterVehicleDeleted(this.subLevelId);
        }
        this.onClose();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0x50000000);
        int left = this.width / 2 - WINDOW_WIDTH / 2;
        int top = this.height / 2 - WINDOW_HEIGHT / 2;
        int right = left + WINDOW_WIDTH;
        int bottom = top + WINDOW_HEIGHT;
        guiGraphics.fill(left - 8, top - 8, right + 8, bottom + 8, 0x55000000);
        guiGraphics.fill(left, top, right, bottom, PANEL);
        guiGraphics.fill(left, top, right, top + 1, BRASS);
        guiGraphics.fill(left, top, left + 1, bottom, BRASS);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, top + 12, TEXT_PRIMARY);
        guiGraphics.drawCenteredString(this.font, this.font.plainSubstrByWidth(this.displayName, 260), this.width / 2, top + 32, TEXT_WARN);
        guiGraphics.drawCenteredString(this.font, Component.translatable("screen.create_aeronautics_toolgun.query.delete_confirm"), this.width / 2, top + 50, TEXT_MUTED);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    @Override
    public void renderTransparentBackground(GuiGraphics guiGraphics) {
    }

    private final class PanelButton extends Button {
        private PanelButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            int fill = !this.active ? 0xAA262019 : (this.isHoveredOrFocused() ? 0xFF5F4A2A : 0xFF3A2F1F);
            int border = this.isHoveredOrFocused() ? BRASS : BRASS_SOFT;
            int text = !this.active ? 0xFF7F735C : TEXT_PRIMARY;

            guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, fill);
            guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + 1, border);
            guiGraphics.fill(this.getX(), this.getY() + this.height - 1, this.getX() + this.width, this.getY() + this.height, PANEL_DARK);
            guiGraphics.fill(this.getX(), this.getY(), this.getX() + 1, this.getY() + this.height, border);
            guiGraphics.fill(this.getX() + this.width - 1, this.getY(), this.getX() + this.width, this.getY() + this.height, PANEL_DARK);
            guiGraphics.drawCenteredString(QueryVehicleDeleteScreen.this.font, this.getMessage(), this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, text);
        }
    }
}
