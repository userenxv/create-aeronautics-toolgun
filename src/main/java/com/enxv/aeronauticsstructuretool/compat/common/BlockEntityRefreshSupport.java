package com.enxv.aeronauticsstructuretool.compat.common;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class BlockEntityRefreshSupport {
    private static final String COPYCAT_INTERFACE =
            "com.copycatsplus.copycats.foundation.copycat.ICopycatBlockEntity";

    private BlockEntityRefreshSupport() {
    }

    public static void refresh(BlockEntity blockEntity) {
        try {
            blockEntity.requestModelDataUpdate();
        } catch (RuntimeException | LinkageError exception) {
            AeronauticsStructureToolMod.LOGGER.warn(
                    "Block entity model-data refresh failed for {} at {}",
                    blockEntity.getClass().getName(),
                    blockEntity.getBlockPos(),
                    exception
            );
        }

        boolean notified = notifyCopycatInterface(blockEntity);
        if (!notified && blockEntity instanceof SmartBlockEntity smartBlockEntity) {
            try {
                smartBlockEntity.notifyUpdate();
                notified = true;
            } catch (RuntimeException | LinkageError exception) {
                AeronauticsStructureToolMod.LOGGER.warn(
                        "Create block entity notifyUpdate failed for {} at {}",
                        blockEntity.getClass().getName(),
                        blockEntity.getBlockPos(),
                        exception
                );
            }
        }

        blockEntity.setChanged();
        if (blockEntity.getLevel() != null) {
            blockEntity.getLevel().sendBlockUpdated(
                    blockEntity.getBlockPos(),
                    blockEntity.getBlockState(),
                    blockEntity.getBlockState(),
                    3
            );
        } else if (!notified) {
            AeronauticsStructureToolMod.LOGGER.warn(
                    "Block entity refresh could not send a level update for {} at {}",
                    blockEntity.getClass().getName(),
                    blockEntity.getBlockPos()
            );
        }
    }

    private static boolean notifyCopycatInterface(BlockEntity blockEntity) {
        Class<?> copycatType;
        try {
            copycatType = Class.forName(COPYCAT_INTERFACE);
        } catch (ClassNotFoundException | LinkageError unavailable) {
            return false;
        }
        if (!copycatType.isInstance(blockEntity)) {
            return false;
        }
        try {
            Method notifyUpdate = copycatType.getMethod("notifyUpdate");
            notifyUpdate.invoke(blockEntity);
            return true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            Throwable cause = exception instanceof InvocationTargetException invocation
                    && invocation.getCause() != null
                    ? invocation.getCause()
                    : exception;
            AeronauticsStructureToolMod.LOGGER.warn(
                    "Copycats notifyUpdate failed for {} at {}",
                    blockEntity.getClass().getName(),
                    blockEntity.getBlockPos(),
                    cause
            );
            return false;
        }
    }
}
