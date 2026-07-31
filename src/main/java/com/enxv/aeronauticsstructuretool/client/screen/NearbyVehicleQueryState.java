package com.enxv.aeronauticsstructuretool.client.screen;

import com.enxv.aeronauticsstructuretool.PortableStructurePreviewData;
import com.enxv.aeronauticsstructuretool.SyncQueryVehiclePreviewPayload;
import com.enxv.aeronauticsstructuretool.SyncQueryVehiclesPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class NearbyVehicleQueryState {
    private List<Entry> entries = List.of();
    private UUID selectedId;
    private UUID previewId;
    private PortableStructurePreviewData preview;
    private Component previewError;

    public List<Entry> entries() {
        return this.entries;
    }

    public UUID selectedId() {
        return this.selectedId;
    }

    public Entry selectedEntry() {
        if (this.selectedId == null) {
            return null;
        }
        for (Entry entry : this.entries) {
            if (this.selectedId.equals(entry.id())) {
                return entry;
            }
        }
        return null;
    }

    public boolean isSelected(Entry entry) {
        return entry != null && this.selectedId != null && this.selectedId.equals(entry.id());
    }

    public PortableStructurePreviewData preview() {
        return this.preview;
    }

    public Component previewError() {
        return this.previewError;
    }

    public void select(UUID id) {
        if (id != null && this.entries.stream().noneMatch(entry -> id.equals(entry.id()))) {
            clearSelection();
            return;
        }
        this.selectedId = id;
        if (id == null || !id.equals(this.previewId)) {
            clearPreview();
        }
    }

    public boolean replaceEntries(List<SyncQueryVehiclesPayload.Entry> syncedEntries) {
        Entry previousSelection = selectedEntry();
        boolean wasBroken = previousSelection != null && previousSelection.broken();
        List<Entry> replacement = new ArrayList<>(syncedEntries.size());
        for (SyncQueryVehiclesPayload.Entry entry : syncedEntries) {
            replacement.add(new Entry(
                    entry.id(),
                    entry.displayName(),
                    entry.fullName(),
                    entry.distance(),
                    entry.position(),
                    entry.loaded(),
                    entry.broken()
            ));
        }
        this.entries = List.copyOf(replacement);
        Entry selected = selectedEntry();
        if (selected == null) {
            clearSelection();
            return false;
        }
        if (selected.broken()) {
            setBrokenPreview(selected);
            return false;
        }
        if (wasBroken) {
            clearPreview();
            return true;
        }
        return !this.selectedId.equals(this.previewId);
    }

    public void clear() {
        this.entries = List.of();
        clearSelection();
    }

    public void remove(UUID removedId) {
        if (removedId == null) {
            return;
        }
        this.entries = this.entries.stream()
                .filter(entry -> !removedId.equals(entry.id()))
                .toList();
        if (removedId.equals(this.selectedId)) {
            clearSelection();
        }
    }

    public Entry beginPreviewLoad() {
        Entry selected = selectedEntry();
        if (selected != null && selected.broken()) {
            setBrokenPreview(selected);
            return null;
        }
        this.previewId = selected == null ? null : selected.id();
        this.preview = null;
        this.previewError = selected == null
                ? null
                : Component.translatable("screen.create_aeronautics_toolgun.query.preview_loading");
        return selected;
    }

    public boolean applyPreview(SyncQueryVehiclePreviewPayload payload, Level level) {
        Entry selected = selectedEntry();
        if (selected == null || selected.broken() || !selected.id().equals(payload.subLevelId())) {
            return false;
        }
        this.previewId = payload.subLevelId();
        this.preview = null;
        this.previewError = null;
        if (!payload.success()) {
            this.previewError = Component.literal(
                    payload.error().isBlank() ? "preview failed" : payload.error()
            );
            return true;
        }
        try {
            String fallbackName = payload.name().isBlank() ? "vehicle" : payload.name();
            this.preview = PortableStructurePreviewData.fromBlueprintBytes(
                    fallbackName,
                    payload.blueprintBytes(),
                    level
            );
        } catch (IOException | RuntimeException exception) {
            this.previewError = Component.literal(
                    exception.getMessage() == null ? "preview failed" : exception.getMessage()
            );
        }
        return true;
    }

    private void clearSelection() {
        this.selectedId = null;
        clearPreview();
    }

    private void clearPreview() {
        this.previewId = null;
        this.preview = null;
        this.previewError = null;
    }

    private void setBrokenPreview(Entry selected) {
        this.previewId = selected.id();
        this.preview = null;
        this.previewError = Component.translatable(
                "screen.create_aeronautics_toolgun.query.broken_detail"
        );
    }

    public record Entry(
            UUID id,
            String displayName,
            String fullName,
            double distance,
            BlockPos position,
            boolean loaded,
            boolean broken
    ) {
        public String summary() {
            return String.format(
                    Locale.ROOT,
                    "%s%.1fb | %d,%d,%d",
                    broken ? "broken | " : loaded ? "" : "disk | ",
                    distance,
                    position.getX(),
                    position.getY(),
                    position.getZ()
            );
        }

        public String positionText() {
            return position.getX() + ", " + position.getY() + ", " + position.getZ();
        }
    }
}
