package com.enxv.aeronauticsstructuretool.blueprint.compat;

import com.enxv.aeronauticsstructuretool.blueprint.model.CapturePlan;
import com.enxv.aeronauticsstructuretool.blueprint.model.CapturedSubLevel;
import com.enxv.aeronauticsstructuretool.blueprint.model.LoadedSubLevel;
import com.enxv.aeronauticsstructuretool.compat.aeronautics.AeronauticsLinkBlueprintCompat;
import com.enxv.aeronauticsstructuretool.compat.copycats.CopycatBlueprintCompat;
import com.enxv.aeronauticsstructuretool.compat.create.CreateBlueprintCompat;
import com.enxv.aeronauticsstructuretool.compat.hardblock.HardBlockBlueprintCompat;
import com.enxv.aeronauticsstructuretool.compat.mianbao.MianbaoModernWarfareBlueprintCompat;
import com.enxv.aeronauticsstructuretool.compat.offroad.OffroadBlueprintCompat;
import com.enxv.aeronauticsstructuretool.compat.simulated.SimulatedDockingBlueprintCompat;
import com.enxv.aeronauticsstructuretool.compat.simulated.SimulatedStructureCompat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class BlockEntityCompatibilityPipeline {
    private static final String ID_TAG = "id";
    private static final String PLOT_CHUNKS_TAG = "chunks";
    private static final String PLOT_BLOCK_ENTITIES_TAG = "block_entities";
    private static final String SWIVEL_PLATE_ID = "simulated:swivel_bearing_link_block";
    private static final String SWIVEL_BEARING_ID = "simulated:swivel_bearing";

    private BlockEntityCompatibilityPipeline() {
    }

    public static void remapForSave(
            CompoundTag plotTag,
            CapturePlan plan,
            CapturedSubLevel currentSubLevel
    ) {
        forEachBlockEntity(plotTag, tag -> {
            String id = tag.getString(ID_TAG);
            if (SWIVEL_PLATE_ID.equals(id)) {
                SimulatedStructureCompat.remapSwivelPlateForSave(tag, plan);
            } else if (SWIVEL_BEARING_ID.equals(id)) {
                SimulatedStructureCompat.validateSwivelBearingForSave(tag, plan);
            } else if (SimulatedDockingBlueprintCompat.BLOCK_ENTITY_ID.equals(id)) {
                SimulatedDockingBlueprintCompat.remapForSave(tag, plan, currentSubLevel);
            } else if (AeronauticsLinkBlueprintCompat.supports(id)) {
                AeronauticsLinkBlueprintCompat.remapForSave(id, tag, plan, currentSubLevel);
            } else if (CreateBlueprintCompat.BELT_BLOCK_ENTITY_ID.equals(id)) {
                CreateBlueprintCompat.remapBeltForSave(tag, currentSubLevel);
            }
            HardBlockBlueprintCompat.remapForSave(id, tag, plan);
            SimulatedStructureCompat.remapRopeForSave(tag, plan, currentSubLevel);
        });
    }

    public static void remapForLoad(
            CompoundTag plotTag,
            Map<UUID, LoadedSubLevel> loadedSublevels,
            LoadedSubLevel currentSubLevel,
            Map<UUID, UUID> ropeIdRemap
    ) {
        forEachBlockEntity(plotTag, tag -> {
            String id = tag.getString(ID_TAG);
            if (SWIVEL_PLATE_ID.equals(id)) {
                SimulatedStructureCompat.remapSwivelPlateForLoad(tag, loadedSublevels);
            } else if (SWIVEL_BEARING_ID.equals(id)) {
                SimulatedStructureCompat.restoreSwivelBearingPositionForLoad(tag, loadedSublevels);
            } else if (SimulatedDockingBlueprintCompat.BLOCK_ENTITY_ID.equals(id)) {
                SimulatedDockingBlueprintCompat.remapForLoad(tag, loadedSublevels, currentSubLevel);
            } else if (AeronauticsLinkBlueprintCompat.supports(id)) {
                AeronauticsLinkBlueprintCompat.remapForLoad(id, tag, loadedSublevels, currentSubLevel);
            } else if (CreateBlueprintCompat.BELT_BLOCK_ENTITY_ID.equals(id)) {
                CreateBlueprintCompat.remapBeltForLoad(tag, currentSubLevel);
            }
            HardBlockBlueprintCompat.remapForLoad(id, tag, loadedSublevels, currentSubLevel);
            SimulatedStructureCompat.remapRopeForLoad(
                    tag,
                    loadedSublevels,
                    currentSubLevel,
                    ropeIdRemap
            );
        });
    }

    public static void forEachBlockEntity(CompoundTag plotTag, Consumer<CompoundTag> consumer) {
        CompoundTag chunks = plotTag.getCompound(PLOT_CHUNKS_TAG);
        for (String key : chunks.getAllKeys()) {
            ListTag blockEntities = chunks.getCompound(key)
                    .getList(PLOT_BLOCK_ENTITIES_TAG, Tag.TAG_COMPOUND);
            for (int i = 0; i < blockEntities.size(); i++) {
                consumer.accept(blockEntities.getCompound(i));
            }
        }
    }

    public static void refreshAfterLoad(
            ServerLevel level,
            ServerSubLevel subLevel,
            com.enxv.aeronauticsstructuretool.blueprint.geometry.PlotBlockTransform transform
    ) {
        for (BlockEntity blockEntity : transform.findBlockEntities(level)) {
            String id = String.valueOf(BlockEntityType.getKey(blockEntity.getType()));
            if (SWIVEL_PLATE_ID.equals(id)) {
                SimulatedStructureCompat.repairSwivelPlate(blockEntity);
            }
            if (OffroadBlueprintCompat.WHEEL_MOUNT_BLOCK_ENTITY_ID.equals(id)) {
                OffroadBlueprintCompat.refreshWheelMount(blockEntity);
            } else if (CopycatBlueprintCompat.supports(id)) {
                CopycatBlueprintCompat.refresh(blockEntity);
            }
        }
        MianbaoModernWarfareBlueprintCompat.restoreRecurringTicks(level, subLevel);
    }
}
