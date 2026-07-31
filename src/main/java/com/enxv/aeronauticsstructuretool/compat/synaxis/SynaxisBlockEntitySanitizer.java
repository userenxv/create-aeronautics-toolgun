package com.enxv.aeronauticsstructuretool.compat.synaxis;

import com.enxv.aeronauticsstructuretool.blueprint.compat.BlockEntityCompatibilityPipeline;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.io.IOException;
import java.lang.reflect.Method;

final class SynaxisBlockEntitySanitizer {
    private static final String NETWORK_BLOCK_ENTITY =
            "com.verr1.synaxis.foundation.blockentity.NetworkBlockEntity";

    private SynaxisBlockEntitySanitizer() {
    }

    static void sanitizeForSave(ServerLevel level, CompoundTag plotTag) throws IOException {
        if (!SynaxisReflection.isInstalled()) {
            return;
        }
        Class<?> networkType = SynaxisReflection.requireClass(NETWORK_BLOCK_ENTITY);
        IOException[] failure = new IOException[1];
        BlockEntityCompatibilityPipeline.forEachBlockEntity(plotTag, blockEntityTag -> {
            if (failure[0] != null) {
                return;
            }
            BlockEntity blockEntity = level.getBlockEntity(readBlockEntityPos(blockEntityTag));
            if (blockEntity == null || !networkType.isInstance(blockEntity)) {
                return;
            }
            blockEntityTag.remove("synaxis_state");
            blockEntityTag.remove("synaxis_redstone");
            blockEntityTag.remove("synaxis_companion_blueprint");
            Method writeSafe = SynaxisReflection.findMethod(blockEntity.getClass(), "writeSafe", 2);
            if (writeSafe == null) {
                failure[0] = new IOException(
                        "Synaxis block entity " + blockEntity.getClass().getName() + " has no writeSafe method"
                );
                return;
            }
            try {
                SynaxisReflection.invokeMethod(writeSafe, blockEntity, blockEntityTag, level.registryAccess());
            } catch (IOException exception) {
                failure[0] = new IOException(
                        "Failed to sanitize Synaxis block entity at " + blockEntity.getBlockPos(),
                        exception
                );
            }
        });
        if (failure[0] != null) {
            throw failure[0];
        }
    }

    private static BlockPos readBlockEntityPos(CompoundTag tag) {
        return new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
    }
}
