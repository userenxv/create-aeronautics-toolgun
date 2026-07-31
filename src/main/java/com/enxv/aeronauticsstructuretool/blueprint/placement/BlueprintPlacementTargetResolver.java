package com.enxv.aeronauticsstructuretool.blueprint.placement;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

public final class BlueprintPlacementTargetResolver {
    private BlueprintPlacementTargetResolver() {
    }

    public static Target resolve(
            Level level,
            BlockPos clickedPos,
            Direction face,
            Vec3 hitLocation
    ) {
        if (level == null || clickedPos == null || face == null || hitLocation == null) {
            throw new IllegalArgumentException("placement target is incomplete");
        }
        PlacementTargetMath.requireFinite(hitLocation);
        SubLevel containing = Sable.HELPER.getContaining(level, clickedPos);
        Vec3 worldHit = Sable.HELPER.projectOutOfSubLevel(level, hitLocation);
        PlacementTargetMath.requireFinite(worldHit);
        Direction worldFace = containing == null
                ? face
                : PlacementTargetMath.projectFace(containing.logicalPose().orientation(), face);
        BlockPos worldClickedPos = PlacementTargetMath.resolveClickedBlock(worldHit, worldFace);
        if (!level.getWorldBorder().isWithinBounds(worldClickedPos)) {
            throw new IllegalArgumentException("placement target is outside the world border: " + worldClickedPos);
        }
        return new Target(
                worldClickedPos,
                worldFace,
                new Vector3d(worldHit.x, worldHit.y, worldHit.z)
        );
    }

    public record Target(BlockPos clickedPos, Direction face, Vector3d hit) {
        public Target {
            clickedPos = clickedPos.immutable();
            hit = new Vector3d(hit);
        }

        @Override
        public Vector3d hit() {
            return new Vector3d(this.hit);
        }
    }
}
