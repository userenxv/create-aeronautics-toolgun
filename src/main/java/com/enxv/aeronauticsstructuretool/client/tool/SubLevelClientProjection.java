package com.enxv.aeronauticsstructuretool.client.tool;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.UUID;

public final class SubLevelClientProjection {
    private SubLevelClientProjection() {
    }

    public static Vec3 project(UUID subLevelId, Vec3 localPoint, Vec3 fallback) {
        return project(subLevelId, localPoint, fallback, Vec3.ZERO);
    }

    public static Vec3 project(UUID subLevelId, Vec3 localPoint, Vec3 fallback, Vec3 localOffset) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || subLevelId == null || localPoint == null) {
            return fallback;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(minecraft.level);
        SubLevel subLevel = container == null ? null : container.getSubLevel(subLevelId);
        if (subLevel == null) {
            return fallback;
        }
        Vector3d physical = subLevel.logicalPose().transformPosition(
                new Vector3d(localPoint.x, localPoint.y, localPoint.z),
                new Vector3d()
        );
        if (localOffset != null && localOffset.lengthSqr() > 1.0E-8D) {
            Vector3d worldOffset = new Vector3d(localOffset.x, localOffset.y, localOffset.z);
            subLevel.logicalPose().orientation().transform(worldOffset);
            physical.add(worldOffset);
        }
        return Sable.HELPER.projectOutOfSubLevel(
                minecraft.level,
                new Vec3(physical.x, physical.y, physical.z)
        );
    }

    public static Vec3 plotCenter(SubLevel subLevel) {
        BoundingBox3ic bounds = subLevel.getPlot() != null
                ? subLevel.getPlot().getBoundingBox()
                : new BoundingBox3i(new BoundingBox3d(subLevel.boundingBox()));
        return new Vec3(
                (bounds.minX() + bounds.maxX() + 1.0D) * 0.5D,
                (bounds.minY() + bounds.maxY() + 1.0D) * 0.5D,
                (bounds.minZ() + bounds.maxZ() + 1.0D) * 0.5D
        );
    }
}
