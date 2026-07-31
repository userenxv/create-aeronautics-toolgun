package com.enxv.aeronauticsstructuretool.client.screen.toolmode;

import com.enxv.aeronauticsstructuretool.client.screen.toolmode.ToolModeMenuModel.LeftTab;

import net.minecraft.util.Mth;

public final class ToolModeLayout {
    public static final int PAGE_BUTTON_WIDTH = 64;
    public static final int PAGE_BUTTON_HEIGHT = 20;

    private static final int NORMAL_WINDOW_WIDTH = 392;
    private static final int LOAD_WINDOW_WIDTH = 520;
    private static final int QUERY_WINDOW_WIDTH = 640;
    private static final int WINDOW_HEIGHT = 236;
    private static final int MIN_WINDOW_WIDTH = 320;
    private static final int SCREEN_MARGIN = 8;
    private static final int FOOTER_BUTTON_Y = 194;
    private static final int FOOTER_TEXT_Y = 199;

    private final int screenWidth;
    private final int screenHeight;
    private final LeftTab tab;
    private final int windowWidth;
    private final int windowHeight;
    private final int windowLeft;
    private final int windowTop;

    public ToolModeLayout(int screenWidth, int screenHeight, LeftTab tab) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.tab = tab;
        this.windowWidth = calculateWindowWidth(screenWidth, tab);
        this.windowHeight = Math.min(WINDOW_HEIGHT, Math.max(1, screenHeight - SCREEN_MARGIN * 2));
        this.windowLeft = Mth.clamp(
                (screenWidth - this.windowWidth) / 2,
                SCREEN_MARGIN,
                Math.max(SCREEN_MARGIN, screenWidth - this.windowWidth - SCREEN_MARGIN)
        );
        this.windowTop = Mth.clamp(
                (screenHeight - this.windowHeight) / 2,
                SCREEN_MARGIN,
                Math.max(SCREEN_MARGIN, screenHeight - this.windowHeight - SCREEN_MARGIN)
        );
    }

    public boolean matches(int screenWidth, int screenHeight, LeftTab tab) {
        return this.screenWidth == screenWidth && this.screenHeight == screenHeight && this.tab == tab;
    }

    public int windowLeft() {
        return this.windowLeft;
    }

    public int windowTop() {
        return this.windowTop;
    }

    public int windowHeight() {
        return this.windowHeight;
    }

    public int windowWidth() {
        return this.windowWidth;
    }

    public int splitX() {
        int leftColumnWidth = Mth.clamp(this.windowWidth / 3, 136, 172);
        return this.windowLeft + leftColumnWidth;
    }

    public int rightPanelLeft() {
        return splitX() + 18;
    }

    public int rightPanelRight() {
        return this.windowLeft + this.windowWidth - 22;
    }

    public int rightPanelWidth() {
        return Math.max(1, rightPanelRight() - rightPanelLeft());
    }

    public int loadListWidth() {
        return splitListWidth();
    }

    public int loadPreviewLeft() {
        return rightPanelLeft() + loadListWidth() + 8;
    }

    public int queryListWidth() {
        return splitListWidth();
    }

    public int queryDetailLeft() {
        return rightPanelLeft() + queryListWidth() + 8;
    }

    public int footerPrevButtonX() {
        return rightPanelLeft();
    }

    public int footerNextButtonX() {
        return rightPanelRight() - PAGE_BUTTON_WIDTH;
    }

    public int footerButtonY() {
        return this.windowTop + Math.min(FOOTER_BUTTON_Y, this.windowHeight - 42);
    }

    public int footerTextY() {
        return this.windowTop + Math.min(FOOTER_TEXT_Y, this.windowHeight - 37);
    }

    public int footerMetaCenterX() {
        return (rightPanelLeft() + footerPrevButtonX() + PAGE_BUTTON_WIDTH) / 2;
    }

    public int footerPageCenterX() {
        return (footerPrevButtonX() + PAGE_BUTTON_WIDTH + footerNextButtonX()) / 2;
    }

    public boolean containsLoadPreview(double mouseX, double mouseY) {
        return mouseX >= loadPreviewLeft() && mouseX <= rightPanelRight()
                && mouseY >= this.windowTop + 50 && mouseY <= this.windowTop + 176;
    }

    public boolean containsQueryPreview(double mouseX, double mouseY) {
        int detailLeft = queryDetailLeft();
        int detailWidth = rightPanelRight() - detailLeft;
        int previewLeft = detailWidth < 190 ? detailLeft + 10 : detailLeft + 126;
        int previewTop = detailWidth < 190 ? this.windowTop + 108 : this.windowTop + 62;
        return mouseX >= previewLeft && mouseX <= this.windowLeft + this.windowWidth - 30
                && mouseY >= previewTop && mouseY <= this.windowTop + 168;
    }

    private int splitListWidth() {
        int available = rightPanelWidth();
        int maxForList = Math.min(170, Math.max(64, available - 88));
        return Mth.clamp(Math.min(170, available / 2), 64, maxForList);
    }

    private static int calculateWindowWidth(int screenWidth, LeftTab tab) {
        int desired = switch (tab) {
            case QUERY -> QUERY_WINDOW_WIDTH;
            case LOAD -> LOAD_WINDOW_WIDTH;
            default -> NORMAL_WINDOW_WIDTH;
        };
        int available = Math.max(MIN_WINDOW_WIDTH, screenWidth - SCREEN_MARGIN * 2);
        return Mth.clamp(desired, MIN_WINDOW_WIDTH, available);
    }
}
