package com.backrooms.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Port dari {@code com.sp.block.custom.NewStairs}: anak tangga beton satu blok. */
public class NewStairsBlock extends HorizontalDirectionalBlock {

    private static final VoxelShape SHAPE_SOUTH = Shapes.or(Block.box(0.0D, 0.0D, 0.0D, 16.0D, 6.0D, 16.0D),
            Block.box(0.0D, 6.0D, 5.0D, 16.0D, 11.0D, 11.0D), Block.box(0.0D, 6.0D, 11.0D, 16.0D, 16.0D, 16.0D));
    private static final VoxelShape SHAPE_NORTH = Shapes.or(Block.box(0.0D, 0.0D, 0.0D, 16.0D, 6.0D, 16.0D),
            Block.box(0.0D, 6.0D, 5.0D, 16.0D, 11.0D, 11.0D), Block.box(0.0D, 6.0D, 0.0D, 16.0D, 16.0D, 5.0D));
    private static final VoxelShape SHAPE_WEST = Shapes.or(Block.box(0.0D, 0.0D, 0.0D, 16.0D, 6.0D, 16.0D),
            Block.box(5.0D, 6.0D, 0.0D, 11.0D, 11.0D, 16.0D), Block.box(0.0D, 6.0D, 0.0D, 5.0D, 16.0D, 16.0D));
    private static final VoxelShape SHAPE_EAST = Shapes.or(Block.box(0.0D, 0.0D, 0.0D, 16.0D, 6.0D, 16.0D),
            Block.box(5.0D, 6.0D, 0.0D, 11.0D, 11.0D, 16.0D), Block.box(11.0D, 6.0D, 0.0D, 16.0D, 16.0D, 16.0D));

    public NewStairsBlock(Properties properties) {
        super(properties);
    }

    // Pemetaan facing -> bentuk sengaja "terbalik" persis seperti aslinya.
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_EAST;
            case EAST -> SHAPE_WEST;
            default -> SHAPE_NORTH;
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
