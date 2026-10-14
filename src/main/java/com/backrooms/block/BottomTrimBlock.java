package com.backrooms.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Port dari {@code com.sp.block.custom.BottomTrim}: list bawah dinding.
 * Menumpuk sampai 4 sisi kalau ditempel ulang di blok yang sama.
 */
public class BottomTrimBlock extends HorizontalDirectionalBlock {

    public static final IntegerProperty SIDES = IntegerProperty.create("sides", 1, 4);

    public BottomTrimBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState current = ctx.getLevel().getBlockState(ctx.getClickedPos());
        if (current.is(this)) {
            return current.setValue(SIDES, Math.min(4, current.getValue(SIDES) + 1))
                    .setValue(FACING, current.getValue(FACING));
        }
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> Block.box(0.0D, 0.0D, 15.0D, 16.0D, 2.0D, 16.0D);
            case WEST -> Block.box(15.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D);
            case EAST -> Block.box(0.0D, 0.0D, 0.0D, 1.0D, 2.0D, 16.0D);
            default -> Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 1.0D);
        };
    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext ctx) {
        return !ctx.isSecondaryUseActive() && ctx.getItemInHand().is(asItem()) && state.getValue(SIDES) < 4
                || super.canBeReplaced(state, ctx);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SIDES);
    }
}
