package com.enxv.aeronauticsstructuretool.client.screen;

import com.enxv.aeronauticsstructuretool.QueryVehicleActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.BRASS;
import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.BRASS_SOFT;
import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.PANEL;
import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.PANEL_DARK;
import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.TEXT_MUTED;
import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.TEXT_PRIMARY;
import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.TEXT_WARN;

public final class QueryVehicleStorageActionScreen extends Screen {
    private static final int WINDOW_WIDTH = 320;
    private static final int WINDOW_HEIGHT = 142;

    private final Screen parent;
    private final UUID subLevelId;
    private final String displayName;
    private final Action action;

    public QueryVehicleStorageActionScreen(
            Screen parent,
            UUID subLevelId,
            String displayName,
            Action action
    ) {
        super(Component.translatable(action.titleKey));
        this.parent = parent;
        this.subLevelId = subLevelId;
        this.displayName = displayName == null ? "" : displayName;
        this.action = action;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int top = this.height / 2 - WINDOW_HEIGHT / 2;
        this.addRenderableWidget(new PanelButton(
                centerX - 134,
                top + 108,
                128,
                20,
                Component.translatable("screen.create_aeronautics_toolgun.confirm"),
                button -> confirm()
        ));
        this.addRenderableWidget(new PanelButton(
                centerX + 6,
                top + 108,
                128,
                20,
                Component.translatable("screen.create_aeronautics_toolgun.cancel"),
                button -> this.onClose()
        ));
    }

    private void confirm() {
        PacketDistributor.sendToServer(new QueryVehicleActionPayload(
                this.subLevelId,
                this.action.payloadAction,
                0.0D,
                0.0D,
                0.0D,
                ""
        ));
        this.onClose();
        if (this.parent instanceof ToolModeScreen toolModeScreen) {
            toolModeScreen.afterVehicleStorageActionRequested();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x50000000);
        int left = this.width / 2 - WINDOW_WIDTH / 2;
        int top = this.height / 2 - WINDOW_HEIGHT / 2;
        int right = left + WINDOW_WIDTH;
        int bottom = top + WINDOW_HEIGHT;
        graphics.fill(left - 8, top - 8, right + 8, bottom + 8, 0x55000000);
        graphics.fill(left, top, right, bottom, PANEL);
        graphics.fill(left, top, right, top + 1, BRASS);
        graphics.fill(left, top, left + 1, bottom, BRASS);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, top + 12, TEXT_PRIMARY);
        graphics.drawCenteredString(
                this.font,
                this.font.plainSubstrByWidth(this.displayName, 280),
                this.width / 2,
                top + 32,
                this.action.accentColor
        );
        graphics.drawWordWrap(
                this.font,
                Component.translatable(this.action.detailKey),
                left + 20,
                top + 50,
                WINDOW_WIDTH - 40,
                TEXT_MUTED
        );
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
    public void renderTransparentBackground(GuiGraphics graphics) {
    }

    public enum Action {
        RECOVER(
                QueryVehicleActionPayload.ACTION_RECOVER,
                "screen.create_aeronautics_toolgun.query.recover_title",
                "screen.create_aeronautics_toolgun.query.recover_confirm",
                TEXT_WARN
        );

        private final String payloadAction;
        private final String titleKey;
        private final String detailKey;
        private final int accentColor;

        Action(String payloadAction, String titleKey, String detailKey, int accentColor) {
            this.payloadAction = payloadAction;
            this.titleKey = titleKey;
            this.detailKey = detailKey;
            this.accentColor = accentColor;
        }
    }

    private final class PanelButton extends Button {
        private PanelButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int fill = !this.active ? 0xAA262019 : (this.isHoveredOrFocused() ? 0xFF5F4A2A : 0xFF3A2F1F);
            int border = this.isHoveredOrFocused() ? BRASS : BRASS_SOFT;
            int text = !this.active ? 0xFF7F735C : TEXT_PRIMARY;
            graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, fill);
            graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + 1, border);
            graphics.fill(this.getX(), this.getY() + this.height - 1, this.getX() + this.width, this.getY() + this.height, PANEL_DARK);
            graphics.fill(this.getX(), this.getY(), this.getX() + 1, this.getY() + this.height, border);
            graphics.fill(this.getX() + this.width - 1, this.getY(), this.getX() + this.width, this.getY() + this.height, PANEL_DARK);
            graphics.drawCenteredString(
                    QueryVehicleStorageActionScreen.this.font,
                    this.getMessage(),
                    this.getX() + this.width / 2,
                    this.getY() + (this.height - 8) / 2,
                    text
            );
        }
    }
}
