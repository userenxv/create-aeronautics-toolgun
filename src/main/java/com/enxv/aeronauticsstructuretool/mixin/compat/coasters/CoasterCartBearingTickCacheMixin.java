package com.enxv.aeronauticsstructuretool.mixin.compat.coasters;

import com.enxv.aeronauticsstructuretool.compat.coasters.CoastersCartCacheRefresh;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.Map;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.silvergold.simulatedcoasters.track.cart.CoasterCartBearingTickCache", remap = false)
abstract class CoasterCartBearingTickCacheMixin {
    @Shadow @Final private static Map<ServerSubLevel, ?> BY_SUB;

    @Inject(method = "scan", at = @At("HEAD"), remap = false)
    private static void createAeronauticsToolgun$refreshLoadedPlot(ServerSubLevel sub, CallbackInfoReturnable<?> ci) {
        if (CoastersCartCacheRefresh.consumeBody(sub.getUniqueId())) BY_SUB.remove(sub);
    }
}
