package com.enxv.aeronauticsstructuretool.compat.simulated;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.FlexibleBlockPosCodec;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.LoadedSubLevelCoordinates;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.PlotBlockTransform;
import com.enxv.aeronauticsstructuretool.blueprint.model.CapturePlan;
import com.enxv.aeronauticsstructuretool.blueprint.model.CapturedSubLevel;
import com.enxv.aeronauticsstructuretool.blueprint.model.LoadedSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SimulatedDockingBlueprintCompat {
    public static final String BLOCK_ENTITY_ID = "simulated:docking_connector";
    private static final String OTHER_CONNECTOR_TAG = "OtherConnector";
    private static final String OTHER_SUBLEVEL_TAG = "OtherConnectorSubLevelId";

    private SimulatedDockingBlueprintCompat() {
    }

    public static void remapForSave(
            CompoundTag tag,
            CapturePlan plan,
            CapturedSubLevel currentSubLevel
    ) {
        if (!tag.contains(OTHER_CONNECTOR_TAG)) {
            tag.remove(OTHER_SUBLEVEL_TAG);
            return;
        }
        BlockPos worldPos = FlexibleBlockPosCodec.readRequired(
                tag,
                OTHER_CONNECTOR_TAG,
                "Simulated docking connector"
        );
        CapturedSubLevel target = resolveSaveTarget(tag, plan, currentSubLevel, worldPos);
        if (target == null) {
            AeronauticsStructureToolMod.LOGGER.warn(
                    "Simulated docking link at {} points outside the captured blueprint and was explicitly disconnected",
                    worldPos
            );
            tag.remove(OTHER_CONNECTOR_TAG);
            tag.remove(OTHER_SUBLEVEL_TAG);
            return;
        }
        PlotBlockTransform transform = PlotBlockTransform.capture(target.subLevel());
        FlexibleBlockPosCodec.write(
                tag,
                OTHER_CONNECTOR_TAG,
                transform.toSavedLocalBlockPos(worldPos),
                FlexibleBlockPosCodec.Encoding.NBT_BLOCK_POS
        );
        tag.putUUID(OTHER_SUBLEVEL_TAG, target.blueprintId());
    }

    public static void remapForLoad(
            CompoundTag tag,
            Map<UUID, LoadedSubLevel> loadedSublevels,
            LoadedSubLevel currentSubLevel
    ) {
        if (!tag.contains(OTHER_CONNECTOR_TAG)) {
            tag.remove(OTHER_SUBLEVEL_TAG);
            return;
        }
        BlockPos localPos = FlexibleBlockPosCodec.readRequired(
                tag,
                OTHER_CONNECTOR_TAG,
                "Simulated docking connector"
        );
        LoadedSubLevel target = currentSubLevel;
        if (tag.contains(OTHER_SUBLEVEL_TAG)) {
            if (!tag.hasUUID(OTHER_SUBLEVEL_TAG)) {
                throw new IllegalArgumentException("Simulated docking connector has an invalid sublevel UUID");
            }
            UUID blueprintId = tag.getUUID(OTHER_SUBLEVEL_TAG);
            target = loadedSublevels.get(blueprintId);
            if (target == null) {
                throw new IllegalArgumentException(
                        "Simulated docking connector references missing blueprint sublevel " + blueprintId
                );
            }
        }
        FlexibleBlockPosCodec.write(
                tag,
                OTHER_CONNECTOR_TAG,
                LoadedSubLevelCoordinates.toGlobalBlockPos(target, localPos),
                FlexibleBlockPosCodec.Encoding.NBT_BLOCK_POS
        );
        tag.putUUID(OTHER_SUBLEVEL_TAG, target.subLevel().getUniqueId());
    }

    private static CapturedSubLevel resolveSaveTarget(
            CompoundTag tag,
            CapturePlan plan,
            CapturedSubLevel currentSubLevel,
            BlockPos worldPos
    ) {
        if (tag.contains(OTHER_SUBLEVEL_TAG)) {
            if (!tag.hasUUID(OTHER_SUBLEVEL_TAG)) {
                throw new IllegalArgumentException("Simulated docking connector has an invalid sublevel UUID");
            }
            UUID originalId = tag.getUUID(OTHER_SUBLEVEL_TAG);
            CapturedSubLevel target = plan.findByOriginalId(originalId);
            if (target == null) {
                throw new IllegalArgumentException(
                        "Simulated docking connector references uncaptured sublevel " + originalId
                );
            }
            return target;
        }

        List<CapturedSubLevel> matches = new ArrayList<>();
        for (CapturedSubLevel candidate : plan.sublevels()) {
            if (PlotBlockTransform.capture(candidate.subLevel()).containsPlotAbsolute(worldPos)) {
                matches.add(candidate);
            }
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException(
                    "Simulated docking target " + worldPos + " belongs to multiple captured sublevels"
            );
        }
        if (!matches.isEmpty()) {
            return matches.getFirst();
        }
        return PlotBlockTransform.capture(currentSubLevel.subLevel()).containsPlotAbsolute(worldPos)
                ? currentSubLevel
                : null;
    }
}
