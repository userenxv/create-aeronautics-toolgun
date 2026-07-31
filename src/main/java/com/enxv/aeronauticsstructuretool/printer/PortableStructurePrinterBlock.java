package com.enxv.aeronauticsstructuretool.printer;

import com.enxv.aeronauticsstructuretool.ModBlockEntities;
import com.enxv.aeronauticsstructuretool.ModItems;
import com.enxv.aeronauticsstructuretool.OpenPortableStructurePrinterPayload;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PortableStructurePrinterBlock extends BaseEntityBlock {
    public static final MapCodec<PortableStructurePrinterBlock> CODEC = simpleCodec(PortableStructurePrinterBlock::new);
    private static final VoxelShape SHAPE = box(4.0D, 0.0D, 4.0D, 12.0D, 8.25D, 12.0D);

    public PortableStructurePrinterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.player.Player player, BlockHitResult hitResult) {
        return openPrinter(level, pos, player);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        InteractionResult result = openPrinter(level, pos, player);
        if (result == InteractionResult.SUCCESS || result == InteractionResult.CONSUME) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private static InteractionResult openPrinter(Level level, BlockPos pos, Player player) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.CONSUME;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof PortableStructurePrinterBlockEntity printer)) {
            return InteractionResult.FAIL;
        }
        PacketDistributor.sendToPlayer(serverPlayer, new OpenPortableStructurePrinterPayload(
                pos,
                printer.blueprintDisplayName(),
                printer.hasBlueprint()
        ));
        PortableStructurePrinterService.syncState(serverPlayer, printer);
        return InteractionResult.CONSUME;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PortableStructurePrinterBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide || blockEntityType != ModBlockEntities.PORTABLE_STRUCTURE_PRINTER.get()) {
            return null;
        }
        return (tickerLevel, tickerPos, tickerState, blockEntity) -> {
            if (blockEntity instanceof PortableStructurePrinterBlockEntity printer && tickerLevel instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                PortableStructurePrinterService.tick(serverLevel, tickerPos, printer);
            }
        };
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && !movedByPiston) {
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel
                    && level.getBlockEntity(pos) instanceof PortableStructurePrinterBlockEntity printer) {
                PortableStructurePrinterService.refundReservedMaterials(
                        serverLevel,
                        pos,
                        printer,
                        "the printer block was removed"
                );
            }
            popResource(level, pos, new ItemStack(ModItems.PORTABLE_STRUCTURE_CONTAINER.get()));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected boolean isPathfindable(BlockState state, net.minecraft.world.level.pathfinder.PathComputationType pathComputationType) {
        return false;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return false;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
        return true;
    }
}
