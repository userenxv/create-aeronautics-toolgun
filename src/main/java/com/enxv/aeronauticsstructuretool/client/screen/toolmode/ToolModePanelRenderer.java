package com.enxv.aeronauticsstructuretool.client.screen.toolmode;

import com.enxv.aeronauticsstructuretool.BlueprintListEntry;
import com.enxv.aeronauticsstructuretool.BlueprintSourceType;
import com.enxv.aeronauticsstructuretool.client.tool.ClientStructureToolHandler;
import com.enxv.aeronauticsstructuretool.ClientToolState;
import com.enxv.aeronauticsstructuretool.ConnectionMode;
import com.enxv.aeronauticsstructuretool.ToolMode;
import com.enxv.aeronauticsstructuretool.client.screen.NearbyVehicleQueryState;
import com.enxv.aeronauticsstructuretool.client.screen.StructurePreviewRenderer;
import com.enxv.aeronauticsstructuretool.client.screen.StructurePreviewViewState;
import com.enxv.aeronauticsstructuretool.client.screen.toolmode.ToolModeControls.InfiniteRangeToggle;
import com.enxv.aeronauticsstructuretool.client.screen.toolmode.ToolModeControls.RadarToggle;
import com.enxv.aeronauticsstructuretool.client.screen.toolmode.ToolModeControls.SearchField;
import com.enxv.aeronauticsstructuretool.client.screen.toolmode.ToolModeMenuModel.LeftTab;
import com.enxv.aeronauticsstructuretool.client.screen.toolmode.ToolModeMenuModel.ToolEntry;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.BLUEPRINT_BLUE;
import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.BRASS;
import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.BRASS_SOFT;
import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.PANEL;
import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.PANEL_DARK;
import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.PANEL_LIGHT;
import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.PANEL_MID;
import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.TEXT_MUTED;
import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.TEXT_PRIMARY;
import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.TEXT_ERROR;
import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.TEXT_WARN;
import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.drawPanel;
import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.drawSelectionRow;

public final class ToolModePanelRenderer {
    private static final int FILES_PER_PAGE = ToolModeBlueprintBrowser.FILES_PER_PAGE;
    private static final int TOOLS_PER_PAGE = 8;

    private final Font font;

    public ToolModePanelRenderer(Font font) {
        this.font = font;
    }

    public void renderFrame(
            GuiGraphics graphics,
            Component title,
            Component rightPanelTitle,
            ToolModeLayout layout,
            boolean showingQuery
    ) {
        int left = layout.windowLeft();
        int top = layout.windowTop();
        int right = left + layout.windowWidth();
        int bottom = top + layout.windowHeight();
        int split = layout.splitX();
        graphics.fill(left - 8, top - 8, right + 8, bottom + 8, 0x55000000);
        drawPanel(graphics, left, top, right, bottom, PANEL);
        drawPanel(graphics, left + 12, top + 24, split, bottom - 14, PANEL_LIGHT);
        drawPanel(graphics, split + 8, top + 24, right - 12, bottom - 14, PANEL_LIGHT);
        graphics.fill(left + 20, top + 50, split - 10, bottom - 24, PANEL_MID);
        graphics.fill(split + 18, top + 50, right - 22, bottom - 62, PANEL_MID);
        if (showingQuery) {
            graphics.fill(layout.queryDetailLeft(), top + 50, right - 22, bottom - 62, PANEL_LIGHT);
        }
        graphics.fill(split + 3, top + 24, split + 6, bottom - 14, BRASS_SOFT);
        graphics.drawString(this.font, title, left + 16, top + 10, TEXT_PRIMARY, false);
        graphics.drawString(this.font, Component.literal("TOOL CONTROL"), right - 108, top + 10, TEXT_MUTED, false);
        graphics.drawString(this.font, Component.translatable("screen.create_aeronautics_toolgun.mode"), left + 28, top + 34, BRASS, false);
        graphics.drawString(this.font, rightPanelTitle, split + 18, top + 32, BRASS, false);
    }

    public void renderLeftRows(
            GuiGraphics graphics,
            ToolModeLayout layout,
            LeftTab[] tabs,
            LeftTab selectedTab,
            int hoveredIndex
    ) {
        int left = layout.windowLeft();
        int top = layout.windowTop();
        int split = layout.splitX();
        for (int index = 0; index < tabs.length; index++) {
            int rowTop = top + 58 + index * 20;
            boolean selected = tabs[index] == selectedTab;
            drawSelectionRow(graphics, left + 28, split - 18, rowTop, selected, index == hoveredIndex);
            graphics.drawString(this.font, tabs[index].label(), left + 34, rowTop, selected ? TEXT_PRIMARY : TEXT_MUTED, false);
        }
        ToolMode mode = ClientToolState.getMode();
        if (!isInteractiveMode(mode)) {
            return;
        }
        graphics.drawString(this.font, Component.translatable("screen.create_aeronautics_toolgun.active_mode"), left + 28, top + 152, BRASS, false);
        graphics.drawString(this.font, mode.title(), left + 34, top + 166, TEXT_PRIMARY, false);
        Component detail = activeModeDetail(mode);
        if (detail != null) {
            graphics.drawString(this.font, detail, left + 34, top + 178, TEXT_MUTED, false);
        }
    }

    public void renderHint(GuiGraphics graphics, ToolModeLayout layout, Component hint) {
        ToolMode mode = ClientToolState.getMode();
        boolean compact = isInteractiveMode(mode);
        int hintX = layout.windowLeft() + 22;
        int hintWidth = Math.max(40, layout.splitX() - hintX - 12);
        List<FormattedCharSequence> lines = new ArrayList<>(this.font.split(hint, hintWidth));
        int hintY = layout.windowTop() + (compact ? 188 : 168);
        int limit = compact ? 2 : 4;
        for (int index = 0; index < Math.min(lines.size(), limit); index++) {
            graphics.drawString(
                    this.font,
                    lines.get(index),
                    hintX,
                    hintY + index * 11,
                    TEXT_WARN,
                    false
            );
        }
    }

    public void renderInfoPanel(GuiGraphics graphics, ToolModeLayout layout) {
        List<FormattedCharSequence> lines = this.font.split(
                Component.translatable("screen.create_aeronautics_toolgun.mode.save_detail"),
                166
        );
        int y = layout.windowTop() + 58;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(this.font, line, layout.splitX() + 22, y, TEXT_PRIMARY, false);
            y += 12;
        }
    }

    public void renderBlueprintPanel(
            GuiGraphics graphics,
            ToolModeLayout layout,
            ToolModeBlueprintBrowser browser,
            SearchField searchField,
            StructurePreviewViewState previewView,
            int hoveredFileIndex
    ) {
        int top = layout.windowTop();
        int right = layout.windowLeft() + layout.windowWidth();
        int listLeft = layout.rightPanelLeft();
        int listTop = top + 50;
        int listRight = listLeft + layout.loadListWidth();
        int previewLeft = layout.loadPreviewLeft();
        int previewRight = right - 22;
        searchField.setBounds(listLeft + 3, top + 56, Math.max(40, listRight - listLeft - 18), 16);
        drawPanel(graphics, listLeft + 2, top + 54, listRight - 12, top + 72, PANEL_DARK);
        searchField.render(graphics, this.font);
        if (searchField.getValue().isEmpty() && !searchField.isFocused()) {
            graphics.drawString(this.font, Component.translatable("screen.create_aeronautics_toolgun.search_short"), listLeft + 9, top + 59, TEXT_MUTED, false);
        }
        graphics.fill(listLeft, listTop, listRight, top + 176, PANEL_DARK);
        graphics.fill(previewLeft, listTop, previewRight, top + 176, PANEL_DARK);

        List<BlueprintListEntry> filtered = browser.filteredFiles(searchField.getValue());
        int pageStart = browser.page() * FILES_PER_PAGE;
        int pageEnd = Math.min(pageStart + FILES_PER_PAGE, filtered.size());
        if (filtered.isEmpty()) {
            Component empty = searchField.getValue().isBlank()
                    ? Component.translatable("screen.create_aeronautics_toolgun.no_files")
                    : Component.translatable("screen.create_aeronautics_toolgun.search_empty");
            graphics.drawString(this.font, empty, listLeft + 12, top + 88, TEXT_MUTED, false);
        } else {
            BlueprintListEntry selectedEntry = browser.selectedEntry();
            for (int index = pageStart; index < pageEnd; index++) {
                int rowTop = top + 84 + (index - pageStart) * 16;
                BlueprintListEntry entry = filtered.get(index);
                boolean selected = selectedEntry != null
                        && selectedEntry.selectionKey().equals(entry.selectionKey());
                drawSelectionRow(graphics, listLeft + 2, listRight - 4, rowTop, selected, index == hoveredFileIndex);
                int nameColor = selected
                        ? TEXT_PRIMARY
                        : entry.sourceType() == BlueprintSourceType.CREATE_PHYSICAL ? 0xFFCFEAFF : TEXT_MUTED;
                int badgeWidth = entry.sourceType() == BlueprintSourceType.CREATE_PHYSICAL || entry.isImported() ? 36 : 0;
                graphics.drawString(
                        this.font,
                        this.font.plainSubstrByWidth(entry.displayName(), Math.max(40, listRight - listLeft - 28 - badgeWidth)),
                        listLeft + 12,
                        rowTop,
                        nameColor,
                        false
                );
                if (entry.sourceType() == BlueprintSourceType.CREATE_PHYSICAL) {
                    graphics.drawString(this.font, "NBT", listRight - 38, rowTop + 1, BLUEPRINT_BLUE, false);
                }
                if (entry.isImported()) {
                    graphics.fill(Math.max(listLeft + 60, listRight - 76), rowTop - 1, listRight - 4, rowTop + 9, 0x664D220D);
                    graphics.drawString(this.font, Component.translatable("screen.create_aeronautics_toolgun.import_warning_badge"), Math.max(listLeft + 64, listRight - 62), rowTop + 1, TEXT_WARN, false);
                }
            }
        }
        renderBlueprintPreview(graphics, browser, previewView, previewLeft, top + 50, previewRight - previewLeft, 126);
        graphics.drawCenteredString(
                this.font,
                Component.literal((browser.page() + 1) + " / " + browser.pageCount(searchField.getValue())),
                layout.footerPageCenterX(),
                layout.footerTextY(),
                TEXT_MUTED
        );
    }

    public void renderDeletePanel(GuiGraphics graphics, ToolModeLayout layout, int hoveredIndex) {
        int top = layout.windowTop();
        int split = layout.splitX();
        int right = layout.windowLeft() + layout.windowWidth();
        int firstRowTop = top + 56;
        drawSelectionRow(graphics, split + 20, right - 24, firstRowTop, false, hoveredIndex == 0);
        graphics.drawString(this.font, Component.translatable("screen.create_aeronautics_toolgun.delete.range_toggle"), split + 26, firstRowTop, TEXT_PRIMARY, false);
        graphics.drawString(this.font, ToolModeMenuModel.toggleValue(ClientToolState.isRangeDeleteEnabled()), split + 150, firstRowTop, hoveredIndex == 0 ? TEXT_PRIMARY : TEXT_MUTED, false);

        int secondRowTop = firstRowTop + 18;
        drawSelectionRow(graphics, split + 20, right - 24, secondRowTop, false, hoveredIndex == 1);
        graphics.drawString(this.font, Component.translatable("screen.create_aeronautics_toolgun.delete.range_value"), split + 26, secondRowTop, TEXT_PRIMARY, false);
        graphics.drawString(this.font, ClientToolState.getDeleteRange() + "b", split + 150, secondRowTop, hoveredIndex == 1 ? TEXT_PRIMARY : TEXT_MUTED, false);
        graphics.drawString(this.font, Component.translatable("screen.create_aeronautics_toolgun.delete.range_scroll_hint"), split + 26, secondRowTop + 10, TEXT_MUTED, false);

        List<FormattedCharSequence> lines = this.font.split(Component.translatable("screen.create_aeronautics_toolgun.mode.delete_detail"), 166);
        int y = top + 118;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(this.font, line, split + 22, y, TEXT_MUTED, false);
            y += 12;
        }
    }

    public void renderVehiclePanel(
            GuiGraphics graphics,
            ToolModeLayout layout,
            ToolModeVehicleQueryController query,
            StructurePreviewViewState previewView,
            InfiniteRangeToggle infiniteToggle,
            RadarToggle radarToggle,
            ToolModeVehicleRadar radar,
            boolean radarView,
            boolean survivalRestricted,
            int hoveredVehicleIndex,
            LocalPlayer player,
            int radarRange,
            double mouseX,
            double mouseY
    ) {
        int top = layout.windowTop();
        int split = layout.splitX();
        int right = layout.windowLeft() + layout.windowWidth();
        int listLeft = layout.rightPanelLeft();
        int listRight = listLeft + layout.queryListWidth();
        int detailLeft = layout.queryDetailLeft();
        int detailRight = right - 22;
        graphics.drawString(this.font, Component.translatable("screen.create_aeronautics_toolgun.query.range"), split + 22, top + 44, BRASS, false);
        graphics.drawString(this.font, Component.translatable("screen.create_aeronautics_toolgun.query.count", query.entries().size()), listLeft + Math.max(82, layout.queryListWidth() - 62), top + 44, TEXT_MUTED, false);
        graphics.fill(split + 22, top + 54, split + 98, top + 72, PANEL_DARK);
        graphics.fill(split + 22, top + 54, split + 98, top + 55, BRASS_SOFT);
        graphics.fill(split + 22, top + 71, split + 98, top + 72, PANEL);
        if (survivalRestricted
                || ClientToolState.nearbyQueryRangeForTool(false) != ClientToolState.INFINITE_NEARBY_QUERY_RANGE) {
            graphics.drawString(this.font, Component.translatable("screen.create_aeronautics_toolgun.query.blocks"), split + 104, top + 59, TEXT_MUTED, false);
        }
        graphics.fill(detailLeft, top + 54, detailRight, top + 176, PANEL_DARK);

        if (radarView) {
            radar.render(
                    graphics,
                    this.font,
                    layout,
                    query.entries(),
                    query.state().selectedId(),
                    hoveredVehicleIndex,
                    player,
                    radarRange
            );
        } else {
            renderVehicleList(graphics, layout, query, hoveredVehicleIndex, listLeft, listRight, top);
        }
        renderSelectedVehicle(graphics, query, previewView, detailLeft, top + 54, detailRight - detailLeft, 122);
        infiniteToggle.render(graphics, this.font, mouseX, mouseY);
        radarToggle.render(graphics, this.font, mouseX, mouseY);
        if (!radarView) {
            graphics.drawCenteredString(this.font, Component.literal((query.page() + 1) + " / " + query.pageCount()), layout.footerPageCenterX(), layout.footerTextY(), TEXT_MUTED);
        }
    }

    private void renderVehicleList(
            GuiGraphics graphics,
            ToolModeLayout layout,
            ToolModeVehicleQueryController query,
            int hoveredVehicleIndex,
            int listLeft,
            int listRight,
            int top
    ) {
        graphics.fill(listLeft, top + 78, listRight, top + 176, PANEL_DARK);
        int pageStart = query.page() * FILES_PER_PAGE;
        int pageEnd = Math.min(pageStart + FILES_PER_PAGE, query.entries().size());
        if (query.entries().isEmpty()) {
            graphics.drawString(this.font, Component.translatable("screen.create_aeronautics_toolgun.query.none"), listLeft + 8, top + 86, TEXT_MUTED, false);
            return;
        }
        for (int index = pageStart; index < pageEnd; index++) {
            int rowTop = top + 82 + (index - pageStart) * 16;
            NearbyVehicleQueryState.Entry entry = query.entries().get(index);
            boolean selected = query.isSelected(entry);
            drawSelectionRow(graphics, listLeft + 2, listRight - 4, rowTop, selected, index == hoveredVehicleIndex);
            int summaryWidth = Math.min(64, Math.max(0, layout.queryListWidth() - 104));
            int rowColor = entry.broken() ? TEXT_ERROR : selected ? TEXT_PRIMARY : TEXT_MUTED;
            graphics.drawString(this.font, this.font.plainSubstrByWidth(entry.displayName(), Math.max(40, layout.queryListWidth() - summaryWidth - 30)), listLeft + 8, rowTop, rowColor, false);
            if (summaryWidth > 0) {
                graphics.drawString(this.font, this.font.plainSubstrByWidth(entry.summary(), summaryWidth), listRight - summaryWidth - 6, rowTop, rowColor, false);
            }
        }
    }

    public void renderToolPanel(
            GuiGraphics graphics,
            ToolModeLayout layout,
            ToolEntry[] tools,
            int page,
            int hoveredToolIndex
    ) {
        int top = layout.windowTop();
        int split = layout.splitX();
        int right = layout.windowLeft() + layout.windowWidth();
        int pageStart = page * TOOLS_PER_PAGE;
        int pageEnd = Math.min(pageStart + TOOLS_PER_PAGE, tools.length);
        for (int index = pageStart; index < pageEnd; index++) {
            int rowTop = top + 54 + (index - pageStart) * 15;
            boolean hovered = index == hoveredToolIndex;
            drawSelectionRow(graphics, split + 20, right - 24, rowTop, false, hovered);
            ToolEntry entry = tools[index];
            String label = this.font.plainSubstrByWidth(entry.label(), 118);
            if (entry == ToolEntry.SIMPLE_WELD_MODE) {
                int accent = hovered ? 0xFFFFF6DE : 0xFFF8EFD1;
                graphics.drawString(this.font, Component.literal(label).withStyle(ChatFormatting.BOLD), split + 26, rowTop, accent, false);
            } else if (entry == ToolEntry.GHOST_VEHICLE_TEST) {
                graphics.drawString(
                        this.font,
                        Component.literal(label).withStyle(ChatFormatting.BOLD),
                        split + 26,
                        rowTop,
                        TEXT_ERROR,
                        false
                );
            } else {
                graphics.drawString(this.font, label, split + 26, rowTop, TEXT_PRIMARY, false);
            }
            graphics.drawString(this.font, this.font.plainSubstrByWidth(ToolModeMenuModel.value(entry), 72), split + 150, rowTop, hovered ? TEXT_PRIMARY : TEXT_MUTED, false);
        }
        int pageCount = Math.max(1, (int) Math.ceil((double) tools.length / TOOLS_PER_PAGE));
        graphics.drawCenteredString(this.font, Component.literal((page + 1) + " / " + pageCount), layout.footerPageCenterX(), layout.footerTextY(), TEXT_MUTED);
    }

    private void renderBlueprintPreview(
            GuiGraphics graphics,
            ToolModeBlueprintBrowser browser,
            StructurePreviewViewState previewView,
            int x,
            int y,
            int width,
            int height
    ) {
        if (browser.preview().preview() != null && browser.preview().preview().hasPreview()) {
            StructurePreviewRenderer.render(
                    graphics,
                    x + 8,
                    y + 8,
                    width - 16,
                    height - 26,
                    browser.preview().preview(),
                    previewView.yaw(),
                    previewView.pitch(),
                    previewView.zoom(),
                    previewView.skippedBlockEntityTypes()
            );
        } else {
            Component message = browser.preview().loading()
                    ? Component.translatable("screen.create_aeronautics_toolgun.printer.preview_loading")
                    : browser.preview().error() != null
                    ? browser.preview().error()
                    : Component.translatable("screen.create_aeronautics_toolgun.printer.preview_empty");
            graphics.drawWordWrap(this.font, message, x + 10, y + 30, width - 20, TEXT_MUTED);
        }
        BlueprintListEntry selectedEntry = browser.selectedEntry();
        if (selectedEntry != null) {
            int nameColor = selectedEntry.sourceType() == BlueprintSourceType.CREATE_PHYSICAL
                    ? BLUEPRINT_BLUE
                    : TEXT_PRIMARY;
            graphics.drawString(this.font, this.font.plainSubstrByWidth(selectedEntry.displayName(), width - 20), x + 10, y + height - 14, nameColor, false);
        }
        graphics.drawString(this.font, Component.translatable("screen.create_aeronautics_toolgun.printer.preview_rotate"), x + 10, y + 10, TEXT_MUTED, false);
        graphics.drawString(this.font, Component.translatable("screen.create_aeronautics_toolgun.printer.preview_zoom"), x + 10, y + 20, TEXT_MUTED, false);
    }

    private void renderSelectedVehicle(
            GuiGraphics graphics,
            ToolModeVehicleQueryController query,
            StructurePreviewViewState previewView,
            int x,
            int y,
            int width,
            int height
    ) {
        NearbyVehicleQueryState.Entry selected = query.selectedEntry();
        if (selected == null) {
            graphics.drawWordWrap(this.font, Component.translatable("screen.create_aeronautics_toolgun.query.select_hint"), x + 10, y + 16, width - 20, TEXT_MUTED);
            return;
        }
        graphics.drawString(this.font, Component.translatable("screen.create_aeronautics_toolgun.query.selected"), x + 10, y + 10, BRASS, false);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(selected.positionText(), Math.max(40, width - 20)), x + 10, y + 28, TEXT_MUTED, false);
        graphics.drawString(this.font, Component.literal(String.format(Locale.ROOT, "%.1fb", selected.distance())), x + 10, y + 40, TEXT_MUTED, false);
        boolean narrow = width < 190;
        int previewLeft = narrow ? x + 10 : x + 126;
        int previewTop = narrow ? y + 54 : y + 8;
        int previewWidth = Math.max(24, narrow ? width - 20 : width - 136);
        int previewHeight = Math.max(24, narrow ? height - 62 : height - 16);
        graphics.fill(previewLeft, previewTop, previewLeft + previewWidth, previewTop + previewHeight, PANEL_DARK);
        if (selected.broken()) {
            graphics.drawString(
                    this.font,
                    Component.translatable("screen.create_aeronautics_toolgun.query.broken_title"),
                    previewLeft + 6,
                    previewTop + 8,
                    TEXT_ERROR,
                    false
            );
            List<FormattedCharSequence> brokenLines = this.font.split(
                    Component.translatable("screen.create_aeronautics_toolgun.query.broken_detail"),
                    Math.max(20, previewWidth - 12)
            );
            int maxBrokenLines = Math.max(1, (previewHeight - 26) / 9);
            for (int index = 0; index < Math.min(brokenLines.size(), maxBrokenLines); index++) {
                graphics.drawString(
                        this.font,
                        brokenLines.get(index),
                        previewLeft + 6,
                        previewTop + 22 + index * 9,
                        TEXT_ERROR,
                        false
                );
            }
        } else if (query.state().preview() != null && query.state().preview().hasPreview()) {
            StructurePreviewRenderer.render(
                    graphics,
                    previewLeft + 2,
                    previewTop + 2,
                    previewWidth - 4,
                    previewHeight - 4,
                    query.state().preview(),
                    previewView.yaw(),
                    previewView.pitch(),
                    previewView.zoom(),
                    previewView.skippedBlockEntityTypes()
            );
        } else {
            Component message = query.state().previewError() != null
                    ? query.state().previewError()
                    : Component.translatable("screen.create_aeronautics_toolgun.printer.preview_empty");
            graphics.drawWordWrap(this.font, message, previewLeft + 6, previewTop + 12, previewWidth - 12, TEXT_MUTED);
        }
        if (!selected.loaded() && !selected.broken()) {
            List<FormattedCharSequence> lines = this.font.split(
                    Component.translatable("screen.create_aeronautics_toolgun.query.unloaded_hint"),
                    previewWidth - 12
            );
            int hintHeight = lines.size() * 9 + 6;
            graphics.fill(previewLeft + 2, previewTop + 2, previewLeft + previewWidth - 2, previewTop + 2 + hintHeight, 0xDD100D0A);
            int lineY = previewTop + 5;
            for (FormattedCharSequence line : lines) {
                graphics.drawString(this.font, line, previewLeft + 6, lineY, TEXT_WARN, false);
                lineY += 9;
            }
        }
    }

    private static boolean isInteractiveMode(ToolMode mode) {
        return mode == ToolMode.WELD
                || mode == ToolMode.SIMPLE_WELD
                || mode == ToolMode.DISCONNECT
                || mode == ToolMode.ROTATE
                || mode == ToolMode.TRANSLATE;
    }

    private static Component activeModeDetail(ToolMode mode) {
        return switch (mode) {
            case WELD -> weldModeLabel();
            case SIMPLE_WELD -> Component.literal(
                    ClientStructureToolHandler.getPendingSimpleWeldAdjustMode().title().getString()
                            + " | "
                            + ClientStructureToolHandler.getPendingSimpleWeldAxis().title().getString()
            );
            case TRANSLATE -> Component.literal(
                    ToolModeMenuModel.stepValue(ClientToolState.getTranslateStep())
                            + " | "
                            + ClientStructureToolHandler.getPendingTranslateAxis().title().getString()
            );
            case ROTATE -> Component.literal(
                    ClientToolState.getRotationStep()
                            + "\u00B0 | "
                            + ClientStructureToolHandler.getPendingRotateAxis().title().getString()
            );
            default -> null;
        };
    }

    private static Component weldModeLabel() {
        if (ClientToolState.getConnectionMode() != ConnectionMode.BEARING) {
            return ClientToolState.getConnectionMode().title();
        }
        return Component.translatable(
                "screen.create_aeronautics_toolgun.weld_mode_with_axis",
                ClientToolState.getConnectionMode().title(),
                ClientToolState.getBearingAxisMode().title()
        );
    }
}
