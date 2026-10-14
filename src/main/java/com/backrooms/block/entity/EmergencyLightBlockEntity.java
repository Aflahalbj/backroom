package com.backrooms.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Port dari {@code com.sp.block.entity.EmergencyLightBlockEntity}: isinya
 * hanya efek klien -- lampu putih biasa, atau saat {@code red_light} dua
 * sorot merah berputar + point light merah dan bunyi alarm.
 */
public class EmergencyLightBlockEntity extends BlockEntity {

    public final float randomOffset;
    public boolean playingEmergencyAlarm = false;
    /** Lampu Veil dan suara alarm di klien (lihat {@code com.backrooms.client.light.LightTicks}). */
    public Object clientLight;

    public EmergencyLightBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.EMERGENCY_LIGHT.get(), pos, state);
        this.randomOffset = RandomSource.create().nextFloat() * 180.0F;
    }

    @Override
    public void setRemoved() {
        if (this.level != null && this.level.isClientSide) {
            com.backrooms.client.light.LightTicks.remove(this);
        }
        super.setRemoved();
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide) {
            com.backrooms.client.light.LightTicks.emergency(level, pos, state, this);
        }
    }

    public void setEmergencyAlarm(boolean playing) {
        this.playingEmergencyAlarm = playing;
    }
}
