package com.enxv.aeronauticsstructuretool.blueprint.model;

import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record CapturePlan(
        UUID rootBlueprintId,
        Quaterniond rootOrientation,
        Vector3d rootRotationOffset,
        List<CapturedSubLevel> sublevels
) {
    public CapturedSubLevel findByOriginalId(UUID originalId) {
        for (CapturedSubLevel subLevel : this.sublevels) {
            if (subLevel.subLevel().getUniqueId().equals(originalId)) {
                return subLevel;
            }
        }
        return null;
    }

    public CapturedSubLevel findByBlueprintId(UUID blueprintId) {
        for (CapturedSubLevel subLevel : this.sublevels) {
            if (subLevel.blueprintId().equals(blueprintId)) {
                return subLevel;
            }
        }
        return null;
    }

    public static CapturePlan create(ServerSubLevel rootSubLevel, List<ServerSubLevel> sublevels) {
        Pose3d rootPose = rootSubLevel.logicalPose();
        Quaterniond rootOrientation = new Quaterniond(rootPose.orientation());
        Quaterniond rootOrientationInverse = new Quaterniond(rootOrientation).conjugate();
        Vector3d rootRotationOffset = new Vector3d(rootPose.rotationPoint());
        rootOrientationInverse.transform(rootRotationOffset);

        List<CapturedSubLevel> captured = new ArrayList<>();
        UUID rootBlueprintId = null;
        for (ServerSubLevel subLevel : sublevels) {
            UUID blueprintId = UUID.randomUUID();
            if (subLevel.equals(rootSubLevel)) {
                rootBlueprintId = blueprintId;
            }

            Vector3d relativePosition = rootPose.transformPositionInverse(subLevel.logicalPose().transformPosition(new Vector3d(
                    subLevel.getPlot().getCenterBlock().getX(),
                    subLevel.getPlot().getCenterBlock().getY(),
                    subLevel.getPlot().getCenterBlock().getZ()
            )));

            Vector3d rotationOffset = new Vector3d(subLevel.logicalPose().rotationPoint());
            rootOrientationInverse.transform(rotationOffset);

            Quaterniond relativeOrientation = new Quaterniond(rootOrientationInverse).mul(subLevel.logicalPose().orientation());
            var centerBlock = subLevel.getPlot().getCenterBlock();
            captured.add(new CapturedSubLevel(
                    blueprintId,
                    subLevel,
                    relativePosition,
                    rotationOffset,
                    relativeOrientation,
                    new Vector3d(centerBlock.getX(), centerBlock.getY(), centerBlock.getZ())
            ));
        }

        return new CapturePlan(
                rootBlueprintId != null ? rootBlueprintId : UUID.randomUUID(),
                rootOrientation,
                rootRotationOffset,
                List.copyOf(captured)
        );
    }
}
