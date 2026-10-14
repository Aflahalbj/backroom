package com.backrooms.block.pipe;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Port dari {@code com.sp.block.custom.pipes.PipeCorner}: belokan pipa, facing
 * 6 arah, {@code type} 0..2 naik tiap kali ditempel ulang di blok yang sama.
 *
 * <p>Aslinya extend {@code HorizontalFacingBlock} tapi memakai facing 6 arah
 * dan meng-override rotate/mirror sendiri, jadi perilakunya sama dengan blok
 * biasa yang memutar facing-nya seperti di sini.
 */
public class PipeCornerBlock extends Block {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final IntegerProperty TYPE = IntegerProperty.create("type", 0, 2);
    private static final VoxelShape SHAPE = Block.box(1.0D, 1.0D, 1.0D, 15.0D, 15.0D, 15.0D);

    public PipeCornerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState current = ctx.getLevel().getBlockState(ctx.getClickedPos());
        if (current.is(this)) {
            return current.setValue(TYPE, Math.min(2, current.getValue(TYPE) + 1))
                    .setValue(FACING, current.getValue(FACING));
        }
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext ctx) {
        return !ctx.isSecondaryUseActive() && ctx.getItemInHand().is(asItem()) && state.getValue(TYPE) < 2
                || super.canBeReplaced(state, ctx);
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
        builder.add(FACING, TYPE);
    }
}
