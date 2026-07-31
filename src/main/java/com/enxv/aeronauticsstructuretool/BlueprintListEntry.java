package com.enxv.aeronauticsstructuretool;

import java.nio.file.Path;

public record BlueprintListEntry(
        String selectionKey,
        String displayName,
        String fileName,
        Path path,
        BlueprintSourceType sourceType
) {
    public boolean isImported() {
        return sourceType.isImported();
    }
}
