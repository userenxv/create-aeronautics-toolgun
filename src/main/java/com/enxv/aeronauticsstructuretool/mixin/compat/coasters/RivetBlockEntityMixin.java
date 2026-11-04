package com.enxv.aeronauticsstructuretool.mixin.compat.coasters;

import com.enxv.aeronauticsstructuretool.compat.coasters.CoastersPlacementScope;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.silvergold.simulatedcoasters.rivet.RivetBlockEntity", remap = false)
abstract class RivetBlockEntityMixin {
    @Inject(method = "applyHostLock", at = @At("HEAD"), cancellable = true, remap = false)
    private void createAeronauticsToolgun$waitForHostPlots(ServerSubLevel rivet, CallbackInfo ci) {
        if (CoastersPlacementScope.defers(rivet.getUniqueId())) ci.cancel();
    }
}
