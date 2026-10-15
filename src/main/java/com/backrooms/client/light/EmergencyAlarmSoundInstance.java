package com.backrooms.client.light;

import com.backrooms.block.entity.EmergencyLightBlockEntity;
import com.backrooms.client.CameraEffect;
import com.backrooms.sound.ModSounds;
import com.backrooms.world.BackroomsLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Port dari {@code com.sp.sounds.EmergencyAlarmSoundInstance}: alarm lampu darurat merah. */
public class EmergencyAlarmSoundInstance extends AbstractTickableSoundInstance {

    private final EmergencyLightBlockEntity entity;
    private final Player player;

    public EmergencyAlarmSoundInstance(EmergencyLightBlockEntity entity, Player player) {
        super(ModSounds.event("emergency_light_alarm"), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.x = (float) entity.getBlockPos().getCenter().x;
        this.y = (float) entity.getBlockPos().getCenter().y;
        this.z = (float) entity.getBlockPos().getCenter().z;
        this.entity = entity;
        this.player = player;
        this.pitch = 1.0F;
        this.volume = 3.0F;
        this.looping = true;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        Level world = this.entity.getLevel();
        if (world != null && BackroomsLevel.at(world, this.entity.getBlockPos().getX()) == BackroomsLevel.LEVEL0) {
            if (!this.entity.isRemoved() && this.entity.getBlockPos().closerToCenterThan(this.player.position(), 80.0D)
                    && com.backrooms.client.skinwalker.ClientWorldEvents.lightState() != com.backrooms.world.events.LightState.BLACKOUT && !CameraEffect.blackScreen) {
                this.pitch = 1.0F;
                this.volume = 10.0F;
            } else {
                this.stop();
                this.entity.setEmergencyAlarm(false);
            }
        }
    }
}
