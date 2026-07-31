package com.enxv.aeronauticsstructuretool.client.screen.toolmode;

public final class ToolModeHitTest {
    private static final int FILES_PER_PAGE = ToolModeBlueprintBrowser.FILES_PER_PAGE;
    private static final int TOOLS_PER_PAGE = 8;

    private ToolModeHitTest() {
    }

    public static int leftTabIndex(
            ToolModeLayout layout,
            int tabCount,
            double mouseX,
            double mouseY
    ) {
        if (mouseX < layout.windowLeft() + 28 || mouseX > layout.splitX() - 18) {
            return -1;
        }
        for (int index = 0; index < tabCount; index++) {
            int rowTop = layout.windowTop() + 58 + index * 20;
            if (mouseY >= rowTop - 2 && mouseY <= rowTop + 12) {
                return index;
            }
        }
        return -1;
    }

    public static int blueprintIndex(
            ToolModeLayout layout,
            boolean showingLoad,
            int filteredCount,
            int page,
            double mouseX,
            double mouseY
    ) {
        if (!showingLoad || filteredCount == 0) {
            return -1;
        }
        int listLeft = layout.rightPanelLeft();
        int listRight = listLeft + layout.loadListWidth();
        if (mouseX < listLeft + 2 || mouseX > listRight - 4) {
            return -1;
        }
        int pageStart = page * FILES_PER_PAGE;
        int pageEnd = Math.min(pageStart + FILES_PER_PAGE, filteredCount);
        for (int index = pageStart; index < pageEnd; index++) {
            int rowTop = layout.windowTop() + 84 + (index - pageStart) * 16;
            if (mouseY >= rowTop - 2 && mouseY <= rowTop + 10) {
                return index;
            }
        }
        return -1;
    }

    public static int toolIndex(
            ToolModeLayout layout,
            boolean showingTools,
            int toolCount,
            int page,
            double mouseX,
            double mouseY
    ) {
        if (!showingTools || mouseX < layout.splitX() + 20 || mouseX > layout.rightPanelRight()) {
            return -1;
        }
        int pageStart = page * TOOLS_PER_PAGE;
        int pageEnd = Math.min(pageStart + TOOLS_PER_PAGE, toolCount);
        for (int index = pageStart; index < pageEnd; index++) {
            int rowTop = layout.windowTop() + 54 + (index - pageStart) * 15;
            if (mouseY >= rowTop - 2 && mouseY <= rowTop + 11) {
                return index;
            }
        }
        return -1;
    }

    public static int deleteOptionIndex(
            ToolModeLayout layout,
            boolean showingDelete,
            double mouseX,
            double mouseY
    ) {
        if (!showingDelete || mouseX < layout.splitX() + 20 || mouseX > layout.rightPanelRight()) {
            return -1;
        }
        int firstRowTop = layout.windowTop() + 56;
        if (mouseY >= firstRowTop - 2 && mouseY <= firstRowTop + 11) {
            return 0;
        }
        int secondRowTop = firstRowTop + 18;
        return mouseY >= secondRowTop - 2 && mouseY <= secondRowTop + 11 ? 1 : -1;
    }

    public static int vehicleIndex(
            ToolModeLayout layout,
            boolean showingQuery,
            int entryCount,
            int page,
            double mouseX,
            double mouseY
    ) {
        if (!showingQuery || entryCount == 0) {
            return -1;
        }
        int listLeft = layout.rightPanelLeft();
        int listRight = listLeft + layout.queryListWidth();
        if (mouseX < listLeft + 2 || mouseX > listRight - 4) {
            return -1;
        }
        int pageStart = page * FILES_PER_PAGE;
        int pageEnd = Math.min(pageStart + FILES_PER_PAGE, entryCount);
        for (int index = pageStart; index < pageEnd; index++) {
            int rowTop = layout.windowTop() + 82 + (index - pageStart) * 16;
            if (mouseY >= rowTop - 2 && mouseY <= rowTop + 10) {
                return index;
            }
        }
        return -1;
    }
}
