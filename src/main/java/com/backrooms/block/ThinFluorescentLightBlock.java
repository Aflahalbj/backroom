package com.backrooms.block;

import com.backrooms.block.entity.ModBlockEntities;
import com.backrooms.block.entity.ThinFluorescentLightBlockEntity;
import com.backrooms.block.pipe.ThinPipeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Port dari {@code com.sp.block.custom.ThinFluorescentLightBlock}: neon tipis
 * level 1, 2, dan poolrooms. Bentuk dan cara pasangnya sama dengan pipa tipis.
 */
public class ThinFluorescentLightBlock extends BaseEntityBlock {

    public ThinFluorescentLightBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(Lights.BLACKOUT, false).setValue(Lights.ON, true).setValue(Lights.COPY, false));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return ThinPipeBlock.placeAttached(defaultBlockState()
                .setValue(Lights.ON, true).setValue(Lights.BLACKOUT, false).setValue(Lights.COPY, false), ctx);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return ThinPipeBlock.thinShape(state);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(ThinPipeBlock.FACING, rotation.rotate(state.getValue(ThinPipeBlock.FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(ThinPipeBlock.FACING)));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // Saat menyala digambar oleh block entity renderer (shader Veil), seperti aslinya.
        return Lights.lit(state) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ThinFluorescentLightBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.THIN_FLUORESCENT_LIGHT.get(), (l, pos, s, be) -> be.tick(l, pos, s));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ThinPipeBlock.FACING, ThinPipeBlock.FACE, Lights.ON, Lights.COPY, Lights.BLACKOUT);
    }
}
