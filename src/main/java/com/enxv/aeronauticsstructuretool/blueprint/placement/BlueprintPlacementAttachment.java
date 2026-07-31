package com.enxv.aeronauticsstructuretool.blueprint.placement;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.io.IOException;

@FunctionalInterface
public interface BlueprintPlacementAttachment {
    void attach(
            ServerLevel level,
            @Nullable ServerSubLevel target,
            ServerSubLevel placedRoot,
            Vector3d worldPoint,
            Direction face
    ) throws IOException;
}
