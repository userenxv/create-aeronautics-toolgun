package com.enxv.aeronauticsstructuretool.client.screen;

import net.minecraft.client.gui.GuiGraphics;

public final class ClientPanelStyle {
    public static final int PANEL = 0xCC1A1712;
    public static final int PANEL_LIGHT = 0xCC2B261D;
    public static final int PANEL_DARK = 0xCC100D0A;
    public static final int PANEL_MID = 0xCC221D16;
    public static final int BRASS = 0xFFD3B06A;
    public static final int BRASS_SOFT = 0xFF9E7B3D;
    public static final int TEXT_PRIMARY = 0xFFF6E7C2;
    public static final int TEXT_MUTED = 0xFFB8A27B;
    public static final int TEXT_WARN = 0xFFFFD58A;
    public static final int TEXT_ERROR = 0xFFFF6B6B;
    public static final int BLUEPRINT_BLUE = 0xFF5DB7FF;
    public static final int BLUEPRINT_BLUE_SOFT = 0x663A9DFF;
    public static final int BLUEPRINT_BLUE_GLOW = 0x223A9DFF;

    private ClientPanelStyle() {
    }

    public static void drawPanel(GuiGraphics graphics, int left, int top, int right, int bottom, int fill) {
        graphics.fill(left, top, right, bottom, fill);
        graphics.fill(left, top, right, top + 1, BRASS);
        graphics.fill(left, bottom - 1, right, bottom, PANEL_DARK);
        graphics.fill(left, top, left + 1, bottom, BRASS_SOFT);
        graphics.fill(right - 1, top, right, bottom, PANEL_DARK);
    }

    public static void drawSelectionRow(
            GuiGraphics graphics,
            int left,
            int right,
            int rowTop,
            boolean selected,
            boolean hovered
    ) {
        if (selected) {
            graphics.fill(left, rowTop - 2, right, rowTop + 12, 0x556B5733);
            graphics.fill(left, rowTop + 11, right, rowTop + 12, BRASS);
        } else if (hovered) {
            graphics.fill(left, rowTop - 2, right, rowTop + 12, 0x332D2416);
        }
    }
}
