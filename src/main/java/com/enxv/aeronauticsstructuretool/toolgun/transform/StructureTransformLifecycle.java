package com.enxv.aeronauticsstructuretool.toolgun.transform;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(modid = AeronauticsStructureToolMod.MOD_ID)
public final class StructureTransformLifecycle {
    private StructureTransformLifecycle() {
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level) {
            StructureTransformService.discardPlayer(level, event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            StructureTransformService.discardDimension(level);
        }
    }
}
