package com.enxv.aeronauticsstructuretool.compat.create;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.LoadedSubLevelCoordinates;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.PlotBlockTransform;
import com.enxv.aeronauticsstructuretool.blueprint.model.CapturedSubLevel;
import com.enxv.aeronauticsstructuretool.blueprint.model.LoadedSubLevel;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class CreateBlueprintCompat {
    public static final String BELT_BLOCK_ENTITY_ID = "create:belt";
    private static final String PLOT_TAG = "plot";
    private static final String SUPER_GLUE_TAG = "AST_CreateSuperGlue";
    private static final String BELT_CONTROLLER_TAG = "Controller";

    private CreateBlueprintCompat() {
    }

    public static void captureSuperGlue(
            ServerLevel level,
            PlotBlockTransform transform,
            CompoundTag plotTag
    ) {
        ListTag glueEntries = new ListTag();
        for (net.minecraft.world.entity.Entity entity : transform.findEntities(level)) {
            if (!(entity instanceof SuperGlueEntity glueEntity)) {
                continue;
            }
            AABB box = glueEntity.getBoundingBox();
            Vector3d localMin = transform.toSavedLocalPosition(new Vector3d(box.minX, box.minY, box.minZ));
            Vector3d localMax = transform.toSavedLocalPosition(new Vector3d(box.maxX, box.maxY, box.maxZ));
            CompoundTag glueTag = new CompoundTag();
            SuperGlueEntity.writeBoundingBox(glueTag, new AABB(
                    localMin.x, localMin.y, localMin.z,
                    localMax.x, localMax.y, localMax.z
            ));
            glueEntries.add(glueTag);
        }
        if (!glueEntries.isEmpty()) {
            plotTag.put(SUPER_GLUE_TAG, glueEntries);
        }
    }

    public static List<CompoundTag> readSuperGlueEntries(CompoundTag sublevelTag) {
        if (!sublevelTag.contains(PLOT_TAG, Tag.TAG_COMPOUND)) {
            return List.of();
        }
        CompoundTag plotTag = sublevelTag.getCompound(PLOT_TAG);
        if (!plotTag.contains(SUPER_GLUE_TAG, Tag.TAG_LIST)) {
            return List.of();
        }
        ListTag glueList = plotTag.getList(SUPER_GLUE_TAG, Tag.TAG_COMPOUND);
        List<CompoundTag> result = new ArrayList<>(glueList.size());
        for (int i = 0; i < glueList.size(); i++) {
            result.add(glueList.getCompound(i).copy());
        }
        return result;
    }

    public static void restoreSuperGlue(ServerLevel level, Collection<LoadedSubLevel> loadedSublevels) {
        for (LoadedSubLevel loaded : loadedSublevels) {
            for (CompoundTag glueTag : loaded.saved().superGlueEntries()) {
                AABB localBox = SuperGlueEntity.readBoundingBox(glueTag);
                Vector3d globalMin = LoadedSubLevelCoordinates.toGlobalPosition(
                        loaded,
                        new Vector3d(localBox.minX, localBox.minY, localBox.minZ)
                );
                Vector3d globalMax = LoadedSubLevelCoordinates.toGlobalPosition(
                        loaded,
                        new Vector3d(localBox.maxX, localBox.maxY, localBox.maxZ)
                );
                SuperGlueEntity entity = new SuperGlueEntity(level, new AABB(
                        globalMin.x, globalMin.y, globalMin.z,
                        globalMax.x, globalMax.y, globalMax.z
                ));
                if (!level.addFreshEntity(entity)) {
                    throw new IllegalStateException("server rejected restored Create super glue entity");
                }
            }
        }
    }

    public static void refreshKinetics(ServerLevel level, Collection<LoadedSubLevel> loadedSublevels) {
        List<KineticBlockEntity> kinetics = new ArrayList<>();
        for (LoadedSubLevel loaded : loadedSublevels) {
            for (BlockEntity blockEntity : loaded.transform().findBlockEntities(level)) {
                if (blockEntity instanceof KineticBlockEntity kineticBlockEntity) {
                    kinetics.add(kineticBlockEntity);
                }
            }
        }

        for (KineticBlockEntity kinetic : kinetics) {
            kinetic.removeSource();
            kinetic.setNetwork(null);
        }
        for (KineticBlockEntity kinetic : kinetics) {
            try {
                kinetic.attachKinetics();
                kinetic.setChanged();
            } catch (RuntimeException exception) {
                AeronauticsStructureToolMod.LOGGER.warn(
                        "Create kinetic refresh failed for {} at {}; printed blocks remain valid",
                        kinetic.getType(),
                        kinetic.getBlockPos(),
                        exception
                );
            }
        }
    }

    public static void rebuildBelts(ServerLevel level, Collection<LoadedSubLevel> loadedSublevels) {
        Set<BlockPos> rebuiltSegments = new LinkedHashSet<>();
        for (LoadedSubLevel loaded : loadedSublevels) {
            for (BlockEntity blockEntity : loaded.transform().findBlockEntities(level)) {
                if (!(blockEntity instanceof BeltBlockEntity belt)) {
                    continue;
                }
                BlockPos beltPos = belt.getBlockPos();
                if (!rebuiltSegments.add(beltPos)) {
                    continue;
                }
                BeltBlock.initBelt(level, beltPos);
                rebuiltSegments.addAll(BeltBlock.getBeltChain(level, beltPos));
            }
        }
    }

    public static void remapBeltForSave(CompoundTag tag, CapturedSubLevel currentSubLevel) {
        if (!tag.contains(BELT_CONTROLLER_TAG)) {
            return;
        }
        BlockPos controllerPos = NbtUtils.readBlockPos(tag, BELT_CONTROLLER_TAG)
                .orElseThrow(() -> new IllegalArgumentException("Create belt has an invalid Controller position"));
        PlotBlockTransform transform = PlotBlockTransform.capture(currentSubLevel.subLevel());
        tag.put(
                BELT_CONTROLLER_TAG,
                NbtUtils.writeBlockPos(transform.toSavedLocalBlockPos(controllerPos))
        );
    }

    public static void remapBeltForLoad(CompoundTag tag, LoadedSubLevel currentSubLevel) {
        if (!tag.contains(BELT_CONTROLLER_TAG)) {
            return;
        }
        BlockPos controllerPos = NbtUtils.readBlockPos(tag, BELT_CONTROLLER_TAG)
                .orElseThrow(() -> new IllegalArgumentException("Create belt has an invalid Controller position"));
        tag.put(
                BELT_CONTROLLER_TAG,
                NbtUtils.writeBlockPos(LoadedSubLevelCoordinates.toGlobalBlockPos(currentSubLevel, controllerPos))
        );
    }
}
