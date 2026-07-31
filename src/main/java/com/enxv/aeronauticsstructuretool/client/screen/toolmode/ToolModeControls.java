package com.enxv.aeronauticsstructuretool.client.screen.toolmode;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.BRASS;
import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.BRASS_SOFT;
import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.PANEL_DARK;
import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.TEXT_MUTED;
import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.TEXT_PRIMARY;

public final class ToolModeControls {
    private ToolModeControls() {
    }

    public static final class PanelButton extends Button {
        private final Font font;

        public PanelButton(Font font, int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.font = font;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean closeButton = "X".equals(this.getMessage().getString());
            int fill = !this.active ? 0xAA262019 : (this.isHoveredOrFocused() ? 0xFF5F4A2A : 0xFF3A2F1F);
            int border = this.isHoveredOrFocused() ? BRASS : BRASS_SOFT;
            int text = !this.active ? 0xFF7F735C : TEXT_PRIMARY;
            graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, fill);
            graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + 1, border);
            graphics.fill(this.getX(), this.getY() + this.height - 1, this.getX() + this.width, this.getY() + this.height, PANEL_DARK);
            graphics.fill(this.getX(), this.getY(), this.getX() + 1, this.getY() + this.height, border);
            graphics.fill(this.getX() + this.width - 1, this.getY(), this.getX() + this.width, this.getY() + this.height, PANEL_DARK);
            graphics.drawCenteredString(
                    this.font,
                    this.getMessage(),
                    this.getX() + this.width / 2,
                    this.getY() + (closeButton ? 4 : (this.height - 8) / 2),
                    text
            );
        }
    }

    public static final class InfiniteRangeToggle {
        public static final int WIDTH = 56;
        public static final int HEIGHT = 16;

        private final BooleanSupplier checked;
        private final Runnable toggle;
        private int x;
        private int y;
        private boolean visible;

        public InfiniteRangeToggle(int x, int y, BooleanSupplier checked, Runnable toggle) {
            this.checked = checked;
            this.toggle = toggle;
            setPosition(x, y);
        }

        public void setPosition(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public void setVisible(boolean visible) {
            this.visible = visible;
        }

        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!this.visible || button != 0 || mouseX < this.x || mouseX > this.x + WIDTH
                    || mouseY < this.y || mouseY > this.y + HEIGHT) {
                return false;
            }
            this.toggle.run();
            return true;
        }

        public void render(GuiGraphics graphics, Font font, double mouseX, double mouseY) {
            if (!this.visible) {
                return;
            }
            boolean selected = this.checked.getAsBoolean();
            boolean hovered = mouseX >= this.x && mouseX <= this.x + WIDTH
                    && mouseY >= this.y && mouseY <= this.y + HEIGHT;
            int box = hovered ? 0xFF4A3A23 : 0xFF241E15;
            int border = hovered ? BRASS : BRASS_SOFT;
            graphics.fill(this.x, this.y + 2, this.x + 12, this.y + 14, box);
            graphics.fill(this.x, this.y + 2, this.x + 12, this.y + 3, border);
            graphics.fill(this.x, this.y + 13, this.x + 12, this.y + 14, PANEL_DARK);
            graphics.fill(this.x, this.y + 2, this.x + 1, this.y + 14, border);
            graphics.fill(this.x + 11, this.y + 2, this.x + 12, this.y + 14, PANEL_DARK);
            if (selected) {
                graphics.fill(this.x + 3, this.y + 5, this.x + 9, this.y + 11, BRASS);
                graphics.fill(this.x + 4, this.y + 6, this.x + 8, this.y + 10, 0xFFFFE4A8);
            }
            graphics.drawString(
                    font,
                    Component.translatable("screen.create_aeronautics_toolgun.query.infinite"),
                    this.x + 17,
                    this.y + 4,
                    selected ? TEXT_PRIMARY : TEXT_MUTED,
                    false
            );
        }
    }

    public static final class SearchField {
        private final Runnable valueChanged;
        private int x;
        private int y;
        private int width;
        private int height;
        private boolean focused;
        private boolean visible = true;
        private String value = "";
        private int maxLength = 64;

        public SearchField(int x, int y, int width, int height, Runnable valueChanged) {
            this.valueChanged = valueChanged;
            setBounds(x, y, width, height);
        }

        public void setBounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public void setVisible(boolean visible) {
            this.visible = visible;
        }

        public void setFocused(boolean focused) {
            this.focused = focused && this.visible;
        }

        public boolean isFocused() {
            return this.focused;
        }

        public String getValue() {
            return this.value;
        }

        public int getX() {
            return this.x;
        }

        public int getY() {
            return this.y;
        }

        public int getWidth() {
            return this.width;
        }

        public int getHeight() {
            return this.height;
        }

        public void render(GuiGraphics graphics, Font font) {
            if (!this.visible) {
                return;
            }
            String shown = tailThatFits(font, this.value, Math.max(0, this.width - 8));
            graphics.flush();
            graphics.enableScissor(this.x, this.y, this.x + this.width, this.y + this.height);
            graphics.drawString(font, shown, this.x + 4, this.y + 3, TEXT_PRIMARY, false);
            if (this.focused && (System.currentTimeMillis() / 300L) % 2L == 0L) {
                int cursorX = this.x + 4 + font.width(shown);
                graphics.fill(cursorX, this.y + 3, cursorX + 1, this.y + this.height - 3, TEXT_PRIMARY);
            }
            graphics.flush();
            graphics.disableScissor();
        }

        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            boolean inside = mouseX >= this.x && mouseX <= this.x + this.width
                    && mouseY >= this.y && mouseY <= this.y + this.height;
            setFocused(inside && button == 0);
            return inside;
        }

        public boolean charTyped(char codePoint, int modifiers) {
            if (!this.focused || !isAllowedSearchCharacter(codePoint)) {
                return false;
            }
            if (this.value.length() >= this.maxLength) {
                return true;
            }
            this.value += codePoint;
            this.valueChanged.run();
            return true;
        }

        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (!this.focused) {
                return false;
            }
            if (Screen.isPaste(keyCode)) {
                String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (clipboard != null && !clipboard.isEmpty()) {
                    String filtered = filterSearchText(clipboard);
                    if (!filtered.isEmpty()) {
                        int room = this.maxLength - this.value.length();
                        if (room > 0) {
                            this.value += filtered.substring(0, Math.min(room, filtered.length()));
                            this.valueChanged.run();
                        }
                    }
                }
                return true;
            }
            if (keyCode == 259 && !this.value.isEmpty()) {
                this.value = this.value.substring(0, this.value.length() - 1);
                this.valueChanged.run();
                return true;
            }
            return keyCode == 257 || keyCode == 335 || keyCode == 256;
        }

        public boolean canConsumeInput() {
            return this.focused;
        }

        private static String tailThatFits(Font font, String raw, int maxWidth) {
            if (font.width(raw) <= maxWidth) {
                return raw;
            }
            for (int start = raw.length() - 1; start >= 0; start--) {
                String candidate = raw.substring(start);
                if (font.width(candidate) <= maxWidth) {
                    return candidate;
                }
            }
            return "";
        }

        private static boolean isAllowedSearchCharacter(char codePoint) {
            return codePoint >= 32 && codePoint != 127;
        }

        private static String filterSearchText(String raw) {
            StringBuilder builder = new StringBuilder(raw.length());
            for (int i = 0; i < raw.length(); i++) {
                char ch = raw.charAt(i);
                if (isAllowedSearchCharacter(ch)) {
                    builder.append(ch);
                }
            }
            return builder.toString();
        }
    }
}
