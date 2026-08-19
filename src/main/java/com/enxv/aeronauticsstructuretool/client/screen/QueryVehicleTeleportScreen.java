package com.enxv.aeronauticsstructuretool.client.screen;

import com.enxv.aeronauticsstructuretool.QueryVehicleActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.navigation.CommonInputs;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

public final class QueryVehicleTeleportScreen extends Screen {
    private static final int WINDOW_WIDTH = 320;
    private static final int WINDOW_HEIGHT = 202;
    private static final int PANEL = 0xD018140F;
    private static final int PANEL_LIGHT = 0xD22A241A;
    private static final int PANEL_DARK = 0xCC0E0A08;
    private static final int BRASS = 0xFFD3B06A;
    private static final int BRASS_SOFT = 0xFF9E7B3D;
    private static final int TEXT_PRIMARY = 0xFFF6E7C2;
    private static final int TEXT_MUTED = 0xFFB8A27B;

    private final Screen parent;
    private final UUID subLevelId;
    private final String displayName;
    private final double initialX;
    private final double initialY;
    private final double initialZ;
    private final boolean vehicleLoaded;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;

    public QueryVehicleTeleportScreen(
            Screen parent,
            UUID subLevelId,
            String displayName,
            double initialX,
            double initialY,
            double initialZ,
            boolean vehicleLoaded
    ) {
        super(Component.translatable("screen.create_aeronautics_toolgun.query.teleport_title"));
        this.parent = parent;
        this.subLevelId = subLevelId;
        this.displayName = displayName == null ? "" : displayName;
        this.initialX = initialX;
        this.initialY = initialY;
        this.initialZ = initialZ;
        this.vehicleLoaded = vehicleLoaded;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int top = this.height / 2 - WINDOW_HEIGHT / 2;
        int left = centerX - WINDOW_WIDTH / 2;

        this.xBox = createCoordinateBox(left + 82, top + 48, 210, this.initialX, "X");
        this.yBox = createCoordinateBox(left + 82, top + 76, 210, this.initialY, "Y");
        this.zBox = createCoordinateBox(left + 82, top + 104, 210, this.initialZ, "Z");
        this.xBox.setEditable(this.vehicleLoaded);
        this.yBox.setEditable(this.vehicleLoaded);
        this.zBox.setEditable(this.vehicleLoaded);
        this.addRenderableWidget(this.xBox);
        this.addRenderableWidget(this.yBox);
        this.addRenderableWidget(this.zBox);
        if (this.vehicleLoaded) {
            this.xBox.setFocused(true);
            this.setInitialFocus(this.xBox);
        }

        this.addRenderableWidget(new PanelButton(left + 18, top + 138, 284, 20, Component.translatable("screen.create_aeronautics_toolgun.query.teleport_player_to_vehicle"), button -> teleportPlayerToVehicle()));
        Button confirmButton = this.addRenderableWidget(new PanelButton(left + 18, top + 166, 136, 20, Component.translatable("screen.create_aeronautics_toolgun.confirm"), button -> confirm()));
        confirmButton.active = this.vehicleLoaded;
        this.addRenderableWidget(new PanelButton(left + 166, top + 166, 136, 20, Component.translatable("screen.create_aeronautics_toolgun.cancel"), button -> this.onClose()));
    }

    private EditBox createCoordinateBox(int x, int y, int width, double initialValue, String axis) {
        EditBox box = new EditBox(this.font, x, y, width, 18, Component.literal(axis));
        box.setBordered(false);
        box.setMaxLength(20);
        box.setTextColor(TEXT_PRIMARY);
        box.setTextColorUneditable(TEXT_MUTED);
        box.setFilter(QueryVehicleTeleportScreen::isValidCoordinateText);
        box.setValue(formatCoordinate(initialValue));
        return box;
    }

    private void confirm() {
        if (!this.vehicleLoaded) {
            showClientMessage(Component.translatable("screen.create_aeronautics_toolgun.query.teleport_unloaded_hint"));
            return;
        }
        Double x = parseCoordinate(this.xBox.getValue(), this.minecraft != null && this.minecraft.player != null ? this.minecraft.player.getX() : null);
        Double y = parseCoordinate(this.yBox.getValue(), this.minecraft != null && this.minecraft.player != null ? this.minecraft.player.getY() : null);
        Double z = parseCoordinate(this.zBox.getValue(), this.minecraft != null && this.minecraft.player != null ? this.minecraft.player.getZ() : null);
        if (x == null || y == null || z == null) {
            showClientMessage(Component.translatable("message.create_aeronautics_toolgun.query_invalid_coordinates"));
            return;
        }
        PacketDistributor.sendToServer(new QueryVehicleActionPayload(
                this.subLevelId,
                QueryVehicleActionPayload.ACTION_TELEPORT,
                x,
                y,
                z,
                ""
        ));
        this.onClose();
    }

    private void teleportPlayerToVehicle() {
        PacketDistributor.sendToServer(new QueryVehicleActionPayload(
                this.subLevelId,
                QueryVehicleActionPayload.ACTION_TELEPORT_PLAYER_TO_VEHICLE,
                0.0D,
                0.0D,
                0.0D,
                ""
        ));
        this.onClose();
    }

    private void showClientMessage(Component message) {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(message, true);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (CommonInputs.selected(keyCode)) {
            confirm();
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
        renderCoordinateRow(guiGraphics, left, top + 44);
        renderCoordinateRow(guiGraphics, left, top + 72);
        renderCoordinateRow(guiGraphics, left, top + 100);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, top + 12, TEXT_PRIMARY);
        guiGraphics.drawCenteredString(this.font, this.font.plainSubstrByWidth(this.displayName, 260), this.width / 2, top + 28, TEXT_MUTED);
        guiGraphics.drawString(this.font, "X", left + 30, top + 53, BRASS, false);
        guiGraphics.drawString(this.font, "Y", left + 30, top + 81, BRASS, false);
        guiGraphics.drawString(this.font, "Z", left + 30, top + 109, BRASS, false);
        guiGraphics.drawString(
                this.font,
                Component.translatable(this.vehicleLoaded
                        ? "screen.create_aeronautics_toolgun.query.teleport_blank_player_hint"
                        : "screen.create_aeronautics_toolgun.query.teleport_unloaded_hint"),
                left + 18,
                top + 126,
                TEXT_MUTED,
                false
        );
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderCoordinateRow(GuiGraphics guiGraphics, int left, int rowTop) {
        guiGraphics.fill(left + 18, rowTop, left + 302, rowTop + 24, PANEL_LIGHT);
        guiGraphics.fill(left + 18, rowTop + 23, left + 302, rowTop + 24, PANEL_DARK);
        guiGraphics.fill(left + 76, rowTop + 3, left + 296, rowTop + 21, PANEL_DARK);
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

    private static String formatCoordinate(double value) {
        String raw = String.format(java.util.Locale.ROOT, "%.2f", value);
        while (raw.endsWith("0")) {
            raw = raw.substring(0, raw.length() - 1);
        }
        if (raw.endsWith(".")) {
            raw = raw.substring(0, raw.length() - 1);
        }
        return raw;
    }

    private static Double parseCoordinate(String raw, Double blankValue) {
        if (raw == null || raw.isBlank()) {
            return blankValue;
        }
        try {
            double value = Double.parseDouble(raw.trim());
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
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
            guiGraphics.drawCenteredString(QueryVehicleTeleportScreen.this.font, this.getMessage(), this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, text);
        }
    }

    private static boolean isValidCoordinateText(String raw) {
        for (int i = 0; i < raw.length(); i++) {
            char character = raw.charAt(i);
            if ((character < '0' || character > '9')
                    && character != '-'
                    && character != '.'
                    && character != '+') {
                return false;
            }
        }
        return true;
    }
}
