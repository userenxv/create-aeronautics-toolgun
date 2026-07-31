package com.enxv.aeronauticsstructuretool.client.screen.toolmode;

import com.enxv.aeronauticsstructuretool.BlueprintListEntry;
import com.enxv.aeronauticsstructuretool.ClientToolState;
import com.enxv.aeronauticsstructuretool.client.screen.BlueprintPreviewState;

import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ToolModeBlueprintBrowser {
    public static final int FILES_PER_PAGE = 6;

    private final BlueprintPreviewState preview = new BlueprintPreviewState();
    private List<BlueprintListEntry> files = List.of();
    private int selectedIndex;
    private int page;

    public void initialize(Level level, boolean loadPreview) {
        ClientToolState.refreshFileCache();
        this.files = ClientToolState.listFiles();
        this.selectedIndex = resolveSelectedIndex();
        this.page = Math.max(0, this.selectedIndex / FILES_PER_PAGE);
        syncSelectedFileWithState("");
        if (loadPreview) {
            loadPreview(level);
        } else {
            this.preview.clear();
        }
    }

    public void reload(Level level, String query) {
        ClientToolState.refreshFileCache();
        this.files = ClientToolState.listFiles();
        this.selectedIndex = resolveSelectedIndex();
        syncSelectedFileWithState(query);
        loadPreview(level);
    }

    public List<BlueprintListEntry> files() {
        return this.files;
    }

    public boolean isEmpty() {
        return this.files.isEmpty();
    }

    public BlueprintListEntry selectedEntry() {
        return ClientToolState.getSelectedEntry();
    }

    public BlueprintPreviewState preview() {
        return this.preview;
    }

    public void ensurePreview(Level level) {
        if (this.preview.preview() == null && !this.preview.loading() && this.preview.error() == null) {
            loadPreview(level);
        }
    }

    public void close() {
        this.preview.clear();
    }

    public List<BlueprintListEntry> filteredFiles(String rawQuery) {
        if (this.files.isEmpty()) {
            return List.of();
        }
        String query = normalizeQuery(rawQuery);
        if (query.isBlank()) {
            return this.files;
        }
        List<BlueprintListEntry> filtered = new ArrayList<>();
        for (BlueprintListEntry entry : this.files) {
            String display = entry.displayName().toLowerCase(Locale.ROOT);
            String fileName = entry.fileName().toLowerCase(Locale.ROOT);
            if (display.contains(query) || fileName.contains(query)) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    public int page() {
        return this.page;
    }

    public int pageCount(String query) {
        List<BlueprintListEntry> filtered = filteredFiles(query);
        return filtered.isEmpty() ? 1 : Mth.ceil((float) filtered.size() / FILES_PER_PAGE);
    }

    public void changePage(int delta, String query) {
        if (filteredFiles(query).isEmpty()) {
            return;
        }
        this.page = Math.floorMod(this.page + delta, pageCount(query));
    }

    public void syncPageToSelection(String query) {
        List<BlueprintListEntry> filtered = filteredFiles(query);
        if (filtered.isEmpty()) {
            this.page = 0;
            return;
        }
        BlueprintListEntry selected = selectedEntry();
        if (selected == null) {
            this.page = Mth.clamp(this.page, 0, Math.max(0, pageCount(query) - 1));
            return;
        }
        for (int index = 0; index < filtered.size(); index++) {
            if (filtered.get(index).selectionKey().equals(selected.selectionKey())) {
                this.page = index / FILES_PER_PAGE;
                return;
            }
        }
        this.page = 0;
    }

    public void select(BlueprintListEntry entry, Level level, String query) {
        int index = this.files.indexOf(entry);
        if (index < 0) {
            return;
        }
        this.selectedIndex = index;
        syncSelectedFileWithState(query);
        loadPreview(level);
    }

    private int resolveSelectedIndex() {
        String selectedFile = ClientToolState.getSelectedFile();
        for (int index = 0; index < this.files.size(); index++) {
            if (this.files.get(index).selectionKey().equals(selectedFile)) {
                return index;
            }
        }
        return 0;
    }

    private void syncSelectedFileWithState(String query) {
        if (this.files.isEmpty()) {
            ClientToolState.setSelectedFile("");
            this.selectedIndex = 0;
            this.page = 0;
            return;
        }
        this.selectedIndex = Mth.clamp(this.selectedIndex, 0, this.files.size() - 1);
        ClientToolState.setSelectedFile(this.files.get(this.selectedIndex).selectionKey());
        syncPageToSelection(query);
    }

    private void loadPreview(Level level) {
        this.preview.load(selectedEntry(), level);
    }

    private static String normalizeQuery(String rawQuery) {
        return rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.ROOT);
    }
}
