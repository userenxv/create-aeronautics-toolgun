package com.enxv.aeronauticsstructuretool.mixin.compat.letitwalk;

import com.enxv.aeronauticsstructuretool.compat.letitwalk.LetItWalkNbt;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Pseudo
@Mixin(targets = "dev.letitwalk.content.WalkerCoreBlockEntity", remap = false)
abstract class WalkerCoreBlockEntityMixin {
    @Shadow private UUID walkerId;

    @Inject(method = "read", at = @At("TAIL"), remap = false)
    private void createAeronauticsToolgun$restoreCopyIdentity(
            CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci
    ) {
        if (!clientPacket && tag.hasUUID(LetItWalkNbt.COPY_ID)) {
            this.walkerId = tag.getUUID(LetItWalkNbt.COPY_ID);
            tag.remove(LetItWalkNbt.COPY_ID);
        }
    }
}
