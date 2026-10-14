package com.backrooms.block.entity;

import com.backrooms.block.Lights;
import com.backrooms.block.ModBlocks;
import com.backrooms.world.BackroomsLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Port sisi server dari {@code com.sp.block.entity.FluorescentLightBlockEntity}.
 *
 * <ul>
 *   <li>Di level 0, panel neon yang di bawahnya bukan udara (tertimpa dinding)
 *       diganti ceiling tile -- ini yang membuat pola lampu atap mengikuti
 *       bentuk ruangan.</li>
 *   <li>Panel yang bersebelahan (utara/barat) menyalin state tetangganya
 *       dengan {@code copy=true}, seperti aslinya.</li>
 *   <li>Event blackout/flicker aslinya tidak di-port, jadi lampu selalu
 *       kembali menyala.</li>
 * </ul>
 */
public class FluorescentLightBlockEntity extends BlockEntity {

    public BlockState currentState;
    public final RandomSource random = RandomSource.create();
    public final java.util.Random random1 = new java.util.Random();
    public boolean playingSound;
    public boolean prevOn;
    public int ticks = 0;
    /** Lampu Veil di klien (lihat {@code com.backrooms.client.light.LightTicks}). */
    public Object clientLight;

    public FluorescentLightBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLUORESCENT_LIGHT.get(), pos, state);
        this.currentState = state;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.level != null && this.level.isClientSide) {
            this.playingSound = false;
            com.backrooms.client.light.LightTicks.remove(this);
        }
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (!level.getBlockState(pos).is(ModBlocks.FLUORESCENT_LIGHT.get())) return;
        ++this.ticks;
        this.currentState = state;
        if (!level.isClientSide) {
            if (!serverTick(level, pos, state)) return;
        } else {
            com.backrooms.client.light.LightTicks.fluorescent(level, pos, state, this);
        }
        if (this.ticks > 100) {
            this.ticks = 1;
        }
        BlockState now = level.getBlockState(pos);
        this.prevOn = now.hasProperty(Lights.ON) && now.getValue(Lights.ON);
    }

    /** @return false kalau panel ini baru saja diganti ceiling tile. */
    private static boolean serverTick(Level level, BlockPos pos, BlockState state) {
        if (BackroomsLevel.at(level, pos.getX()) == BackroomsLevel.LEVEL0
                && level.getBlockState(pos.below()) != Blocks.AIR.defaultBlockState()) {
            level.setBlockAndUpdate(pos, ModBlocks.CEILING_TILE.get().defaultBlockState());
            return false;
        }

        BlockState north = level.getBlockState(pos.north());
        BlockState west = level.getBlockState(pos.west());
        if (north.is(ModBlocks.FLUORESCENT_LIGHT.get())) {
            level.setBlockAndUpdate(pos, north.setValue(Lights.COPY, true));
        } else if (west.is(ModBlocks.FLUORESCENT_LIGHT.get())) {
            level.setBlockAndUpdate(pos, west.setValue(Lights.COPY, true));
        } else {
            if (state.getValue(Lights.COPY)) {
                level.setBlockAndUpdate(pos, ModBlocks.FLUORESCENT_LIGHT.get().defaultBlockState()
                        .setValue(Lights.COPY, false));
            }
            BlockState current = level.getBlockState(pos);
            if (current.is(ModBlocks.FLUORESCENT_LIGHT.get()) && !current.getValue(Lights.ON)) {
                level.setBlockAndUpdate(pos, current.setValue(Lights.ON, true));
            }
        }
        return true;
    }
}
