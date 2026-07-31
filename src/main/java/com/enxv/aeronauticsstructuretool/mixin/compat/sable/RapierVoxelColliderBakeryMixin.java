package com.enxv.aeronauticsstructuretool.mixin.compat.sable;

import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Backports Sable commit e725aa9c7b29 to 2.0.3. The one-block view exposes
 * the current state only at BlockPos.ZERO.
 */
@Pseudo
@Mixin(
        targets = "dev.ryanhcode.sable.physics.impl.rapier.collider.RapierVoxelColliderBakery",
        remap = false
)
abstract class RapierVoxelColliderBakeryMixin {
    @Redirect(
            method = "buildPhysicsDataForBlock(Lnet/minecraft/world/level/block/state/BlockState;)"
                    + "Ldev/ryanhcode/sable/physics/impl/rapier/collider/RapierVoxelColliderData;",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/api/block/BlockSubLevelCollisionShape;"
                            + "getSubLevelCollisionShape(Lnet/minecraft/world/level/BlockGetter;"
                            + "Lnet/minecraft/world/level/block/state/BlockState;)"
                            + "Lnet/minecraft/world/phys/shapes/VoxelShape;"
            ),
            require = 1,
            remap = false
    )
    private VoxelShape createAeronauticsToolgun$bakeExtensionShapeWithCurrentState(
            BlockSubLevelCollisionShape extension,
            BlockGetter blockGetter,
            BlockState state
    ) {
        PhysicsColliderBlockGetterAccess access = (PhysicsColliderBlockGetterAccess) blockGetter;
        access.createAeronauticsToolgun$setup(state);
        try {
            return extension.getSubLevelCollisionShape(blockGetter, state);
        } finally {
            access.createAeronauticsToolgun$setup(Blocks.AIR.defaultBlockState());
        }
    }

    @Redirect(
            method = "buildPhysicsDataForBlock(Lnet/minecraft/world/level/block/state/BlockState;)"
                    + "Ldev/ryanhcode/sable/physics/impl/rapier/collider/RapierVoxelColliderData;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;getCollisionShape("
                            + "Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;"
                            + "Lnet/minecraft/world/phys/shapes/CollisionContext;)"
                            + "Lnet/minecraft/world/phys/shapes/VoxelShape;"
            ),
            require = 1,
            remap = false
    )
    private VoxelShape createAeronauticsToolgun$bakeVanillaShapeWithCurrentState(
            BlockState state,
            BlockGetter blockGetter,
            BlockPos pos,
            CollisionContext context
    ) {
        PhysicsColliderBlockGetterAccess access = (PhysicsColliderBlockGetterAccess) blockGetter;
        access.createAeronauticsToolgun$setup(state);
        try {
            return state.getCollisionShape(blockGetter, pos, context);
        } finally {
            access.createAeronauticsToolgun$setup(Blocks.AIR.defaultBlockState());
        }
    }
}
