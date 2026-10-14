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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Port dari {@code com.sp.block.custom.PoolTileWall}: dinding keramik tipis, type=1 jadi sudut. */
public class PoolTileWallBlock extends HorizontalDirectionalBlock {

    public static final IntegerProperty TYPE = IntegerProperty.create("type", 0, 1);

    public PoolTileWallBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean corner = state.getValue(TYPE) == 1;
        return switch (state.getValue(FACING)) {
            case NORTH -> corner
                    ? Shapes.or(box(0, 0, 9.6D, 16, 16, 16), box(9.6D, 0, 0, 16, 16, 16))
                    : box(0, 0, 9.6D, 16, 16, 16);
            case WEST -> corner
                    ? Shapes.or(box(9.6D, 0, 0, 16, 16, 16), box(0, 0, 0, 16, 16, 6.4D))
                    : box(9.6D, 0, 0, 16, 16, 16);
            case EAST -> corner
                    ? Shapes.or(box(0, 0, 0, 6.4D, 16, 16), box(0, 0, 9.6D, 16, 16, 16))
                    : box(0, 0, 0, 6.4D, 16, 16);
            default -> corner
                    ? Shapes.or(box(0, 0, 0, 16, 16, 6.4D), box(0, 0, 0, 6.4D, 16, 16))
                    : box(0, 0, 0, 16, 16, 6.4D);
        };
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState current = ctx.getLevel().getBlockState(ctx.getClickedPos());
        if (current.is(this)) {
            return current.setValue(TYPE, Math.min(1, current.getValue(TYPE) + 1))
                    .setValue(FACING, current.getValue(FACING));
        }
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext ctx) {
        return !ctx.isSecondaryUseActive() && ctx.getItemInHand().is(asItem()) && state.getValue(TYPE) < 1
                || super.canBeReplaced(state, ctx);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TYPE);
    }
}
