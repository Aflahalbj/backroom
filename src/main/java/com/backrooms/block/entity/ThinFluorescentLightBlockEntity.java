package com.backrooms.block.entity;

import com.backrooms.block.Lights;
import com.backrooms.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Port sisi server dari {@code com.sp.block.entity.ThinFluorescentLightBlockEntity}:
 * neon tipis yang bertetangga (utara, barat, lalu bawah) menyalin state
 * tetangganya dengan {@code copy=true}; sisanya selalu dikembalikan ke
 * menyala dan tidak blackout, karena event lampu aslinya tidak di-port.
 */
public class ThinFluorescentLightBlockEntity extends BlockEntity {

    public BlockState currentState;
    public final RandomSource random = RandomSource.create();
    public boolean playingSound;
    public boolean prevOn;
    public int ticks = 0;
    /** Lampu Veil di klien (lihat {@code com.backrooms.client.light.LightTicks}). */
    public Object clientLight;

    public ThinFluorescentLightBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.THIN_FLUORESCENT_LIGHT.get(), pos, state);
        this.currentState = state;
    }

    @Override
    public void setRemoved() {
        if (this.level != null && this.level.isClientSide) {
            com.backrooms.client.light.LightTicks.remove(this);
        }
        super.setRemoved();
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (!level.getBlockState(pos).is(ModBlocks.THIN_FLUORESCENT_LIGHT.get())) return;
        this.currentState = state;
        ++this.ticks;
        if (!level.isClientSide) {
            serverTick(level, pos, state);
        } else {
            com.backrooms.client.light.LightTicks.thinFluorescent(level, pos, state, this);
        }
        if (this.ticks > 100) {
            this.ticks = 1;
        }
        BlockState now = level.getBlockState(pos);
        this.prevOn = now.hasProperty(Lights.ON) && now.getValue(Lights.ON);
    }

    private static void serverTick(Level level, BlockPos pos, BlockState state) {
        Block thin = ModBlocks.THIN_FLUORESCENT_LIGHT.get();

        BlockState north = level.getBlockState(pos.north());
        BlockState west = level.getBlockState(pos.west());
        BlockState down = level.getBlockState(pos.below());
        if (north.is(thin)) {
            level.setBlockAndUpdate(pos, north.setValue(Lights.COPY, true));
        } else if (west.is(thin)) {
            level.setBlockAndUpdate(pos, west.setValue(Lights.COPY, true));
        } else if (down.is(thin)) {
            level.setBlockAndUpdate(pos, down.setValue(Lights.COPY, true));
        } else {
            if (state.getValue(Lights.COPY)) {
                level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(Lights.COPY, false));
            }
            level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(Lights.BLACKOUT, false));
            if (!state.getValue(Lights.ON)) {
                level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(Lights.ON, true));
            }
        }
    }
}
