package com.enxv.aeronauticsstructuretool.compat.simulated;

import com.enxv.aeronauticsstructuretool.blueprint.geometry.LoadedSubLevelCoordinates;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.PlotBlockTransform;
import com.enxv.aeronauticsstructuretool.blueprint.model.LoadedSubLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class SimulatedGlueBlueprintCompat {
    private static final String HONEY_GLUE_ENTITY_ID = "simulated:honey_glue";
    private static final String HONEY_GLUE_ENTITY_CLASS =
            "dev.simulated_team.simulated.content.entities.honey_glue.HoneyGlueEntity";
    private static final String PLOT_TAG = "plot";
    private static final String HONEY_GLUE_TAG = "AST_SimulatedHoneyGlue";

    private SimulatedGlueBlueprintCompat() {
    }

    public static void captureHoneyGlue(
            ServerLevel level,
            PlotBlockTransform transform,
            CompoundTag plotTag
    ) {
        ListTag glueEntries = new ListTag();
        for (Entity entity : transform.findEntities(level)) {
            if (!HONEY_GLUE_ENTITY_ID.equals(String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())))) {
                continue;
            }
            AABB box = entity.getBoundingBox();
            Vector3d localMin = transform.toSavedLocalPosition(new Vector3d(box.minX, box.minY, box.minZ));
            Vector3d localMax = transform.toSavedLocalPosition(new Vector3d(box.maxX, box.maxY, box.maxZ));
            CompoundTag glueTag = new CompoundTag();
            glueTag.put("From", writeVector(localMin));
            glueTag.put("To", writeVector(localMax));
            glueEntries.add(glueTag);
        }
        if (!glueEntries.isEmpty()) {
            plotTag.put(HONEY_GLUE_TAG, glueEntries);
        }
    }

    public static List<CompoundTag> readHoneyGlueEntries(CompoundTag sublevelTag) {
        if (!sublevelTag.contains(PLOT_TAG, Tag.TAG_COMPOUND)) {
            return List.of();
        }
        CompoundTag plotTag = sublevelTag.getCompound(PLOT_TAG);
        if (!plotTag.contains(HONEY_GLUE_TAG, Tag.TAG_LIST)) {
            return List.of();
        }
        ListTag glueList = plotTag.getList(HONEY_GLUE_TAG, Tag.TAG_COMPOUND);
        List<CompoundTag> result = new ArrayList<>(glueList.size());
        for (int i = 0; i < glueList.size(); i++) {
            result.add(glueList.getCompound(i).copy());
        }
        return result;
    }

    public static void restoreHoneyGlue(ServerLevel level, Collection<LoadedSubLevel> loadedSublevels) {
        Constructor<?> constructor = null;
        for (LoadedSubLevel loaded : loadedSublevels) {
            for (CompoundTag glueTag : loaded.saved().honeyGlueEntries()) {
                if (constructor == null) {
                    constructor = resolveConstructor();
                }
                Vector3d localMin = readVector(glueTag, "From");
                Vector3d localMax = readVector(glueTag, "To");
                Vector3d globalMin = LoadedSubLevelCoordinates.toGlobalPosition(loaded, localMin);
                Vector3d globalMax = LoadedSubLevelCoordinates.toGlobalPosition(loaded, localMax);
                Entity glueEntity = createEntity(constructor, level, new AABB(
                        globalMin.x, globalMin.y, globalMin.z,
                        globalMax.x, globalMax.y, globalMax.z
                ));
                if (!level.addFreshEntity(glueEntity)) {
                    throw new IllegalStateException("server rejected restored Simulated honey glue entity");
                }
            }
        }
    }

    private static Constructor<?> resolveConstructor() {
        try {
            return Class.forName(HONEY_GLUE_ENTITY_CLASS)
                    .getConstructor(net.minecraft.world.level.Level.class, AABB.class);
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new IllegalStateException("Simulated honey glue adapter is unavailable", exception);
        }
    }

    private static Entity createEntity(Constructor<?> constructor, ServerLevel level, AABB bounds) {
        try {
            Object instance = constructor.newInstance(level, bounds);
            if (instance instanceof Entity entity) {
                return entity;
            }
            throw new IllegalStateException("Simulated honey glue constructor returned an incompatible value");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("failed to create Simulated honey glue entity", exception);
        }
    }

    private static ListTag writeVector(Vector3d vector) {
        requireFinite(vector, "captured Simulated honey glue vector");
        ListTag list = new ListTag();
        list.add(DoubleTag.valueOf(vector.x));
        list.add(DoubleTag.valueOf(vector.y));
        list.add(DoubleTag.valueOf(vector.z));
        return list;
    }

    private static Vector3d readVector(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_LIST)) {
            throw new IllegalArgumentException("missing Simulated honey glue vector '" + key + "'");
        }
        ListTag list = tag.getList(key, Tag.TAG_DOUBLE);
        if (list.size() != 3) {
            throw new IllegalArgumentException("invalid Simulated honey glue vector '" + key + "'");
        }
        Vector3d vector = new Vector3d(list.getDouble(0), list.getDouble(1), list.getDouble(2));
        requireFinite(vector, "Simulated honey glue vector '" + key + "'");
        return vector;
    }

    private static void requireFinite(Vector3d vector, String label) {
        if (!Double.isFinite(vector.x) || !Double.isFinite(vector.y) || !Double.isFinite(vector.z)) {
            throw new IllegalArgumentException(label + " must be finite");
        }
    }
}
