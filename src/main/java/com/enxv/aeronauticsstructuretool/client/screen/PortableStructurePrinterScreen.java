package com.enxv.aeronauticsstructuretool.client.screen;

import com.enxv.aeronauticsstructuretool.BlueprintListEntry;
import com.enxv.aeronauticsstructuretool.BlueprintSourceType;
import com.enxv.aeronauticsstructuretool.OpenPortableStructurePrinterPayload;
import com.enxv.aeronauticsstructuretool.PortableStructurePreviewData;
import com.enxv.aeronauticsstructuretool.PrintPortableStructurePrinterPayload;
import com.enxv.aeronauticsstructuretool.RequestPortableStructurePrinterStatePayload;
import com.enxv.aeronauticsstructuretool.SelectPortableStructurePrinterBlueprintPayload;
import com.enxv.aeronauticsstructuretool.SyncPortableStructurePrinterStatePayload;
import com.enxv.aeronauticsstructuretool.UsePortableStructurePrinterChecklistSlotPayload;
import com.enxv.aeronauticsstructuretool.blueprint.storage.ClientBlueprintCatalog;
import com.enxv.aeronauticsstructuretool.client.render.PortableStructurePrinterClientEffects;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.BLUEPRINT_BLUE;
import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.BRASS;
import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.BRASS_SOFT;
import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.PANEL;
import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.PANEL_DARK;
import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.PANEL_LIGHT;
import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.PANEL_MID;
import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.TEXT_MUTED;
import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.TEXT_PRIMARY;
import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.TEXT_WARN;
import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.drawPanel;

public final class PortableStructurePrinterScreen extends Screen {
    private static final int PANEL_WIDTH = 386;
    private static final int PANEL_HEIGHT = 246;
    private static final int ENTRY_HEIGHT = 18;
    private static final int MATERIAL_LINE_HEIGHT = 10;
    private static final int TEXT_SUCCESS = 0xFFBEE8B9;
    private static final int BAR_BG = 0xCC15110D;
    private static final int BAR_FILL_MATERIAL = 0xFFD7B14A;
    private static final int BAR_FILL_PRINT = 0xFFB7D76A;

    private final net.minecraft.core.BlockPos printerPos;
    private final String initialDisplayName;
    private final boolean initialHasBlueprint;
    private int leftPos;
    private int topPos;
    private final List<BlueprintListEntry> entries = new ArrayList<>();
    private BlueprintListEntry selectedEntry;
    private PortableStructurePreviewData selectedPreview;
    private Component previewError;
    private final StructurePreviewViewState previewView = new StructurePreviewViewState();
    private int scrollOffset;
    private long lastClickTime;
    private String lastClickKey = "";
    private Page page = Page.LIST;
    private SyncPortableStructurePrinterStatePayload latestState;
    private Button backButton;
    private Button printButton;
    private Button refreshButton;
    private static final int CHECKLIST_SLOT_X = 320;
    private static final int CHECKLIST_SLOT_Y = 52;

    private PortableStructurePrinterScreen(net.minecraft.core.BlockPos printerPos, String initialDisplayName, boolean initialHasBlueprint) {
        super(Component.translatable("screen.create_aeronautics_toolgun.printer.title"));
        this.printerPos = printerPos;
        this.initialDisplayName = initialDisplayName == null ? "" : initialDisplayName;
        this.initialHasBlueprint = initialHasBlueprint;
    }

    @Override
    public void removed() {
        StructurePreviewRenderer.clearCache();
        super.removed();
    }

    public static void open(OpenPortableStructurePrinterPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        PortableStructurePrinterScreen screen = new PortableStructurePrinterScreen(payload.printerPos(), payload.displayName(), payload.hasBlueprint());
        minecraft.setScreen(screen);
    }

    public static void receiveState(SyncPortableStructurePrinterStatePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.screen instanceof PortableStructurePrinterScreen screen && screen.printerPos.equals(payload.printerPos())) {
            if (!payload.printing()) {
                PortableStructurePrinterClientEffects.clear(payload.printerPos());
            }
            screen.latestState = payload;
            screen.restoreSelectionFromState(payload);
            screen.updateButtons();
        }
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - PANEL_WIDTH) / 2;
        this.topPos = (this.height - PANEL_HEIGHT) / 2;
        this.backButton = this.addRenderableWidget(new FlatButton(this.leftPos + 16, this.topPos + PANEL_HEIGHT - 26, 52, 20, Component.translatable("gui.back"), button -> {
            if (this.page == Page.MATERIALS) {
                this.page = Page.LIST;
                updateButtons();
            } else {
                this.onClose();
            }
        }));
        this.refreshButton = this.addRenderableWidget(new FlatButton(
                this.leftPos + PANEL_WIDTH - 126,
                this.topPos + PANEL_HEIGHT - 26,
                52,
                20,
                Component.translatable("screen.create_aeronautics_toolgun.printer.refresh"),
                button -> requestPrinterState()
        ));
        this.printButton = this.addRenderableWidget(new FlatButton(
                this.leftPos + PANEL_WIDTH - 68,
                this.topPos + PANEL_HEIGHT - 26,
                52,
                20,
                Component.translatable("screen.create_aeronautics_toolgun.printer.print"),
                button -> {
            PacketDistributor.sendToServer(new PrintPortableStructurePrinterPayload(this.printerPos));
            requestPrinterState();
        }));
        reloadEntries();
        if (this.initialHasBlueprint && this.selectedEntry != null) {
            this.page = Page.MATERIALS;
        }
        requestPrinterState();
        updateButtons();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0x78101218);
        drawPanel(guiGraphics, this.leftPos, this.topPos, this.leftPos + PANEL_WIDTH, this.topPos + PANEL_HEIGHT, PANEL);
        guiGraphics.fill(this.leftPos + 12, this.topPos + 28, this.leftPos + PANEL_WIDTH - 12, this.topPos + 29, PANEL_DARK);
        guiGraphics.drawString(this.font, this.title, this.leftPos + 14, this.topPos + 12, TEXT_PRIMARY, false);

        if (this.page == Page.LIST) {
            drawBlueprintList(guiGraphics, mouseX, mouseY);
            drawPreviewPanel(guiGraphics, mouseX, mouseY);
        } else {
            drawMaterialPage(guiGraphics, mouseX, mouseY);
        }
        if (this.backButton != null) {
            this.backButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (this.refreshButton != null && this.refreshButton.visible) {
            this.refreshButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (this.printButton != null && this.printButton.visible) {
            this.printButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    private void drawBlueprintList(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int listX = this.leftPos + 12;
        int listY = this.topPos + 34;
        int listWidth = 172;
        int listHeight = 176;
        drawPanel(guiGraphics, listX, listY, listX + listWidth, listY + listHeight, PANEL_MID);
        guiGraphics.drawString(this.font, Component.translatable("screen.create_aeronautics_toolgun.printer.folder"), listX + 8, listY + 8, TEXT_MUTED, false);
        List<BlueprintListEntry> visibleEntries = visibleEntries();
        for (int i = 0; i < visibleEntries.size(); i++) {
            BlueprintListEntry entry = visibleEntries.get(i);
            int entryY = listY + 26 + i * ENTRY_HEIGHT;
            boolean selected = this.selectedEntry != null && this.selectedEntry.selectionKey().equals(entry.selectionKey());
            guiGraphics.fill(listX + 6, entryY - 2, listX + listWidth - 6, entryY + 14, selected ? 0x88483218 : 0x44201812);
            if (selected) {
                guiGraphics.fill(listX + 6, entryY - 2, listX + listWidth - 6, entryY - 1, BRASS_SOFT);
                guiGraphics.fill(listX + 6, entryY + 13, listX + listWidth - 6, entryY + 14, PANEL_DARK);
                if (entry.sourceType() == BlueprintSourceType.CREATE_PHYSICAL) {
                    guiGraphics.drawString(this.font, "NBT", listX + listWidth - 23, entryY + 3, BLUEPRINT_BLUE, false);
                }
            }
            int color = selected ? TEXT_PRIMARY : entry.sourceType() == BlueprintSourceType.CREATE_PHYSICAL ? 0xFFCFEAFF : TEXT_MUTED;
            drawTrimmed(guiGraphics, entry.displayName(), listX + 16, entryY + 1, listWidth - 32, color);
        }
        if (visibleEntries.isEmpty()) {
            guiGraphics.drawWordWrap(this.font, Component.translatable("screen.create_aeronautics_toolgun.printer.no_files"), listX + 10, listY + 42, listWidth - 20, TEXT_MUTED);
        }
        if (isPointInside(listX, listY, listWidth, listHeight, mouseX, mouseY)) {
            guiGraphics.renderComponentTooltip(this.font, List.of(
                    Component.translatable("screen.create_aeronautics_toolgun.printer.double_click"),
                    Component.translatable("screen.create_aeronautics_toolgun.printer.select_hint").withStyle(ChatFormatting.GRAY)
            ), mouseX, mouseY);
        }
    }

    private void drawPreviewPanel(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int panelX = this.leftPos + 196;
        int panelY = this.topPos + 34;
        int panelWidth = 178;
        int panelHeight = 176;
        drawPanel(guiGraphics, panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL_MID);
        guiGraphics.drawString(this.font, Component.translatable("screen.create_aeronautics_toolgun.printer.preview"), panelX + 8, panelY + 8, TEXT_MUTED, false);

        if (this.selectedPreview != null && this.selectedPreview.hasPreview()) {
            StructurePreviewRenderer.render(
                    guiGraphics,
                    panelX + 10,
                    panelY + 24,
                    panelWidth - 20,
                    panelHeight - 36,
                    this.selectedPreview,
                    this.previewView.yaw(),
                    this.previewView.pitch(),
                    this.previewView.zoom(),
                    this.previewView.skippedBlockEntityTypes()
            );
        } else {
            Component message = this.previewError != null
                    ? this.previewError
                    : Component.translatable("screen.create_aeronautics_toolgun.printer.preview_empty");
            guiGraphics.drawWordWrap(this.font, message, panelX + 12, panelY + 56, panelWidth - 24, TEXT_MUTED);
        }

        if (this.selectedEntry != null) {
            int nameColor = this.selectedEntry.sourceType() == BlueprintSourceType.CREATE_PHYSICAL ? BLUEPRINT_BLUE : TEXT_PRIMARY;
            drawTrimmed(guiGraphics, this.selectedEntry.displayName(), panelX + 10, panelY + panelHeight - 18, panelWidth - 20, nameColor);
        }
        if (isPointInside(panelX, panelY, panelWidth, panelHeight, mouseX, mouseY)) {
            guiGraphics.renderComponentTooltip(this.font, List.of(
                    Component.translatable("screen.create_aeronautics_toolgun.printer.preview_rotate"),
                    Component.translatable("screen.create_aeronautics_toolgun.printer.preview_zoom").withStyle(ChatFormatting.GRAY)
            ), mouseX, mouseY);
        }
    }

    private void drawMaterialPage(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int x = this.leftPos + 12;
        int y = this.topPos + 34;
        int width = PANEL_WIDTH - 24;
        int height = 176;
        drawPanel(guiGraphics, x, y, x + width, y + height, PANEL_MID);
        Component title = this.selectedEntry != null
                ? Component.literal(this.selectedEntry.displayName())
                : Component.translatable("screen.create_aeronautics_toolgun.printer.no_selection");
        drawTrimmed(guiGraphics, title.getString(), x + 10, y + 10, width - 20, TEXT_PRIMARY);

        Component status = this.latestState == null
                ? Component.translatable("screen.create_aeronautics_toolgun.printer.status.loading")
                : Component.translatable(this.latestState.statusMessage());
        int statusColor = this.latestState != null && (this.latestState.ready() || this.latestState.printing()) ? TEXT_SUCCESS : TEXT_WARN;
        guiGraphics.drawString(this.font, status, x + 10, y + 28, statusColor, false);
        drawProgressBar(guiGraphics, x + 10, y + 42, width - 96, 10);
        drawChecklistSlot(guiGraphics, mouseX, mouseY, x + CHECKLIST_SLOT_X, y + CHECKLIST_SLOT_Y);

        int listX = x + 10;
        int listY = y + 60;
        int listWidth = width - 120;
        int listHeight = height - 70;
        if (this.latestState != null && this.latestState.printing()) {
            guiGraphics.drawWordWrap(this.font, Component.translatable("screen.create_aeronautics_toolgun.printer.printing_text"), listX, listY, listWidth, TEXT_SUCCESS);
            return;
        }
        if (this.latestState != null && this.latestState.ready()) {
            guiGraphics.drawWordWrap(this.font, Component.translatable("screen.create_aeronautics_toolgun.printer.ready_text"), listX, listY, listWidth, TEXT_SUCCESS);
            return;
        }
        if (this.latestState == null) {
            return;
        }
        if (this.latestState.requiredItems().isEmpty()) {
            guiGraphics.drawWordWrap(this.font, Component.translatable("screen.create_aeronautics_toolgun.printer.status.invalid"), listX, listY, listWidth, TEXT_WARN);
            return;
        }
        if (this.latestState.missingItems().isEmpty()) {
            guiGraphics.drawWordWrap(this.font, Component.translatable("screen.create_aeronautics_toolgun.printer.all_ready"), listX, listY, listWidth, TEXT_SUCCESS);
            return;
        }

        guiGraphics.drawString(this.font, Component.translatable("screen.create_aeronautics_toolgun.printer.missing"), listX, listY, TEXT_WARN, false);
        int contentTop = listY + 14;
        int columnCount = 4;
        int columnGap = 6;
        int columnWidth = (listWidth - columnGap * (columnCount - 1)) / columnCount;
        int footerHeight = MATERIAL_LINE_HEIGHT + 2;
        int rowsPerColumn = Math.max(1, (listHeight - 16 - footerHeight) / MATERIAL_LINE_HEIGHT);
        int displayed = 0;
        int total = this.latestState.missingItems().size();
        for (Map.Entry<String, Long> entry : this.latestState.missingItems().entrySet()) {
            ItemName itemName = resolveItemName(entry.getKey());
            String text = itemName.name + " x" + entry.getValue();
            int column = displayed / rowsPerColumn;
            if (column >= columnCount) {
                break;
            }
            int row = displayed % rowsPerColumn;
            int lineX = listX + column * (columnWidth + columnGap);
            int lineY = contentTop + row * MATERIAL_LINE_HEIGHT;
            drawTrimmed(guiGraphics, text, lineX, lineY, columnWidth, TEXT_PRIMARY);
            displayed++;
        }

        int hidden = Math.max(0, total - displayed);
        if (hidden > 0) {
            guiGraphics.drawString(
                    this.font,
                    Component.translatable("screen.create_aeronautics_toolgun.printer.more_missing", hidden),
                    listX,
                    listY + listHeight - MATERIAL_LINE_HEIGHT - 2,
                    0xB6AE9E,
                    false
            );
        }
    }

    private void drawChecklistSlot(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y) {
        guiGraphics.drawString(this.font, Component.translatable("screen.create_aeronautics_toolgun.printer.checklist"), x - 2, y - 12, TEXT_MUTED, false);
        drawPanel(guiGraphics, x, y, x + 18, y + 18, PANEL_DARK);
        guiGraphics.fill(x + 1, y + 1, x + 17, y + 17, 0xEE1A1712);
        ItemStack checklistStack = this.latestState == null ? ItemStack.EMPTY : this.latestState.checklistStack();
        if (!checklistStack.isEmpty()) {
            guiGraphics.renderItem(checklistStack, x + 1, y + 1);
            guiGraphics.renderItemDecorations(this.font, checklistStack, x + 1, y + 1);
        }
        if (isPointInside(x, y, 18, 18, mouseX, mouseY)) {
            List<Component> tooltip = new ArrayList<>();
            if (!checklistStack.isEmpty()) {
                tooltip.add(checklistStack.getHoverName());
                tooltip.add(Component.translatable("screen.create_aeronautics_toolgun.printer.checklist_take").withStyle(ChatFormatting.GRAY));
            } else {
                tooltip.add(Component.translatable("screen.create_aeronautics_toolgun.printer.checklist_hint"));
                tooltip.add(Component.translatable("screen.create_aeronautics_toolgun.printer.checklist_hint_2").withStyle(ChatFormatting.GRAY));
            }
            guiGraphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
        }
    }

    private void drawProgressBar(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        drawPanel(guiGraphics, x, y, x + width, y + height, BAR_BG);
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xEE120F0B);
        if (this.latestState == null) {
            return;
        }
        int innerWidth = width - 2;
        int materialWidth = Math.round(innerWidth * Mth.clamp(this.latestState.materialProgress(), 0.0F, 1.0F));
        if (materialWidth > 0) {
            guiGraphics.fill(x + 1, y + 1, x + 1 + materialWidth, y + height - 1, BAR_FILL_MATERIAL);
        }
        if (this.latestState.printing()) {
            int printWidth = Math.round(innerWidth * Mth.clamp(this.latestState.printProgress(), 0.0F, 1.0F));
            if (printWidth > 0) {
                guiGraphics.fill(x + 1, y + 1, x + 1 + printWidth, y + height - 1, BAR_FILL_PRINT);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (this.page == Page.LIST) {
            int listX = this.leftPos + 12;
            int listY = this.topPos + 34;
            if (isPointInside(listX, listY, 172, 176, mouseX, mouseY) && button == 0) {
                int relativeY = (int) mouseY - (listY + 26);
                int index = relativeY / ENTRY_HEIGHT;
                List<BlueprintListEntry> visible = visibleEntries();
                if (index >= 0 && index < visible.size()) {
                    BlueprintListEntry clicked = visible.get(index);
                    selectEntry(clicked);
                    long now = System.currentTimeMillis();
                    if (clicked.selectionKey().equals(this.lastClickKey) && now - this.lastClickTime <= 350L) {
                        enterMaterialPage();
                    }
                    this.lastClickKey = clicked.selectionKey();
                    this.lastClickTime = now;
                    return true;
                }
            }
            if (isPointInside(this.leftPos + 196, this.topPos + 34, 178, 176, mouseX, mouseY) && button == 0) {
                this.previewView.beginDrag();
                return true;
            }
        }
        if (this.page == Page.MATERIALS) {
            int slotX = this.leftPos + 12 + CHECKLIST_SLOT_X;
            int slotY = this.topPos + 34 + CHECKLIST_SLOT_Y;
            if (isPointInside(slotX, slotY, 18, 18, mouseX, mouseY) && button == 0) {
                PacketDistributor.sendToServer(new UsePortableStructurePrinterChecklistSlotPayload(this.printerPos));
                requestPrinterState();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.previewView.endDrag();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.previewView.drag(dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isPointInside(this.leftPos + 12, this.topPos + 34, 172, 176, mouseX, mouseY)) {
            int maxOffset = Math.max(0, this.entries.size() - 8);
            this.scrollOffset = Mth.clamp(this.scrollOffset - (int) Math.signum(scrollY), 0, maxOffset);
            return true;
        }
        if (isPointInside(this.leftPos + 196, this.topPos + 34, 178, 176, mouseX, mouseY)) {
            this.previewView.zoom(scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void reloadEntries() {
        this.entries.clear();
        this.entries.addAll(ClientBlueprintCatalog.listEntries());
        if (this.selectedEntry == null && !this.initialDisplayName.isBlank()) {
            for (BlueprintListEntry entry : this.entries) {
                if (entry.displayName().equals(this.initialDisplayName) || entry.fileName().equals(this.initialDisplayName)) {
                    this.selectedEntry = entry;
                    break;
                }
            }
        }
        if (this.selectedEntry == null && !this.entries.isEmpty()) {
            this.selectedEntry = this.entries.getFirst();
        }
        if (this.latestState != null) {
            restoreSelectionFromState(this.latestState);
        }
        if (this.selectedEntry != null) {
            loadPreview(this.selectedEntry);
        }
    }

    private void restoreSelectionFromState(SyncPortableStructurePrinterStatePayload payload) {
        if (payload == null || this.entries.isEmpty()) {
            return;
        }
        BlueprintListEntry matched = null;
        for (BlueprintListEntry entry : this.entries) {
            if (!payload.blueprintName().isBlank() && entry.fileName().equals(payload.blueprintName())) {
                matched = entry;
                break;
            }
            if (!payload.displayName().isBlank() && entry.displayName().equals(payload.displayName())) {
                matched = entry;
            }
        }
        if (matched == null) {
            return;
        }
        boolean changed = this.selectedEntry == null || !this.selectedEntry.selectionKey().equals(matched.selectionKey());
        this.selectedEntry = matched;
        if (changed) {
            loadPreview(matched);
        }
        if (payload.hasBlueprint() && this.page == Page.LIST && this.initialHasBlueprint) {
            this.page = Page.MATERIALS;
        }
    }

    private void selectEntry(BlueprintListEntry entry) {
        this.selectedEntry = entry;
        loadPreview(entry);
    }

    private void loadPreview(BlueprintListEntry entry) {
        this.selectedPreview = null;
        this.previewError = null;
        this.previewView.clearRendererFailures();
        if (entry == null || this.minecraft == null) {
            return;
        }
        try {
            this.selectedPreview = PortableStructurePreviewData.fromBlueprintBytes(entry.displayName(), ClientBlueprintCatalog.read(entry), this.minecraft.level);
        } catch (IOException exception) {
            this.previewError = Component.literal(exception.getMessage() == null ? "preview failed" : exception.getMessage());
        }
    }

    private void enterMaterialPage() {
        if (this.selectedEntry == null || this.selectedPreview == null) {
            return;
        }
        try {
            byte[] bytes = ClientBlueprintCatalog.read(this.selectedEntry);
            PacketDistributor.sendToServer(new SelectPortableStructurePrinterBlueprintPayload(
                    this.printerPos,
                    this.selectedEntry.displayName(),
                    this.selectedEntry.fileName(),
                    bytes,
                    this.selectedPreview.bottomY()
            ));
            this.page = Page.MATERIALS;
            this.latestState = null;
            updateButtons();
        } catch (IOException exception) {
            this.previewError = Component.literal(exception.getMessage() == null ? "load failed" : exception.getMessage());
        }
    }

    private void requestPrinterState() {
        PacketDistributor.sendToServer(new RequestPortableStructurePrinterStatePayload(this.printerPos));
    }

    private List<BlueprintListEntry> visibleEntries() {
        int from = Math.min(this.scrollOffset, this.entries.size());
        int to = Math.min(this.entries.size(), from + 8);
        return this.entries.subList(from, to);
    }

    private void updateButtons() {
        if (this.backButton != null) {
            this.backButton.setMessage(this.page == Page.MATERIALS ? Component.translatable("gui.back") : Component.translatable("screen.create_aeronautics_toolgun.close"));
        }
        if (this.printButton != null) {
            this.printButton.visible = this.page == Page.MATERIALS;
            this.printButton.active = this.latestState != null && this.latestState.ready() && !this.latestState.printing();
        }
        if (this.refreshButton != null) {
            this.refreshButton.visible = this.page == Page.MATERIALS;
        }
    }

    private static boolean isPointInside(int x, int y, int width, int height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void drawTrimmed(GuiGraphics guiGraphics, String text, int x, int y, int maxWidth, int color) {
        String trimmed = this.font.width(text) <= maxWidth
                ? text
                : this.font.plainSubstrByWidth(text, Math.max(0, maxWidth - this.font.width("..."))) + "...";
        guiGraphics.drawString(this.font, trimmed, x, y, color, false);
    }

    private ItemName resolveItemName(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(key)) {
            return new ItemName(id);
        }
        return new ItemName(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(key).getName(net.minecraft.world.item.ItemStack.EMPTY).getString());
    }

    private enum Page {
        LIST,
        MATERIALS
    }

    private record ItemName(String name) {
    }

    private final class FlatButton extends Button {
        private FlatButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            int fill = !this.active
                    ? 0x8852482F
                    : this.isHoveredOrFocused()
                    ? PANEL_LIGHT
                    : PANEL_MID;
            int topBorder = this.isHoveredOrFocused() ? BRASS : BRASS_SOFT;
            int leftBorder = this.isHoveredOrFocused() ? BRASS_SOFT : 0xFF6E5630;
            int rightBorder = !this.active ? PANEL_DARK : 0xCC0A0806;
            int bottomBorder = !this.active ? PANEL_DARK : 0xDD080604;
            int textColor = !this.active ? TEXT_MUTED : TEXT_PRIMARY;
            guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, fill);
            guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + 1, topBorder);
            guiGraphics.fill(this.getX(), this.getY(), this.getX() + 1, this.getY() + this.height, leftBorder);
            guiGraphics.fill(this.getX() + this.width - 1, this.getY(), this.getX() + this.width, this.getY() + this.height, rightBorder);
            guiGraphics.fill(this.getX(), this.getY() + this.height - 1, this.getX() + this.width, this.getY() + this.height, bottomBorder);
            guiGraphics.drawCenteredString(PortableStructurePrinterScreen.this.font, this.getMessage(), this.getX() + this.width / 2, this.getY() + 6, textColor);
        }

        @Override
        public void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            this.defaultButtonNarrationText(narrationElementOutput);
        }
    }

}
