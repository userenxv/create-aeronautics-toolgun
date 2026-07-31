package com.enxv.aeronauticsstructuretool;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class StructureToolItem extends Item {
    public static final double MAX_USE_DISTANCE = 256.0D;
    private final boolean survivalRestricted;

    public StructureToolItem(Properties properties, boolean survivalRestricted) {
        super(properties);
        this.survivalRestricted = survivalRestricted;
    }

    public boolean isSurvivalRestricted() {
        return this.survivalRestricted;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!context.getLevel().isClientSide()) {
            return InteractionResult.CONSUME;
        }
        AeronauticsStructureToolMod.LOGGER.debug(
                "Structure tool useOn: hand={}, pos={}, face={}",
                context.getHand(),
                context.getClickedPos(),
                context.getClickedFace()
        );
        handleClientBlockHit(
                context.getHand(),
                new BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), context.isInside()),
                context.getLevel()
        );
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (!level.isClientSide()) {
            return InteractionResultHolder.consume(stack);
        }

        HitResult hit = player.pick(MAX_USE_DISTANCE, 0.0F, false);
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            AeronauticsStructureToolMod.LOGGER.debug(
                    "Structure tool use: hand={}, pos={}, face={}",
                    usedHand,
                    blockHit.getBlockPos(),
                    blockHit.getDirection()
            );
            handleClientBlockHit(usedHand, blockHit, level);
            return InteractionResultHolder.consume(stack);
        }

        handleClientMiss(usedHand, level, hit.getLocation());
        return InteractionResultHolder.consume(stack);
    }

    private static void handleClientBlockHit(InteractionHand hand, BlockHitResult hit, Level level) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        try {
            Class<?> clientHandler = Class.forName("com.enxv.aeronauticsstructuretool.client.tool.ClientStructureToolHandler");
            clientHandler.getMethod("handleBlockHit", InteractionHand.class, BlockHitResult.class, Level.class).invoke(null, hand, hit, level);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to dispatch client structure tool interaction", exception);
        }
    }

    private static void handleClientMiss(InteractionHand hand, Level level, net.minecraft.world.phys.Vec3 target) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        try {
            Class<?> clientHandler = Class.forName("com.enxv.aeronauticsstructuretool.client.tool.ClientStructureToolHandler");
            clientHandler.getMethod("handleMiss", InteractionHand.class, Level.class, net.minecraft.world.phys.Vec3.class).invoke(null, hand, level, target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to dispatch client structure tool miss interaction", exception);
        }
    }
}
