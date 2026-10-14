package com.backrooms.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Port dari {@code com.sp.block.custom.PoolTileSlopeBlock}: lereng keramik kolam (bisa terendam). */
public class PoolTileSlopeBlock extends Block implements SimpleWaterloggedBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    private static final VoxelShape SHAPE_NORTH = Shapes.or(box(0, 0, 0, 16, 4, 16),
            box(0, 4, 4, 16, 8, 16), box(0, 8, 8, 16, 12, 16), box(0, 12, 12, 16, 16, 16));
    private static final VoxelShape SHAPE_SOUTH = Shapes.or(box(0, 0, 0, 16, 4, 16),
            box(0, 4, 0, 16, 8, 12), box(0, 8, 0, 16, 12, 8), box(0, 12, 0, 16, 16, 4));
    private static final VoxelShape SHAPE_WEST = Shapes.or(box(0, 0, 0, 16, 4, 16),
            box(4, 4, 0, 16, 8, 16), box(8, 8, 0, 16, 12, 16), box(12, 12, 0, 16, 16, 16));
    private static final VoxelShape SHAPE_EAST = Shapes.or(box(0, 0, 0, 16, 4, 16),
            box(0, 4, 0, 12, 8, 16), box(0, 8, 0, 8, 12, 16), box(0, 12, 0, 4, 16, 16));
    private static final VoxelShape SHAPE_TOP_NORTH = Shapes.or(box(0, 12, 0, 16, 16, 16),
            box(0, 8, 4, 16, 12, 16), box(0, 4, 8, 16, 8, 16), box(0, 0, 12, 16, 4, 16));
    private static final VoxelShape SHAPE_TOP_SOUTH = Shapes.or(box(0, 12, 0, 16, 16, 16),
            box(0, 8, 0, 16, 12, 12), box(0, 4, 0, 16, 8, 8), box(0, 0, 0, 16, 4, 4));
    private static final VoxelShape SHAPE_TOP_WEST = Shapes.or(box(0, 12, 0, 16, 16, 16),
            box(4, 8, 0, 16, 12, 16), box(8, 4, 0, 16, 8, 16), box(12, 0, 0, 16, 4, 16));
    private static final VoxelShape SHAPE_TOP_EAST = Shapes.or(box(0, 12, 0, 16, 16, 16),
            box(0, 8, 0, 12, 12, 16), box(0, 4, 0, 8, 8, 16), box(0, 0, 0, 4, 4, 16));

    public PoolTileSlopeBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean top = state.getValue(HALF) == Half.TOP;
        return switch (state.getValue(FACING)) {
            case NORTH -> top ? SHAPE_TOP_NORTH : SHAPE_NORTH;
            case WEST -> top ? SHAPE_TOP_WEST : SHAPE_WEST;
            case EAST -> top ? SHAPE_TOP_EAST : SHAPE_EAST;
            default -> top ? SHAPE_TOP_SOUTH : SHAPE_SOUTH;
        };
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction side = ctx.getClickedFace();
        BlockPos pos = ctx.getClickedPos();
        boolean top = side == Direction.DOWN
                || side != Direction.UP && ctx.getClickLocation().y - pos.getY() > 0.5D;
        return defaultBlockState()
                .setValue(FACING, ctx.getHorizontalDirection().getOpposite())
                .setValue(HALF, top ? Half.TOP : Half.BOTTOM)
                .setValue(WATERLOGGED, ctx.getLevel().getFluidState(pos).is(Fluids.WATER));
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, WATERLOGGED);
    }
}
