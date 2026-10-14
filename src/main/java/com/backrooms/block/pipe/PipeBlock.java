package com.backrooms.block.pipe;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Port dari {@code com.sp.block.custom.pipes.Pipe}: pipa lurus 6 arah, dipakai
 * {@code pipe}, {@code pipe_middle}, {@code big_pipe}, {@code big_pipe_middle}.
 */
public class PipeBlock extends DirectionalBlock {

    private static final VoxelShape ALONG_X = Block.box(0.0D, 2.0D, 2.0D, 16.0D, 16.0D, 14.0D);
    private static final VoxelShape ALONG_Z = Block.box(2.0D, 2.0D, 0.0D, 14.0D, 14.0D, 16.0D);
    private static final VoxelShape VERTICAL = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 16.0D, 14.0D);

    public PipeBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case UP, DOWN -> VERTICAL;
            case WEST, EAST -> ALONG_X;
            default -> ALONG_Z;
        };
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getNearestLookingDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
