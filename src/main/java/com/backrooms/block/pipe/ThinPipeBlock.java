package com.backrooms.block.pipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Bentuk tipis yang bisa menempel di lantai, dinding, atau langit-langit --
 * dipakai {@code thin_pipe} dan sebagai dasar lampu neon tipis. Bentuk dan
 * aturan pemasangannya disalin dari {@code ThinPipe} dan
 * {@code ThinFluorescentLightBlock} asli, yang isinya identik.
 */
public class ThinPipeBlock extends Block {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final EnumProperty<AttachFace> FACE = BlockStateProperties.ATTACH_FACE;

    private static final VoxelShape FLOOR_X = Block.box(0.0D, 0.0D, 6.0D, 16.0D, 2.0D, 10.0D);
    private static final VoxelShape FLOOR_Z = Block.box(6.0D, 0.0D, 0.0D, 10.0D, 2.0D, 16.0D);
    private static final VoxelShape EAST_WALL = Block.box(0.0D, 0.0D, 6.0D, 2.0D, 16.0D, 10.0D);
    private static final VoxelShape WEST_WALL = Block.box(14.0D, 0.0D, 6.0D, 16.0D, 16.0D, 10.0D);
    private static final VoxelShape NORTH_WALL = Block.box(6.0D, 0.0D, 14.0D, 10.0D, 16.0D, 16.0D);
    private static final VoxelShape SOUTH_WALL = Block.box(6.0D, 0.0D, 0.0D, 10.0D, 16.0D, 2.0D);
    private static final VoxelShape CEILING_X = Block.box(0.0D, 14.0D, 6.0D, 16.0D, 16.0D, 10.0D);
    private static final VoxelShape CEILING_Z = Block.box(6.0D, 14.0D, 0.0D, 10.0D, 16.0D, 16.0D);

    public ThinPipeBlock(Properties properties) {
        super(properties);
    }

    public static VoxelShape thinShape(BlockState state) {
        Direction facing = state.getValue(FACING);
        return switch (state.getValue(FACE)) {
            case FLOOR -> facing.getAxis() == Direction.Axis.X ? FLOOR_X : FLOOR_Z;
            case WALL -> switch (facing) {
                case EAST -> EAST_WALL;
                case WEST -> WEST_WALL;
                case SOUTH -> SOUTH_WALL;
                default -> NORTH_WALL;
            };
            default -> facing.getAxis() == Direction.Axis.X ? CEILING_X : CEILING_Z;
        };
    }

    /** Arah pemasangan seperti tuas: lantai/plafon ikut arah pandang, dinding menghadap keluar. */
    public static BlockState placeAttached(BlockState base, BlockPlaceContext ctx) {
        for (Direction direction : ctx.getNearestLookingDirections()) {
            BlockState state;
            if (direction.getAxis() == Direction.Axis.Y) {
                state = base.setValue(FACE, direction == Direction.UP ? AttachFace.CEILING : AttachFace.FLOOR)
                        .setValue(FACING, ctx.getHorizontalDirection());
            } else {
                state = base.setValue(FACE, AttachFace.WALL).setValue(FACING, direction.getOpposite());
            }
            if (state.canSurvive(ctx.getLevel(), ctx.getClickedPos())) {
                return state;
            }
        }
        return null;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return thinShape(state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return placeAttached(defaultBlockState(), ctx);
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
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FACE);
    }
}
