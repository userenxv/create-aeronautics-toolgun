package com.enxv.aeronauticsstructuretool.client.tool.state;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.BlueprintListEntry;
import com.enxv.aeronauticsstructuretool.PreviewBlueprintData;
import com.enxv.aeronauticsstructuretool.blueprint.storage.ClientBlueprintCatalog;
import com.enxv.aeronauticsstructuretool.client.ClientBlueprintPreviewExecutor;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ClientBlueprintSelectionState {
    private PreviewBlueprintData cachedPreview;
    private String cachedPreviewKey = "";
    private String previewError = "";
    private List<BlueprintListEntry> cachedFileEntries = List.of();
    private boolean fileEntriesLoaded;
    private BlueprintListEntry cachedSelectedEntry;
    private String loadingPreviewKey = "";
    private long previewGeneration;

    public void selectionChanged(String selectionKey) {
        this.previewGeneration++;
        this.cachedPreview = null;
        this.cachedPreviewKey = "";
        this.previewError = "";
        this.cachedSelectedEntry = findSelectedEntryInCache(selectionKey);
        this.loadingPreviewKey = "";
    }

    public List<BlueprintListEntry> listFiles() {
        ensureFileEntriesLoaded();
        return this.cachedFileEntries;
    }

    public BlueprintListEntry selectedEntry(String selectionKey) {
        ensureFileEntriesLoaded();
        if (selectionKey == null || selectionKey.isBlank()) {
            this.cachedSelectedEntry = null;
            return null;
        }
        if (this.cachedSelectedEntry != null
                && this.cachedSelectedEntry.selectionKey().equals(selectionKey)) {
            return this.cachedSelectedEntry;
        }
        this.cachedSelectedEntry = findSelectedEntryInCache(selectionKey);
        return this.cachedSelectedEntry;
    }

    public void refresh(String selectionKey) {
        this.previewGeneration++;
        List<BlueprintListEntry> refreshed = ClientBlueprintCatalog.listEntries();
        this.cachedFileEntries = Collections.unmodifiableList(new ArrayList<>(refreshed));
        this.fileEntriesLoaded = true;
        this.cachedSelectedEntry = findSelectedEntryInCache(selectionKey);
        this.cachedPreview = null;
        this.cachedPreviewKey = "";
        this.previewError = "";
        this.loadingPreviewKey = "";
    }

    public PreviewBlueprintData getOrLoadPreview(String selectionKey) {
        BlueprintListEntry entry = selectedEntry(selectionKey);
        if (entry == null) {
            this.cachedPreview = null;
            this.cachedPreviewKey = "";
            this.previewError = "";
            return null;
        }
        if (entry.selectionKey().equals(this.cachedPreviewKey)) {
            return this.cachedPreview;
        }
        if (entry.selectionKey().equals(this.loadingPreviewKey)) {
            return null;
        }
        startPreviewLoad(entry);
        return null;
    }

    private void startPreviewLoad(BlueprintListEntry entry) {
        String key = entry.selectionKey();
        long generation = ++this.previewGeneration;
        net.minecraft.world.level.Level level = Minecraft.getInstance().level;
        this.loadingPreviewKey = key;
        this.previewError = "";
        CompletableFuture.supplyAsync(() -> {
            try {
                return PreviewBlueprintData.parse(ClientBlueprintCatalog.read(entry), level);
            } catch (Exception exception) {
                throw new PreviewLoadException(exception);
            }
        }, ClientBlueprintPreviewExecutor.executor()).whenComplete((preview, throwable) ->
                Minecraft.getInstance().execute(() -> completePreviewLoad(
                        entry,
                        key,
                        generation,
                        preview,
                        throwable
                ))
        );
    }

    private void completePreviewLoad(
            BlueprintListEntry entry,
            String key,
            long generation,
            PreviewBlueprintData preview,
            Throwable throwable
    ) {
        if (generation != this.previewGeneration) {
            return;
        }
        this.loadingPreviewKey = "";
        this.cachedPreviewKey = key;
        if (throwable == null) {
            this.cachedPreview = preview;
            this.previewError = "";
            return;
        }
        Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
        this.cachedPreview = null;
        this.previewError = cause.getMessage() == null ? "preview failed" : cause.getMessage();
        AeronauticsStructureToolMod.LOGGER.warn(
                "Failed to build client placement preview for '{}'",
                entry.displayName(),
                cause
        );
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    Component.translatable(
                            "message.create_aeronautics_toolgun.load_failed",
                            this.previewError
                    ),
                    true
            );
        }
    }

    private static final class PreviewLoadException extends RuntimeException {
        private PreviewLoadException(Exception cause) {
            super(cause.getMessage(), cause);
        }
    }

    public String previewError() {
        return this.previewError;
    }

    private void ensureFileEntriesLoaded() {
        if (!this.fileEntriesLoaded) {
            refresh("");
        }
    }

    private BlueprintListEntry findSelectedEntryInCache(String selectionKey) {
        if (selectionKey == null || selectionKey.isBlank()) {
            return null;
        }
        for (BlueprintListEntry entry : this.cachedFileEntries) {
            if (entry.selectionKey().equals(selectionKey)) {
                return entry;
            }
        }
        return null;
    }
}
