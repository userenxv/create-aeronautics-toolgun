package com.enxv.aeronauticsstructuretool.client.screen;

import com.enxv.aeronauticsstructuretool.BlueprintListEntry;
import com.enxv.aeronauticsstructuretool.PortableStructurePreviewData;
import com.enxv.aeronauticsstructuretool.blueprint.storage.ClientBlueprintCatalog;
import com.enxv.aeronauticsstructuretool.client.ClientBlueprintPreviewExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public final class BlueprintPreviewState {
    private PortableStructurePreviewData preview;
    private Component error;
    private boolean loading;
    private long generation;

    public PortableStructurePreviewData preview() {
        return this.preview;
    }

    public Component error() {
        return this.error;
    }

    public boolean loading() {
        return this.loading;
    }

    public void load(BlueprintListEntry entry, Level level) {
        long requestGeneration = ++this.generation;
        this.preview = null;
        this.error = null;
        this.loading = false;
        if (entry == null || level == null) {
            return;
        }
        this.loading = true;
        CompletableFuture.supplyAsync(() -> decode(entry, level), ClientBlueprintPreviewExecutor.executor())
                .whenComplete((result, throwable) -> Minecraft.getInstance().execute(() -> {
                    if (requestGeneration != this.generation) {
                        return;
                    }
                    this.loading = false;
                    if (throwable == null) {
                        this.preview = result;
                        return;
                    }
                    Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
                    this.error = Component.literal(
                            cause.getMessage() == null ? "preview failed" : cause.getMessage()
                    );
                }));
    }

    public void clear() {
        this.generation++;
        this.preview = null;
        this.error = null;
        this.loading = false;
    }

    private static PortableStructurePreviewData decode(BlueprintListEntry entry, Level level) {
        try {
            return PortableStructurePreviewData.fromBlueprintBytes(
                    entry.displayName(),
                    ClientBlueprintCatalog.read(entry),
                    level
            );
        } catch (IOException exception) {
            throw new BlueprintPreviewLoadException(exception);
        }
    }

    private static final class BlueprintPreviewLoadException extends RuntimeException {
        private BlueprintPreviewLoadException(IOException cause) {
            super(cause.getMessage(), cause);
        }
    }
}
