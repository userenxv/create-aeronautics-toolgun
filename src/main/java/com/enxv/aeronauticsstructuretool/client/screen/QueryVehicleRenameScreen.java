package com.enxv.aeronauticsstructuretool.client.screen;

import com.enxv.aeronauticsstructuretool.QueryVehicleActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

public final class QueryVehicleRenameScreen extends Screen {
    private static final int WINDOW_WIDTH = 300;
    private static final int WINDOW_HEIGHT = 118;
    private static final int PANEL = 0xD018140F;
    private static final int PANEL_LIGHT = 0xD22A241A;
    private static final int PANEL_DARK = 0xCC0E0A08;
    private static final int BRASS = 0xFFD3B06A;
    private static final int BRASS_SOFT = 0xFF9E7B3D;
    private static final int TEXT_PRIMARY = 0xFFF6E7C2;
    private static final int TEXT_MUTED = 0xFFB8A27B;

    private final Screen parent;
    private final UUID subLevelId;
    private final String currentName;
    private EditBox nameBox;

    public QueryVehicleRenameScreen(Screen parent, UUID subLevelId, String currentName) {
        super(Component.translatable("screen.create_aeronautics_toolgun.query.rename_title"));
        this.parent = parent;
        this.subLevelId = subLevelId;
        this.currentName = currentName == null ? "" : currentName;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int top = this.height / 2 - WINDOW_HEIGHT / 2;

        this.nameBox = new EditBox(this.font, centerX - 124, top + 42, 248, 20, Component.translatable("screen.create_aeronautics_toolgun.query.rename_hint"));
        this.nameBox.setMaxLength(48);
        this.nameBox.setFocused(true);
        this.nameBox.setBordered(false);
        this.nameBox.setTextColor(TEXT_PRIMARY);
        this.nameBox.setTextColorUneditable(TEXT_MUTED);
        this.nameBox.setValue(this.currentName);
        this.addRenderableWidget(this.nameBox);

        this.addRenderableWidget(new PanelButton(centerX - 124, top + 76, 118, 20, Component.translatable("screen.create_aeronautics_toolgun.confirm"), button -> confirm()));
        this.addRenderableWidget(new PanelButton(centerX + 6, top + 76, 118, 20, Component.translatable("screen.create_aeronautics_toolgun.cancel"), button -> this.onClose()));
    }

    private void confirm() {
        String name = this.nameBox.getValue().trim();
        if (name.isEmpty()) {
            showClientMessage(Component.translatable("message.create_aeronautics_toolgun.query_need_name"));
            return;
        }
        PacketDistributor.sendToServer(new QueryVehicleActionPayload(
                this.subLevelId,
                QueryVehicleActionPayload.ACTION_RENAME,
                0.0D,
                0.0D,
                0.0D,
                name
        ));
        this.onClose();
    }

    private void showClientMessage(Component message) {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(message, true);
        }
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return this.nameBox.charTyped(codePoint, modifiers) || super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.nameBox.keyPressed(keyCode, scanCode, modifiers) || this.nameBox.canConsumeInput()) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
        guiGraphics.fill(left + 14, top + 38, right - 14, top + 62, PANEL_LIGHT);
        guiGraphics.fill(left + 14, top + 61, right - 14, top + 62, PANEL_DARK);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, top + 12, TEXT_PRIMARY);
        guiGraphics.drawCenteredString(this.font, Component.translatable("screen.create_aeronautics_toolgun.query.rename_hint"), this.width / 2, top + 28, TEXT_MUTED);
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
            guiGraphics.drawCenteredString(QueryVehicleRenameScreen.this.font, this.getMessage(), this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, text);
        }
    }
}
