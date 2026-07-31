package com.enxv.aeronauticsstructuretool.blueprint.model;

import com.enxv.aeronauticsstructuretool.BlueprintMaterialSummary;

public record CapturedBlueprintArchive(
        String fileName,
        byte[] fileContents,
        BlueprintMaterialSummary materialSummary
) {
}
