package com.enxv.aeronauticsstructuretool.blueprint.model;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;

public record NativeBlueprintPlacementResult(
        boolean runtimeRestoredImmediately,
        ServerSubLevel rootSubLevel,
        int placedSubLevelCount,
        double placedTotalMass
) {
}
