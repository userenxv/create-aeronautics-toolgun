package com.enxv.aeronauticsstructuretool.blueprint.geometry;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.blueprint.model.CapturePlan;
import com.enxv.aeronauticsstructuretool.blueprint.model.CapturedSubLevel;
import com.enxv.aeronauticsstructuretool.blueprint.model.LoadedSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LinkedBlockReferenceRemapper {
    private LinkedBlockReferenceRemapper() {
    }

    public static void remapForSave(
            CompoundTag tag,
            String positionTag,
            String sublevelTag,
            CapturePlan plan,
            FlexibleBlockPosCodec.Encoding encoding,
            String owner
    ) {
        if (!tag.contains(positionTag)) {
            tag.remove(sublevelTag);
            return;
        }
        BlockPos worldPos = FlexibleBlockPosCodec.readRequired(tag, positionTag, owner);
        CapturedSubLevel target = findUniqueCapturedTarget(plan, worldPos, owner);
        if (target == null) {
            AeronauticsStructureToolMod.LOGGER.warn(
                    "{} link at {} points outside the captured blueprint and was explicitly disconnected",
                    owner,
                    worldPos
            );
            tag.remove(positionTag);
            tag.remove(sublevelTag);
            return;
        }

        PlotBlockTransform transform = PlotBlockTransform.capture(target.subLevel());
        FlexibleBlockPosCodec.write(
                tag,
                positionTag,
                transform.toSavedLocalBlockPos(worldPos),
                encoding
        );
        tag.putUUID(sublevelTag, target.blueprintId());
    }

    public static void remapForLoad(
            CompoundTag tag,
            String positionTag,
            String sublevelTag,
            Map<UUID, LoadedSubLevel> loadedSublevels,
            LoadedSubLevel currentSubLevel,
            FlexibleBlockPosCodec.Encoding encoding,
            boolean retainRuntimeSublevelId,
            String owner
    ) {
        if (!tag.contains(positionTag)) {
            tag.remove(sublevelTag);
            return;
        }
        BlockPos savedLocalPos = FlexibleBlockPosCodec.readRequired(tag, positionTag, owner);
        LoadedSubLevel target = currentSubLevel;
        if (tag.contains(sublevelTag)) {
            if (!tag.hasUUID(sublevelTag)) {
                throw new IllegalArgumentException(owner + " has invalid sublevel UUID '" + sublevelTag + "'");
            }
            UUID blueprintId = tag.getUUID(sublevelTag);
            target = loadedSublevels.get(blueprintId);
            if (target == null) {
                throw new IllegalArgumentException(
                        owner + " references missing blueprint sublevel " + blueprintId
                );
            }
        }

        FlexibleBlockPosCodec.write(
                tag,
                positionTag,
                LoadedSubLevelCoordinates.toGlobalBlockPos(target, savedLocalPos),
                encoding
        );
        if (retainRuntimeSublevelId) {
            tag.putUUID(sublevelTag, target.subLevel().getUniqueId());
        } else {
            tag.remove(sublevelTag);
        }
    }

    private static CapturedSubLevel findUniqueCapturedTarget(
            CapturePlan plan,
            BlockPos worldPos,
            String owner
    ) {
        List<CapturedSubLevel> matches = new ArrayList<>();
        for (CapturedSubLevel candidate : plan.sublevels()) {
            if (PlotBlockTransform.capture(candidate.subLevel()).containsPlotAbsolute(worldPos)) {
                matches.add(candidate);
            }
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException(
                    owner + " target " + worldPos + " belongs to multiple captured sublevels"
            );
        }
        return matches.isEmpty() ? null : matches.getFirst();
    }
}
