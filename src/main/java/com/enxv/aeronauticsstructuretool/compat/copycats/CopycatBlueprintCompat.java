package com.enxv.aeronauticsstructuretool.compat.copycats;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.compat.common.BlockEntityRefreshSupport;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class CopycatBlueprintCompat {
    public static final String CREATE_COPYCAT_BLOCK_ENTITY_ID = "create:copycat";
    public static final String COPYCATS_NAMESPACE_PREFIX = "copycats:";

    private CopycatBlueprintCompat() {
    }

    public static boolean supports(String blockEntityId) {
        return CREATE_COPYCAT_BLOCK_ENTITY_ID.equals(blockEntityId)
                || blockEntityId.startsWith(COPYCATS_NAMESPACE_PREFIX);
    }

    public static void refresh(BlockEntity blockEntity) {
        try {
            blockEntity.onLoad();
        } catch (RuntimeException | LinkageError exception) {
            AeronauticsStructureToolMod.LOGGER.warn(
                    "Copycat onLoad refresh failed for {} at {}",
                    blockEntity.getClass().getName(),
                    blockEntity.getBlockPos(),
                    exception
            );
        }
        BlockEntityRefreshSupport.refresh(blockEntity);
    }
}
