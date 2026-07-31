package com.enxv.aeronauticsstructuretool.compat.offroad;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.compat.common.BlockEntityRefreshSupport;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class OffroadBlueprintCompat {
    public static final String WHEEL_MOUNT_BLOCK_ENTITY_ID = "offroad:wheel_mount";

    private OffroadBlueprintCompat() {
    }

    public static void refreshWheelMount(BlockEntity blockEntity) {
        Method onStackChanged = findNoArgMethod(blockEntity.getClass(), "onStackChanged");
        if (onStackChanged == null) {
            AeronauticsStructureToolMod.LOGGER.warn(
                    "Offroad wheel mount {} at {} has no onStackChanged method",
                    blockEntity.getClass().getName(),
                    blockEntity.getBlockPos()
            );
        } else {
            try {
                onStackChanged.invoke(blockEntity);
            } catch (ReflectiveOperationException | LinkageError exception) {
                Throwable cause = exception instanceof InvocationTargetException invocation
                        && invocation.getCause() != null
                        ? invocation.getCause()
                        : exception;
                AeronauticsStructureToolMod.LOGGER.warn(
                        "Offroad wheel mount refresh failed for {} at {}",
                        blockEntity.getClass().getName(),
                        blockEntity.getBlockPos(),
                        cause
                );
            }
        }
        BlockEntityRefreshSupport.refresh(blockEntity);
    }

    private static Method findNoArgMethod(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == 0) {
                    method.setAccessible(true);
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }
}
