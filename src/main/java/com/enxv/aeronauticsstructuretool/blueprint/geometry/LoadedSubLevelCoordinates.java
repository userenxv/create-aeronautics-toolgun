package com.enxv.aeronauticsstructuretool.blueprint.geometry;

import com.enxv.aeronauticsstructuretool.blueprint.model.LoadedSubLevel;
import net.minecraft.core.BlockPos;
import org.joml.Vector3d;

public final class LoadedSubLevelCoordinates {
    private LoadedSubLevelCoordinates() {
    }

    public static BlockPos toGlobalBlockPos(LoadedSubLevel loaded, BlockPos savedLocalPos) {
        return loaded.transform().toGlobalBlockPos(
                savedLocalPos.offset(0, loaded.verticalLayout().blockYShift(), 0)
        );
    }

    public static Vector3d toGlobalPosition(LoadedSubLevel loaded, Vector3d savedLocalPos) {
        return loaded.transform().toGlobalPosition(new Vector3d(
                savedLocalPos.x,
                savedLocalPos.y + loaded.verticalLayout().blockYShift(),
                savedLocalPos.z
        ));
    }
}
