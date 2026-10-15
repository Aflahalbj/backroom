package com.backrooms.block.entity;

import com.backrooms.block.Lights;
import com.backrooms.block.ModBlocks;
import com.backrooms.world.BackroomsLevel;
import com.backrooms.world.events.LightState;
import com.backrooms.world.events.WorldEventsManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
 *   <li>Lampu mengikuti kondisi lampu level ({@code LightState}) salinan
 *       level tempatnya: blackout, mati, berkedip, atau menyala, persis
 *       seperti aslinya (termasuk {@code blackout=true} yang tidak pernah
 *       dikembalikan). Di level tanpa lampu (Poolrooms, lobby) tidak diubah.</li>
 * </ul>
 */
public class FluorescentLightBlockEntity extends BlockEntity {

    public BlockState currentState;
    public final RandomSource random = RandomSource.create();
    public final java.util.Random random1 = new java.util.Random();
    public boolean playingSound;
    public boolean prevOn;
    public int ticks = 0;
    public final int randInt = this.random.nextIntBetweenInclusive(1, 5);
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
            if (!this.serverTick(level, pos, state)) return;
        } else {
            com.backrooms.client.light.LightTicks.fluorescent(level, pos, state, this);
        }
        if (this.ticks > 100) {
            this.ticks = 1;
        }
        BlockState now = level.getBlockState(pos);
        this.prevOn = now.hasProperty(Lights.ON) && now.getValue(Lights.ON);
    }

    /** @return false kalau tick ini berhenti di sini (diganti ceiling tile, atau level tanpa lampu). */
    private boolean serverTick(Level level, BlockPos pos, BlockState state) {
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
            LightState lightState = WorldEventsManager.lightStateAt((ServerLevel) level, pos.getX());
            if (lightState == null) {
                return false;
            }
            if (lightState == LightState.BLACKOUT) {
                level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(Lights.BLACKOUT, true));
            }
            if (lightState != LightState.ON && state.getValue(Lights.ON)) {
                level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(Lights.ON, false));
            }
            if (lightState == LightState.FLICKER && !state.getValue(Lights.BLACKOUT)) {
                if (this.ticks % this.randInt == 0) {
                    boolean on = this.random.nextBoolean();
                    level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(Lights.ON, on));
                }
            } else if (!state.getValue(Lights.ON) && lightState == LightState.ON) {
                level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(Lights.ON, true));
            }
        }
        return true;
    }
}
