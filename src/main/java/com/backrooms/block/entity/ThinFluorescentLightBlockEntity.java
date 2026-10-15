package com.backrooms.block.entity;

import com.backrooms.block.Lights;
import com.backrooms.block.ModBlocks;
import com.backrooms.world.events.LightState;
import com.backrooms.world.events.WorldEventsManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Port sisi server dari {@code com.sp.block.entity.ThinFluorescentLightBlockEntity}:
 * neon tipis yang bertetangga (utara, barat, lalu bawah) menyalin state
 * tetangganya dengan {@code copy=true}; sisanya mengikuti kondisi lampu level
 * (blackout dan berkedip seperti aslinya, selain itu menyala).
 */
public class ThinFluorescentLightBlockEntity extends BlockEntity {

    public BlockState currentState;
    public final RandomSource random = RandomSource.create();
    public boolean playingSound;
    public boolean prevOn;
    public int ticks = 0;
    public final int randInt = new java.util.Random().nextInt(1, 8);
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
            this.serverTick(level, pos, state);
        } else {
            com.backrooms.client.light.LightTicks.thinFluorescent(level, pos, state, this);
        }
        if (this.ticks > 100) {
            this.ticks = 1;
        }
        BlockState now = level.getBlockState(pos);
        this.prevOn = now.hasProperty(Lights.ON) && now.getValue(Lights.ON);
    }

    private void serverTick(Level level, BlockPos pos, BlockState state) {
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
            // Aslinya level yang tidak dikenal dianggap Poolrooms (tanpa kondisi lampu).
            LightState lightState = WorldEventsManager.lightStateAt((ServerLevel) level, pos.getX());
            boolean blackouted = lightState == LightState.BLACKOUT;
            if (blackouted) {
                level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(Lights.BLACKOUT, true));
                this.playingSound = false;
            } else {
                level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(Lights.BLACKOUT, false));
            }
            if (lightState == LightState.FLICKER && !state.getValue(Lights.BLACKOUT)) {
                if (this.ticks % this.randInt == 0) {
                    int i = RandomSource.create().nextIntBetweenInclusive(1, 2);
                    level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(Lights.ON, i == 1));
                }
            } else if (!state.getValue(Lights.ON)) {
                level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(Lights.ON, true));
            }
        }
    }
}
