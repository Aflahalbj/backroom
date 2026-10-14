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

/**
 * Port dari {@code com.sp.block.custom.WallText}: semua tulisan, panah, dan
 * gambar yang menempel di dinding (wall_text_*, arrow*, wall_small_*,
 * wall_drawing_*). Ikut berputar bersama strukturnya.
 */
public class WallTextBlock extends HorizontalDirectionalBlock {

    private static final VoxelShape SOUTH = Block.box(1.0D, 1.0D, 0.0D, 15.0D, 15.0D, 1.0D);
    private static final VoxelShape NORTH = Block.box(1.0D, 1.0D, 15.0D, 15.0D, 15.0D, 16.0D);
    private static final VoxelShape WEST = Block.box(15.0D, 1.0D, 1.0D, 16.0D, 15.0D, 15.0D);
    private static final VoxelShape EAST = Block.box(0.0D, 1.0D, 1.0D, 1.0D, 15.0D, 15.0D);

    public WallTextBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH;
            case WEST -> WEST;
            case EAST -> EAST;
            default -> SOUTH;
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
