package com.backrooms.block;

import com.backrooms.block.entity.FluorescentLightBlockEntity;
import com.backrooms.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** Port dari {@code com.sp.block.custom.FluorescentLightBlock}: panel neon plafon level 0. */
public class FluorescentLightBlock extends BaseEntityBlock {

    public FluorescentLightBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(Lights.BLACKOUT, false).setValue(Lights.ON, true).setValue(Lights.COPY, false));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState();
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // Saat menyala digambar oleh block entity renderer (shader Veil), seperti aslinya.
        return Lights.lit(state) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluorescentLightBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.FLUORESCENT_LIGHT.get(), (l, pos, s, be) -> be.tick(l, pos, s));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(Lights.ON, Lights.COPY, Lights.BLACKOUT);
    }
}
