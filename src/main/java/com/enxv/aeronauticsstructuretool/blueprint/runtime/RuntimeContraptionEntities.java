package com.enxv.aeronauticsstructuretool.blueprint.runtime;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.compat.cbc.CbcRuntimeContraptionCompat;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Field;

public final class RuntimeContraptionEntities {
    private RuntimeContraptionEntities() {
    }

    public static boolean isSupported(Entity entity) {
        return entity instanceof ControlledContraptionEntity
                || CbcRuntimeContraptionCompat.isPitchEntity(entity);
    }

    public static BlockPos controllerPos(Entity entity) {
        try {
            Field field = findControllerField(entity.getClass());
            if (field == null) {
                AeronauticsStructureToolMod.LOGGER.warn(
                        "Supported runtime contraption {} has no controllerPos field",
                        entity.getClass().getName()
                );
                return null;
            }
            Object value = field.get(entity);
            if (value instanceof BlockPos pos) {
                return pos;
            }
            AeronauticsStructureToolMod.LOGGER.warn(
                    "Runtime contraption {} has an incompatible controllerPos value",
                    entity.getClass().getName()
            );
            return null;
        } catch (ReflectiveOperationException exception) {
            AeronauticsStructureToolMod.LOGGER.warn(
                    "Failed to inspect runtime contraption controller for {}",
                    entity.getType(),
                    exception
            );
            return null;
        }
    }

    public static int purgeForController(ServerLevel level, BlockPos controllerPos) {
        int removed = 0;
        for (net.minecraft.world.level.entity.EntityAccess access : level.getEntities().getAll()) {
            if (!(access instanceof Entity entity) || !entity.isAlive() || !isSupported(entity)) {
                continue;
            }
            if (!controllerPos.equals(controllerPos(entity))) {
                continue;
            }
            entity.discard();
            removed++;
        }
        if (removed > 0) {
            AeronauticsStructureToolMod.LOGGER.info(
                    "Purged {} stale runtime contraption entities for controller {}",
                    removed,
                    controllerPos
            );
        }
        return removed;
    }

    private static Field findControllerField(Class<?> type) {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField("controllerPos");
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException notDeclaredOnCurrentType) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
