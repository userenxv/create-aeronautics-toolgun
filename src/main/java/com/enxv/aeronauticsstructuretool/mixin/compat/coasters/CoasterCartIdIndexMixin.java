package com.enxv.aeronauticsstructuretool.mixin.compat.coasters;

import com.enxv.aeronauticsstructuretool.compat.coasters.CoastersCartCacheRefresh;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import java.util.Map;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.silvergold.simulatedcoasters.track.cart.CoasterCartIdIndex", remap = false)
abstract class CoasterCartIdIndexMixin {
    @Shadow @Final private static Map<ResourceKey<Level>, ?> BY_DIM;

    @Inject(method = "ensureFresh", at = @At("HEAD"), remap = false)
    private static void createAeronauticsToolgun$refreshLoadedCarts(ServerLevel level, CallbackInfo ci) {
        if (CoastersCartCacheRefresh.consumeIndex(level.dimension())) {
            synchronized (BY_DIM) { BY_DIM.remove(level.dimension()); }
        }
    }
}
