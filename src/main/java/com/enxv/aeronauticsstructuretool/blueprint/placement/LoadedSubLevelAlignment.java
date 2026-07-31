package com.enxv.aeronauticsstructuretool.blueprint.placement;

import com.enxv.aeronauticsstructuretool.blueprint.geometry.LoadedSubLevelCoordinates;
import com.enxv.aeronauticsstructuretool.blueprint.model.LoadedSubLevel;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3d;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class LoadedSubLevelAlignment {
    private LoadedSubLevelAlignment() {
    }

    public static void alignAll(
            Map<UUID, LoadedSubLevel> loadedSublevels,
            UUID rootBlueprintId,
            Vector3d target
    ) throws IOException {
        LoadedSubLevel rootLoaded = requireRoot(loadedSublevels, rootBlueprintId);
        alignPositionToAnchor(
                rootLoaded.subLevel(),
                rootLoaded.placementAnchor(),
                LoadedSubLevelCoordinates.toGlobalPosition(rootLoaded, rootLoaded.saved().localAnchor())
        );

        for (LoadedSubLevel loaded : loadedSublevels.values()) {
            if (loaded == rootLoaded) {
                continue;
            }
            alignPositionToAnchor(
                    loaded.subLevel(),
                    loaded.placementAnchor(),
                    LoadedSubLevelCoordinates.toGlobalPosition(loaded, loaded.saved().localAnchor())
            );
        }

        Vector3d rootAnchorWorld = computeWorldAnchor(
                rootLoaded.subLevel(),
                LoadedSubLevelCoordinates.toGlobalPosition(rootLoaded, rootLoaded.saved().localAnchor())
        );
        Vector3d finalPlacementDelta = new Vector3d(target).sub(rootAnchorWorld);
        for (LoadedSubLevel loaded : loadedSublevels.values()) {
            loaded.subLevel().logicalPose().position().add(finalPlacementDelta);
        }
    }

    public static LoadedSubLevel requireRoot(
            Map<UUID, LoadedSubLevel> loadedSublevels,
            UUID rootBlueprintId
    ) throws IOException {
        LoadedSubLevel rootLoaded = loadedSublevels.get(rootBlueprintId);
        if (rootLoaded == null) {
            throw new IOException("blueprint root sublevel is missing: " + rootBlueprintId);
        }
        return rootLoaded;
    }

    public static void alignPositionToAnchor(ServerSubLevel subLevel, Vector3d anchor, Vector3d localAnchor) {
        Vector3d currentAnchorWorld = subLevel.logicalPose().transformPosition(
                new Vector3d(Objects.requireNonNull(localAnchor, "localAnchor")),
                new Vector3d()
        );
        subLevel.logicalPose().position().add(new Vector3d(anchor).sub(currentAnchorWorld));
    }

    private static Vector3d computeWorldAnchor(ServerSubLevel subLevel, Vector3d localAnchor) {
        return subLevel.logicalPose().transformPosition(
                new Vector3d(Objects.requireNonNull(localAnchor, "localAnchor")),
                new Vector3d()
        );
    }
}
