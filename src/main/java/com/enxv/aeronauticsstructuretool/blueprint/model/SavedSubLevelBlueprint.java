package com.enxv.aeronauticsstructuretool.blueprint.model;

import com.enxv.aeronauticsstructuretool.RuntimeContraptionBlueprint;
import net.minecraft.nbt.CompoundTag;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record SavedSubLevelBlueprint(
        UUID blueprintId,
        UUID originalId,
        String name,
        CompoundTag plotTag,
        List<RuntimeContraptionBlueprint> runtimeContraptions,
        Vector3d relativePosition,
        Vector3d relativeRotationData,
        Quaterniond relativeOrientation,
        boolean collisionDisabled,
        int sourceMinBuildHeight,
        Vector3d localAnchor,
        List<CompoundTag> superGlueEntries,
        List<CompoundTag> honeyGlueEntries
) {
    public SavedSubLevelBlueprint {
        blueprintId = Objects.requireNonNull(blueprintId, "blueprintId");
        originalId = Objects.requireNonNull(originalId, "originalId");
        name = Objects.requireNonNullElse(name, "");
        if (plotTag == null || plotTag.isEmpty()) {
            throw new IllegalArgumentException("plotTag is empty");
        }
        plotTag = plotTag.copy();
        runtimeContraptions = List.copyOf(runtimeContraptions);
        relativePosition = new Vector3d(Objects.requireNonNull(relativePosition, "relativePosition"));
        relativeRotationData = new Vector3d(Objects.requireNonNull(relativeRotationData, "relativeRotationData"));
        relativeOrientation = new Quaterniond(Objects.requireNonNull(relativeOrientation, "relativeOrientation"));
        localAnchor = new Vector3d(Objects.requireNonNull(localAnchor, "localAnchor"));
        superGlueEntries = copyTags(superGlueEntries);
        honeyGlueEntries = copyTags(honeyGlueEntries);
    }

    private static List<CompoundTag> copyTags(List<CompoundTag> tags) {
        return Objects.requireNonNull(tags, "tags").stream().map(CompoundTag::copy).toList();
    }
}
