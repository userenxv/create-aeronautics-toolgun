package com.enxv.aeronauticsstructuretool.client.screen.toolmode;

import com.enxv.aeronauticsstructuretool.RequestQueryVehiclePreviewPayload;
import com.enxv.aeronauticsstructuretool.RequestQueryVehiclesPayload;
import com.enxv.aeronauticsstructuretool.SyncQueryVehiclePreviewPayload;
import com.enxv.aeronauticsstructuretool.SyncQueryVehiclesPayload;
import com.enxv.aeronauticsstructuretool.client.screen.NearbyVehicleQueryState;
import com.enxv.aeronauticsstructuretool.client.screen.StructurePreviewViewState;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;

public final class ToolModeVehicleQueryController {
    private static final int ENTRIES_PER_PAGE = 6;

    private final NearbyVehicleQueryState state = new NearbyVehicleQueryState();
    private int page;
    private boolean queryInFlight;

    public NearbyVehicleQueryState state() {
        return this.state;
    }

    public List<NearbyVehicleQueryState.Entry> entries() {
        return this.state.entries();
    }

    public NearbyVehicleQueryState.Entry selectedEntry() {
        return this.state.selectedEntry();
    }

    public boolean isSelected(NearbyVehicleQueryState.Entry entry) {
        return this.state.isSelected(entry);
    }

    public int page() {
        return this.page;
    }

    public int pageCount() {
        return this.state.entries().isEmpty()
                ? 1
                : (int) Math.ceil((double) this.state.entries().size() / ENTRIES_PER_PAGE);
    }

    public void changePage(int delta) {
        this.page = Math.floorMod(this.page + delta, Math.max(1, pageCount()));
    }

    public void select(UUID id, Minecraft minecraft, StructurePreviewViewState previewView) {
        this.state.select(id);
        for (int index = 0; index < this.state.entries().size(); index++) {
            if (this.state.entries().get(index).id().equals(id)) {
                this.page = index / ENTRIES_PER_PAGE;
                break;
            }
        }
        requestSelectedPreview(minecraft, previewView);
    }

    public void refresh(Minecraft minecraft, int range) {
        if (this.queryInFlight) {
            return;
        }
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            this.state.clear();
            this.page = 0;
            return;
        }
        this.queryInFlight = true;
        PacketDistributor.sendToServer(new RequestQueryVehiclesPayload(range));
    }

    public void applyEntries(
            List<SyncQueryVehiclesPayload.Entry> syncedEntries,
            Minecraft minecraft,
            StructurePreviewViewState previewView
    ) {
        this.queryInFlight = false;
        boolean previewNeedsReload = this.state.replaceEntries(syncedEntries);
        this.page = Math.min(this.page, Math.max(0, pageCount() - 1));
        if (previewNeedsReload) {
            requestSelectedPreview(minecraft, previewView);
        }
    }

    public void applyPreview(
            SyncQueryVehiclePreviewPayload payload,
            Minecraft minecraft,
            StructurePreviewViewState previewView
    ) {
        if (this.state.applyPreview(payload, minecraft == null ? null : minecraft.level)) {
            previewView.clearRendererFailures();
        }
    }

    public void remove(UUID id) {
        this.state.remove(id);
        this.page = Math.min(this.page, Math.max(0, pageCount() - 1));
    }

    private void requestSelectedPreview(Minecraft minecraft, StructurePreviewViewState previewView) {
        NearbyVehicleQueryState.Entry selected = this.state.beginPreviewLoad();
        previewView.clearRendererFailures();
        if (selected == null || minecraft == null || minecraft.level == null) {
            return;
        }
        PacketDistributor.sendToServer(new RequestQueryVehiclePreviewPayload(selected.id()));
    }
}
