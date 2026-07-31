package com.enxv.aeronauticsstructuretool.blueprint.model;

import com.enxv.aeronauticsstructuretool.blueprint.geometry.PlotBlockTransform;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.PlotVerticalLayout;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3d;

public record LoadedSubLevel(
        SavedSubLevelBlueprint saved,
        ServerSubLevel subLevel,
        PlotBlockTransform transform,
        Vector3d placementAnchor,
        PlotVerticalLayout verticalLayout
) {
}
