package com.enxv.aeronauticsstructuretool.mixin.compat.sable;

import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(
        targets = "dev.ryanhcode.sable.physics.impl.rapier.collider.PhysicsColliderBlockGetter",
        remap = false
)
public interface PhysicsColliderBlockGetterAccess {
    @Invoker(value = "setup", remap = false)
    void createAeronauticsToolgun$setup(BlockState state);
}
