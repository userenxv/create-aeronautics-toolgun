package com.enxv.aeronauticsstructuretool.client.screen;

import com.enxv.aeronauticsstructuretool.BlueprintListEntry;
import com.enxv.aeronauticsstructuretool.ClientToolState;
import com.enxv.aeronauticsstructuretool.ModItems;
import com.enxv.aeronauticsstructuretool.SyncQueryVehiclePreviewPayload;
import com.enxv.aeronauticsstructuretool.SyncQueryVehiclesPayload;
import com.enxv.aeronauticsstructuretool.ToolMode;
import com.enxv.aeronauticsstructuretool.ToolPanel;
import com.enxv.aeronauticsstructuretool.blueprint.storage.ClientBlueprintCatalog;
import com.enxv.aeronauticsstructuretool.client.screen.toolmode.ToolModeControls.InfiniteRangeToggle;
import com.enxv.aeronauticsstructuretool.client.screen.toolmode.ToolModeControls.PanelButton;
import com.enxv.aeronauticsstructuretool.client.screen.toolmode.ToolModeControls.SearchField;
import com.enxv.aeronauticsstructuretool.client.screen.toolmode.ToolModeLayout;
import com.enxv.aeronauticsstructuretool.client.screen.toolmode.ToolModeBlueprintBrowser;
import com.enxv.aeronauticsstructuretool.client.screen.toolmode.ToolModeHitTest;
import com.enxv.aeronauticsstructuretool.client.screen.toolmode.ToolModeMenuModel;
import com.enxv.aeronauticsstructuretool.client.screen.toolmode.ToolModeMenuModel.LeftTab;
import com.enxv.aeronauticsstructuretool.client.screen.toolmode.ToolModeMenuModel.ToolEntry;
import com.enxv.aeronauticsstructuretool.client.screen.toolmode.ToolModeQueryRange;
import com.enxv.aeronauticsstructuretool.client.screen.toolmode.ToolModeVehicleQueryController;
import com.enxv.aeronauticsstructuretool.client.screen.toolmode.ToolModePanelRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.navigation.CommonInputs;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.PANEL;
import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.TEXT_MUTED;
import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.TEXT_PRIMARY;
import static com.enxv.aeronauticsstructuretool.client.screen.toolmode.ToolModeLayout.PAGE_BUTTON_HEIGHT;
import static com.enxv.aeronauticsstructuretool.client.screen.toolmode.ToolModeLayout.PAGE_BUTTON_WIDTH;

public final class ToolModeScreen extends Screen {
    private static final int TOOLS_PER_PAGE = 8;
    private static final int QUERY_ACTION_BUTTON_WIDTH = 110;
    private static final int QUERY_ACTION_BUTTON_HEIGHT = 18;
    private static final int QUERY_DETAIL_LEFT_PADDING = 10;

    private Button prevButton;
    private Button nextButton;
    private Button closeButton;
    private Button openFolderButton;
    private Button queryTeleportButton;
    private Button queryRecoverButton;
    private Button queryRenameButton;
    private Button queryDeleteButton;
    private Button queryRefreshButton;
    private InfiniteRangeToggle infiniteQueryToggle;
    private EditBox nearbyRangeBox;
    private SearchField blueprintSearchBox;
    private final ToolModeBlueprintBrowser blueprintBrowser = new ToolModeBlueprintBrowser();
    private final StructurePreviewViewState previewView = new StructurePreviewViewState();
    private final ToolModeVehicleQueryController vehicleQuery = new ToolModeVehicleQueryController();
    private ToolModePanelRenderer panelRenderer;
    private ToolModeLayout layout;
    private int hoveredLeftIndex = -1;
    private int hoveredFileIndex = -1;
    private int hoveredToolIndex = -1;
    private int hoveredDeleteIndex = -1;
    private int hoveredVehicleIndex = -1;
    private int toolPage;
    private double lastMouseX;
    private double lastMouseY;
    private boolean survivalRestricted;

    public ToolModeScreen() {
        super(Component.translatable("itemGroup.create_aeronautics_toolgun.main"));
    }

    @Override
    public void removed() {
        this.blueprintBrowser.close();
        StructurePreviewRenderer.clearCache();
        super.removed();
    }

    @Override
    protected void init() {
        this.survivalRestricted = this.minecraft != null
                && this.minecraft.player != null
                && (ModItems.isSurvivalStructureTool(this.minecraft.player.getMainHandItem())
                || ModItems.isSurvivalStructureTool(this.minecraft.player.getOffhandItem()));
        ClientToolState.sanitizeNearbyQueryRangeForTool(this.survivalRestricted);
        sanitizeStateForHeldTool();
        this.blueprintBrowser.initialize(
                this.minecraft == null ? null : this.minecraft.level,
                currentLeftTab() == LeftTab.LOAD
        );
        this.panelRenderer = new ToolModePanelRenderer(this.font);
        this.toolPage = 0;
        int left = windowLeft();
        int top = windowTop();
        int right = left + windowWidth();
        this.prevButton = this.addRenderableWidget(new PanelButton(this.font, footerPrevButtonX(), footerButtonY(), PAGE_BUTTON_WIDTH, PAGE_BUTTON_HEIGHT, Component.translatable("screen.create_aeronautics_toolgun.prev"), button -> changePage(-1)));
        this.nextButton = this.addRenderableWidget(new PanelButton(this.font, footerNextButtonX(), footerButtonY(), PAGE_BUTTON_WIDTH, PAGE_BUTTON_HEIGHT, Component.translatable("screen.create_aeronautics_toolgun.next"), button -> changePage(1)));
        this.closeButton = this.addRenderableWidget(new PanelButton(this.font, right - 30, top + 8, 16, 16, Component.literal("X"), button -> this.onClose()));
        this.openFolderButton = this.addRenderableWidget(new PanelButton(this.font, right - 92, top + 28, 60, 16, Component.translatable("screen.create_aeronautics_toolgun.open_folder"), button -> openBlueprintFolder()));
        this.queryTeleportButton = this.addRenderableWidget(new PanelButton(this.font, 0, 0, QUERY_ACTION_BUTTON_WIDTH, QUERY_ACTION_BUTTON_HEIGHT, Component.translatable("screen.create_aeronautics_toolgun.query.teleport"), button -> openSelectedVehicleTeleport()));
        this.queryRecoverButton = this.addRenderableWidget(new PanelButton(this.font, 0, 0, QUERY_ACTION_BUTTON_WIDTH, QUERY_ACTION_BUTTON_HEIGHT, Component.translatable("screen.create_aeronautics_toolgun.query.recover"), button -> openSelectedVehicleRecovery()));
        this.queryRenameButton = this.addRenderableWidget(new PanelButton(this.font, 0, 0, QUERY_ACTION_BUTTON_WIDTH, QUERY_ACTION_BUTTON_HEIGHT, Component.translatable("screen.create_aeronautics_toolgun.query.rename"), button -> openSelectedVehicleRename()));
        this.queryDeleteButton = this.addRenderableWidget(new PanelButton(this.font, 0, 0, QUERY_ACTION_BUTTON_WIDTH, QUERY_ACTION_BUTTON_HEIGHT, Component.translatable("screen.create_aeronautics_toolgun.query.delete"), button -> openSelectedVehicleDelete()));
        this.queryRefreshButton = this.addRenderableWidget(new PanelButton(this.font, 0, 0, 44, 16, Component.translatable("screen.create_aeronautics_toolgun.query.refresh"), button -> refreshNearbyVehicles()));
        this.infiniteQueryToggle = new InfiniteRangeToggle(
                splitX() + 130,
                top + 56,
                this::isInfiniteQueryRange,
                this::toggleInfiniteQueryRange
        );
        this.nearbyRangeBox = new EditBox(this.font, splitX() + 22, top + 56, 72, 16, Component.translatable("screen.create_aeronautics_toolgun.query.range"));
        this.nearbyRangeBox.setBordered(false);
        this.nearbyRangeBox.setCanLoseFocus(true);
        this.nearbyRangeBox.setTextColor(TEXT_PRIMARY);
        this.nearbyRangeBox.setTextColorUneditable(TEXT_MUTED);
        this.nearbyRangeBox.setMaxLength(10);
        this.nearbyRangeBox.setValue(ToolModeQueryRange.format(ClientToolState.nearbyQueryRangeForTool(this.survivalRestricted)));
        this.nearbyRangeBox.setResponder(value -> {
            int parsed = ToolModeQueryRange.parse(
                    value,
                    ClientToolState.nearbyQueryRangeForTool(this.survivalRestricted),
                    this.survivalRestricted
            );
            ClientToolState.setNearbyQueryRange(parsed);
            ClientToolState.sanitizeNearbyQueryRangeForTool(this.survivalRestricted);
            String normalized = ToolModeQueryRange.format(
                    ClientToolState.nearbyQueryRangeForTool(this.survivalRestricted)
            );
            if (!value.equals(normalized)) {
                this.nearbyRangeBox.setValue(normalized);
            }
            refreshNearbyVehicles();
        });
        this.addRenderableWidget(this.nearbyRangeBox);
        this.blueprintSearchBox = new SearchField(splitX() + 21, top + 56, 152, 16, this::syncLoadPageToSelection);
        refreshNearbyVehicles();
        refreshButtons();
        layoutButtons();
    }

    private void syncLoadPageToSelection() {
        this.blueprintBrowser.syncPageToSelection(blueprintSearchQuery());
    }

    private LeftTab currentLeftTab() {
        return ToolModeMenuModel.currentTab();
    }

    private void sanitizeStateForHeldTool() {
        if (!this.survivalRestricted) {
            return;
        }
        if (ClientToolState.getMode() == ToolMode.LOAD
                || ClientToolState.getMode() == ToolMode.DELETE
                || ClientToolState.getMode() == ToolMode.NO_COLLISION
                || ClientToolState.getMode() == ToolMode.GHOST_VEHICLE_TEST) {
            ClientToolState.setMode(ToolMode.SAVE);
        }
    }

    private LeftTab[] availableLeftTabs() {
        return ToolModeMenuModel.availableTabs(this.survivalRestricted);
    }

    private ToolEntry[] availableToolEntries() {
        return ToolModeMenuModel.availableTools(this.survivalRestricted);
    }

    private void selectLeftTab(LeftTab tab) {
        switch (tab) {
            case SAVE -> {
                ClientToolState.setPanel(ToolPanel.BLUEPRINTS);
                ClientToolState.setMode(ToolMode.SAVE);
            }
            case LOAD -> {
                ClientToolState.setPanel(ToolPanel.BLUEPRINTS);
                ClientToolState.setMode(ToolMode.LOAD);
                this.blueprintBrowser.syncPageToSelection(blueprintSearchQuery());
                this.blueprintBrowser.ensurePreview(this.minecraft == null ? null : this.minecraft.level);
            }
            case DELETE -> {
                ClientToolState.setPanel(ToolPanel.BLUEPRINTS);
                ClientToolState.setMode(ToolMode.DELETE);
            }
            case QUERY -> {
                ClientToolState.setPanel(ToolPanel.QUERY);
                refreshNearbyVehicles();
            }
            case TOOLS -> ClientToolState.setPanel(ToolPanel.TOOLS);
        }
        refreshButtons();
        applyCurrentLayout();
    }

    private boolean showingTools() {
        return currentLeftTab() == LeftTab.TOOLS;
    }

    private boolean showingQuery() {
        return currentLeftTab() == LeftTab.QUERY;
    }

    private ToolModeLayout currentLayout() {
        LeftTab tab = currentLeftTab();
        if (this.layout == null || !this.layout.matches(this.width, this.height, tab)) {
            this.layout = new ToolModeLayout(this.width, this.height, tab);
        }
        return this.layout;
    }

    private int windowLeft() {
        return currentLayout().windowLeft();
    }

    private int windowTop() {
        return currentLayout().windowTop();
    }

    private int windowHeight() {
        return currentLayout().windowHeight();
    }

    private int windowWidth() {
        return currentLayout().windowWidth();
    }

    private int splitX() {
        return currentLayout().splitX();
    }

    private int rightPanelLeft() {
        return currentLayout().rightPanelLeft();
    }

    private int rightPanelRight() {
        return currentLayout().rightPanelRight();
    }

    private int rightPanelWidth() {
        return currentLayout().rightPanelWidth();
    }

    private int loadListWidth() {
        return currentLayout().loadListWidth();
    }

    private int loadPreviewLeft() {
        return currentLayout().loadPreviewLeft();
    }

    private int queryListWidth() {
        return currentLayout().queryListWidth();
    }

    private int queryDetailLeft() {
        return currentLayout().queryDetailLeft();
    }

    private int footerPrevButtonX() {
        return currentLayout().footerPrevButtonX();
    }

    private int footerNextButtonX() {
        return currentLayout().footerNextButtonX();
    }

    private int footerButtonY() {
        return currentLayout().footerButtonY();
    }

    private int footerTextY() {
        return currentLayout().footerTextY();
    }

    private int footerMetaCenterX() {
        return currentLayout().footerMetaCenterX();
    }

    private int footerPageCenterX() {
        return currentLayout().footerPageCenterX();
    }

    private void refreshButtons() {
        applyCurrentLayout();
        boolean visible = currentLeftTab() == LeftTab.LOAD || showingTools() || showingQuery();
        this.prevButton.visible = visible;
        this.nextButton.visible = visible;
        this.prevButton.active = visible;
        this.nextButton.active = visible;
        boolean showOpenFolder = currentLeftTab() == LeftTab.LOAD;
        this.openFolderButton.visible = showOpenFolder;
        this.openFolderButton.active = showOpenFolder;
        this.blueprintSearchBox.setVisible(showOpenFolder);
        if (!showOpenFolder) {
            this.blueprintSearchBox.setFocused(false);
        }
        this.nearbyRangeBox.visible = showingQuery();
        this.nearbyRangeBox.setEditable(showingQuery() && !isInfiniteQueryRange());
        this.queryRefreshButton.visible = showingQuery();
        this.queryRefreshButton.active = showingQuery();
        boolean showInfiniteToggle = showingQuery() && !this.survivalRestricted;
        this.infiniteQueryToggle.setVisible(showInfiniteToggle);
        if (!showingQuery()) {
            this.nearbyRangeBox.setFocused(false);
        }
        refreshQueryActionButtons();
    }

    private void applyCurrentLayout() {
        layoutButtons();
        if (this.blueprintSearchBox != null) {
            this.blueprintSearchBox.setBounds(rightPanelLeft() + 3, windowTop() + 56, Math.max(40, loadListWidth() - 18), 16);
        }
        if (this.nearbyRangeBox != null) {
            this.nearbyRangeBox.setPosition(splitX() + 24, windowTop() + 57);
        }
        if (this.infiniteQueryToggle != null) {
            this.infiniteQueryToggle.setPosition(splitX() + 128, windowTop() + 56);
        }
        if (this.queryRefreshButton != null) {
            this.queryRefreshButton.setPosition(rightPanelLeft() + Math.max(88, queryListWidth() - 30), windowTop() + 30);
        }
    }

    private void layoutButtons() {
        int top = windowTop();
        int right = windowLeft() + windowWidth();
        this.closeButton.setPosition(right - 30, top + 8);
        this.openFolderButton.setPosition(right - 92, top + 28);
        this.prevButton.setPosition(footerPrevButtonX(), footerButtonY());
        this.nextButton.setPosition(footerNextButtonX(), footerButtonY());
        if (this.queryTeleportButton != null) {
            int detailLeft = queryDetailLeft();
            int buttonX = detailLeft + QUERY_DETAIL_LEFT_PADDING;
            this.queryTeleportButton.setPosition(buttonX, top + 112);
            this.queryRecoverButton.setPosition(buttonX, top + 112);
            this.queryRenameButton.setPosition(buttonX, top + 134);
            this.queryDeleteButton.setPosition(buttonX, top + 156);
        }
    }

    private void openBlueprintFolder() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        Path directory = ClientBlueprintCatalog.nativeDirectory();
        try {
            Files.createDirectories(directory);
            new ProcessBuilder("explorer.exe", directory.toString()).start();
        } catch (IOException exception) {
            this.minecraft.player.displayClientMessage(
                    Component.translatable("message.create_aeronautics_toolgun.open_folder_failed", directory.toAbsolutePath()),
                    true
            );
        }
    }

    private int currentFilePage() {
        return this.blueprintBrowser.page();
    }

    private int toolPageCount() {
        return Mth.ceil((float) availableToolEntries().length / TOOLS_PER_PAGE);
    }

    private void changePage(int delta) {
        if (currentLeftTab() == LeftTab.LOAD) {
            this.blueprintBrowser.changePage(delta, blueprintSearchQuery());
            return;
        }
        if (showingTools()) {
            this.toolPage = Math.floorMod(this.toolPage + delta, Math.max(1, toolPageCount()));
            return;
        }
        if (showingQuery()) {
            this.vehicleQuery.changePage(delta);
        }
    }

    private Component rightPanelTitle() {
        return showingTools()
                ? Component.translatable("screen.create_aeronautics_toolgun.panel.tools")
                : currentLeftTab().label();
    }

    private Component leftHint() {
        return switch (currentLeftTab()) {
            case SAVE -> Component.translatable("screen.create_aeronautics_toolgun.mode.save_hint");
            case LOAD -> this.blueprintBrowser.isEmpty()
                    ? Component.translatable("screen.create_aeronautics_toolgun.no_files")
                    : selectedLoadHint();
            case DELETE -> Component.translatable("screen.create_aeronautics_toolgun.mode.delete_hint");
            case QUERY -> Component.translatable("screen.create_aeronautics_toolgun.mode.query_hint");
            case TOOLS -> Component.translatable("screen.create_aeronautics_toolgun.panel.tools_hint");
        };
    }

    private Component selectedLoadHint() {
        BlueprintListEntry entry = this.blueprintBrowser.selectedEntry();
        if (entry == null) {
            return Component.translatable("screen.create_aeronautics_toolgun.no_files");
        }
        return entry.isImported()
                ? Component.translatable("screen.create_aeronautics_toolgun.selected_file_vmod", entry.displayName())
                : Component.translatable("screen.create_aeronautics_toolgun.selected_file", entry.displayName());
    }

    private List<BlueprintListEntry> filteredFiles() {
        return this.blueprintBrowser.filteredFiles(blueprintSearchQuery());
    }

    private String blueprintSearchQuery() {
        return this.blueprintSearchBox == null ? "" : this.blueprintSearchBox.getValue();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        applyCurrentLayout();
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        guiGraphics.fill(0, 0, this.width, this.height, 0x50000000);
        int left = windowLeft();
        int top = windowTop();
        int right = left + windowWidth();
        int bottom = top + windowHeight();
        int split = splitX();
        if (showingQuery()) {
            refreshNearbyVehiclesIfDue();
        }

        this.hoveredLeftIndex = getLeftIndexAt(mouseX, mouseY);
        this.hoveredFileIndex = currentLeftTab() == LeftTab.LOAD ? getFileIndexAt(mouseX, mouseY) : -1;
        this.hoveredToolIndex = showingTools() ? getToolIndexAt(mouseX, mouseY) : -1;
        this.hoveredDeleteIndex = currentLeftTab() == LeftTab.DELETE ? getDeleteOptionIndexAt(mouseX, mouseY) : -1;
        this.hoveredVehicleIndex = showingQuery() ? getNearbyVehicleIndexAt(mouseX, mouseY) : -1;

        this.panelRenderer.renderFrame(guiGraphics, this.title, rightPanelTitle(), currentLayout(), showingQuery());
        this.panelRenderer.renderLeftRows(
                guiGraphics,
                currentLayout(),
                availableLeftTabs(),
                currentLeftTab(),
                this.hoveredLeftIndex
        );
        if (currentLeftTab() == LeftTab.LOAD) {
            this.panelRenderer.renderBlueprintPanel(
                    guiGraphics,
                    currentLayout(),
                    this.blueprintBrowser,
                    this.blueprintSearchBox,
                    this.previewView,
                    this.hoveredFileIndex
            );
        } else if (currentLeftTab() == LeftTab.DELETE) {
            this.panelRenderer.renderDeletePanel(guiGraphics, currentLayout(), this.hoveredDeleteIndex);
        } else if (showingQuery()) {
            this.panelRenderer.renderVehiclePanel(
                    guiGraphics,
                    currentLayout(),
                    this.vehicleQuery,
                    this.previewView,
                    this.infiniteQueryToggle,
                    this.survivalRestricted,
                    this.hoveredVehicleIndex,
                    this.lastMouseX,
                    this.lastMouseY
            );
        } else if (showingTools()) {
            this.panelRenderer.renderToolPanel(
                    guiGraphics,
                    currentLayout(),
                    availableToolEntries(),
                    this.toolPage,
                    this.hoveredToolIndex
            );
        } else {
            this.panelRenderer.renderInfoPanel(guiGraphics, currentLayout());
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        this.panelRenderer.renderHint(guiGraphics, currentLayout(), leftHint());
        if (showingQuery()
                && this.hoveredVehicleIndex >= 0
                && this.hoveredVehicleIndex < this.vehicleQuery.entries().size()) {
            NearbyVehicleQueryState.Entry hoveredEntry = this.vehicleQuery.entries().get(this.hoveredVehicleIndex);
            guiGraphics.renderTooltip(this.font, Component.literal(hoveredEntry.fullName()), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 || button == 1) {
            if (currentLeftTab() == LeftTab.LOAD && isInsideBlueprintSearchBox(mouseX, mouseY)) {
                this.blueprintSearchBox.setFocused(true);
                return this.blueprintSearchBox.mouseClicked(mouseX, mouseY, button);
            }
            if (showingQuery() && isInsideNearbyRangeBox(mouseX, mouseY)) {
                this.nearbyRangeBox.setFocused(true);
                return this.nearbyRangeBox.mouseClicked(mouseX, mouseY, button);
            }
            if (showingQuery() && this.infiniteQueryToggle.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            int clickedLeft = getLeftIndexAt(mouseX, mouseY);
            if (clickedLeft >= 0) {
                selectLeftTab(availableLeftTabs()[clickedLeft]);
                return true;
            }
            if (showingQuery() && this.nearbyRangeBox.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (super.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (showingQuery()) {
                int clickedVehicle = getNearbyVehicleIndexAt(mouseX, mouseY);
                if (clickedVehicle >= 0 && clickedVehicle < this.vehicleQuery.entries().size()) {
                    this.vehicleQuery.select(
                            this.vehicleQuery.entries().get(clickedVehicle).id(),
                            this.minecraft,
                            this.previewView
                    );
                    refreshQueryActionButtons();
                    return true;
                }
                if (isInsideQueryPreview(mouseX, mouseY) && button == 0) {
                    this.previewView.beginDrag();
                    return true;
                }
            }
            if (currentLeftTab() == LeftTab.LOAD) {
                if (this.blueprintSearchBox.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
                int clickedFile = getFileIndexAt(mouseX, mouseY);
                if (clickedFile >= 0) {
                    List<BlueprintListEntry> filtered = filteredFiles();
                    BlueprintListEntry clicked = filtered.get(clickedFile);
                    this.previewView.clearRendererFailures();
                    this.blueprintBrowser.select(
                            clicked,
                            this.minecraft == null ? null : this.minecraft.level,
                            blueprintSearchQuery()
                    );
                    return true;
                }
                if (isInsideLoadPreview(mouseX, mouseY) && button == 0) {
                    this.previewView.beginDrag();
                    return true;
                }
            }
            if (currentLeftTab() == LeftTab.DELETE) {
                int clickedDeleteOption = getDeleteOptionIndexAt(mouseX, mouseY);
                if (clickedDeleteOption >= 0) {
                    applyDeleteOptionAction(clickedDeleteOption, button == 0 ? 1 : -1);
                    return true;
                }
            }
            if (showingTools()) {
                int clickedTool = getToolIndexAt(mouseX, mouseY);
                if (clickedTool >= 0) {
                    applyToolAction(availableToolEntries()[clickedTool], button == 0 ? 1 : -1);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
        if (currentLeftTab() == LeftTab.SAVE) {
            return false;
        }
        if (currentLeftTab() == LeftTab.LOAD && isInsideLoadPreview(mouseX, mouseY)) {
            this.previewView.zoom(scrollY);
            return true;
        }
        if (showingQuery() && isInsideQueryPreview(mouseX, mouseY)) {
            this.previewView.zoom(scrollY);
            return true;
        }
        if (currentLeftTab() == LeftTab.DELETE) {
            if (getDeleteOptionIndexAt(mouseX, mouseY) == 1) {
                ClientToolState.adjustDeleteRange(scrollY > 0 ? 1 : -1);
                return true;
            }
            return false;
        }
        if (currentLeftTab() == LeftTab.TOOLS) {
            int clickedTool = getToolIndexAt(mouseX, mouseY);
            ToolEntry[] entries = availableToolEntries();
            if (clickedTool >= 0 && clickedTool < entries.length && canScrollToolEntry(entries[clickedTool])) {
                applyToolAction(entries[clickedTool], scrollY > 0 ? 1 : -1);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean canScrollToolEntry(ToolEntry entry) {
        return ToolModeMenuModel.canScroll(entry);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (currentLeftTab() == LeftTab.LOAD && this.blueprintSearchBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (showingQuery() && this.nearbyRangeBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (currentLeftTab() == LeftTab.LOAD) {
            if (this.blueprintSearchBox.keyPressed(keyCode, scanCode, modifiers) || this.blueprintSearchBox.canConsumeInput()) {
                return true;
            }
            if (CommonInputs.selected(keyCode)) {
                this.blueprintSearchBox.setFocused(false);
            }
        }
        if (showingQuery()) {
            if (this.nearbyRangeBox.keyPressed(keyCode, scanCode, modifiers) || this.nearbyRangeBox.canConsumeInput()) {
                return true;
            }
            if (CommonInputs.selected(keyCode)) {
                this.nearbyRangeBox.setFocused(false);
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void applyToolAction(ToolEntry entry, int delta) {
        if (ToolModeMenuModel.apply(entry, delta)) {
            this.onClose();
        }
    }

    private void applyDeleteOptionAction(int optionIndex, int delta) {
        if (optionIndex == 0) {
            ClientToolState.toggleRangeDeleteEnabled();
            return;
        }
        if (optionIndex == 1) {
            ClientToolState.adjustDeleteRange(delta);
        }
    }

    private void refreshQueryActionButtons() {
        if (this.queryTeleportButton == null
                || this.queryRecoverButton == null
                || this.queryRenameButton == null
                || this.queryDeleteButton == null) {
            return;
        }
        boolean visible = showingQuery();
        NearbyVehicleQueryState.Entry selected = selectedVehicleEntry();
        boolean hasSelection = visible && selected != null;
        boolean usableSelection = hasSelection && !selected.broken();
        boolean loadedSelection = hasSelection && selected.loaded();
        boolean brokenSelection = hasSelection && selected.broken();
        this.queryTeleportButton.visible = visible && !brokenSelection;
        this.queryRecoverButton.visible = visible && brokenSelection;
        this.queryRenameButton.visible = visible;
        this.queryDeleteButton.visible = visible;
        this.queryTeleportButton.active = usableSelection;
        this.queryRecoverButton.active = brokenSelection && !this.survivalRestricted;
        this.queryRenameButton.active = loadedSelection;
        this.queryDeleteButton.active = loadedSelection && !this.survivalRestricted;
    }

    private NearbyVehicleQueryState.Entry selectedVehicleEntry() {
        return this.vehicleQuery.selectedEntry();
    }

    private void openSelectedVehicleTeleport() {
        NearbyVehicleQueryState.Entry selected = selectedVehicleEntry();
        if (selected == null || selected.broken() || this.minecraft == null) {
            return;
        }
        this.minecraft.setScreen(new QueryVehicleTeleportScreen(
                this,
                selected.id(),
                selected.fullName(),
                selected.position().getX(),
                selected.position().getY(),
                selected.position().getZ()
        ));
    }

    private void openSelectedVehicleRecovery() {
        NearbyVehicleQueryState.Entry selected = selectedVehicleEntry();
        if (selected == null
                || !selected.broken()
                || this.survivalRestricted
                || this.minecraft == null) {
            return;
        }
        this.minecraft.setScreen(new QueryVehicleStorageActionScreen(
                this,
                selected.id(),
                selected.fullName(),
                QueryVehicleStorageActionScreen.Action.RECOVER
        ));
    }

    private void openSelectedVehicleRename() {
        NearbyVehicleQueryState.Entry selected = selectedVehicleEntry();
        if (selected == null || this.minecraft == null) {
            return;
        }
        this.minecraft.setScreen(new QueryVehicleRenameScreen(this, selected.id(), selected.fullName()));
    }

    private void openSelectedVehicleDelete() {
        NearbyVehicleQueryState.Entry selected = selectedVehicleEntry();
        if (selected == null || this.minecraft == null || this.survivalRestricted) {
            return;
        }
        this.minecraft.setScreen(new QueryVehicleDeleteScreen(this, selected.id(), selected.fullName()));
    }

    void afterVehicleDeleted(UUID deletedId) {
        this.vehicleQuery.remove(deletedId);
        refreshQueryActionButtons();
        refreshNearbyVehicles();
    }

    void afterVehicleStorageActionRequested() {
        refreshNearbyVehicles();
    }

    private void toggleInfiniteQueryRange() {
        if (this.survivalRestricted) {
            return;
        }
        if (isInfiniteQueryRange()) {
            ClientToolState.setNearbyQueryRange(ClientToolState.DEFAULT_SURVIVAL_NEARBY_QUERY_RANGE);
        } else {
            ClientToolState.setNearbyQueryRange(ClientToolState.INFINITE_NEARBY_QUERY_RANGE);
        }
        this.nearbyRangeBox.setValue(ToolModeQueryRange.format(ClientToolState.nearbyQueryRangeForTool(false)));
        refreshButtons();
        refreshNearbyVehicles();
    }

    private boolean isInfiniteQueryRange() {
        return !this.survivalRestricted
                && ClientToolState.nearbyQueryRangeForTool(false) == ClientToolState.INFINITE_NEARBY_QUERY_RANGE;
    }

    private int getLeftIndexAt(double mouseX, double mouseY) {
        return ToolModeHitTest.leftTabIndex(
                currentLayout(),
                availableLeftTabs().length,
                mouseX,
                mouseY
        );
    }

    private int getFileIndexAt(double mouseX, double mouseY) {
        List<BlueprintListEntry> filtered = filteredFiles();
        return ToolModeHitTest.blueprintIndex(
                currentLayout(),
                currentLeftTab() == LeftTab.LOAD,
                filtered.size(),
                currentFilePage(),
                mouseX,
                mouseY
        );
    }

    private int getToolIndexAt(double mouseX, double mouseY) {
        return ToolModeHitTest.toolIndex(
                currentLayout(),
                showingTools(),
                availableToolEntries().length,
                this.toolPage,
                mouseX,
                mouseY
        );
    }

    private int getDeleteOptionIndexAt(double mouseX, double mouseY) {
        return ToolModeHitTest.deleteOptionIndex(
                currentLayout(),
                currentLeftTab() == LeftTab.DELETE,
                mouseX,
                mouseY
        );
    }

    public boolean shouldAdjustDeleteRangeFromScroll() {
        return currentLeftTab() == LeftTab.DELETE && getDeleteOptionIndexAt(this.lastMouseX, this.lastMouseY) == 1;
    }

    public boolean shouldHotbarScrollPassThrough() {
        LeftTab tab = currentLeftTab();
        return tab == LeftTab.SAVE
                || tab == LeftTab.QUERY
                || tab == LeftTab.TOOLS
                || (tab == LeftTab.DELETE && !shouldAdjustDeleteRangeFromScroll());
    }

    private int getNearbyVehicleIndexAt(double mouseX, double mouseY) {
        return ToolModeHitTest.vehicleIndex(
                currentLayout(),
                showingQuery(),
                this.vehicleQuery.entries().size(),
                this.vehicleQuery.page(),
                mouseX,
                mouseY
        );
    }

    private boolean isInsideNearbyRangeBox(double mouseX, double mouseY) {
        return this.nearbyRangeBox != null
                && mouseX >= this.nearbyRangeBox.getX()
                && mouseX <= this.nearbyRangeBox.getX() + this.nearbyRangeBox.getWidth()
                && mouseY >= this.nearbyRangeBox.getY()
                && mouseY <= this.nearbyRangeBox.getY() + this.nearbyRangeBox.getHeight();
    }

    private boolean isInsideBlueprintSearchBox(double mouseX, double mouseY) {
        return this.blueprintSearchBox != null
                && mouseX >= this.blueprintSearchBox.getX()
                && mouseX <= this.blueprintSearchBox.getX() + this.blueprintSearchBox.getWidth()
                && mouseY >= this.blueprintSearchBox.getY()
                && mouseY <= this.blueprintSearchBox.getY() + this.blueprintSearchBox.getHeight();
    }

    private boolean isInsideLoadPreview(double mouseX, double mouseY) {
        return currentLayout().containsLoadPreview(mouseX, mouseY);
    }

    private boolean isInsideQueryPreview(double mouseX, double mouseY) {
        return currentLayout().containsQueryPreview(mouseX, mouseY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    @Override
    public void renderTransparentBackground(GuiGraphics guiGraphics) {
    }

    @Override
    protected void repositionElements() {
        super.repositionElements();
        applyCurrentLayout();
    }

    private void refreshNearbyVehiclesIfDue() {
        this.vehicleQuery.refreshIfDue(
                this.minecraft,
                ClientToolState.nearbyQueryRangeForTool(this.survivalRestricted)
        );
    }

    public static void receiveQueriedVehicles(SyncQueryVehiclesPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof ToolModeScreen screen) {
            screen.vehicleQuery.applyEntries(payload.entries(), minecraft, screen.previewView);
            screen.refreshQueryActionButtons();
        }
    }

    public static void receiveQueryVehiclePreview(SyncQueryVehiclePreviewPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof ToolModeScreen screen) {
            screen.vehicleQuery.applyPreview(payload, minecraft, screen.previewView);
        }
    }

    private void refreshNearbyVehicles() {
        int range = ClientToolState.nearbyQueryRangeForTool(this.survivalRestricted);
        this.vehicleQuery.refresh(this.minecraft, range);
        refreshQueryActionButtons();
    }

}
