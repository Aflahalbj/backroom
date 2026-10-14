package com.backrooms.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Port dari {@code com.sp.block.custom.ChainFence}: pagar kawat setipis 1 piksel. */
public class ChainFenceBlock extends HorizontalDirectionalBlock {

    private static final VoxelShape ALONG_X = Block.box(0.0D, 0.0D, 7.5D, 16.0D, 16.0D, 8.5D);
    private static final VoxelShape ALONG_Z = Block.box(7.5D, 0.0D, 0.0D, 8.5D, 16.0D, 16.0D);

    public ChainFenceBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case EAST, WEST -> ALONG_Z;
            default -> ALONG_X;
        };
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
