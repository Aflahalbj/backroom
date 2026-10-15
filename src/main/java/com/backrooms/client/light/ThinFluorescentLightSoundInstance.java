package com.backrooms.client.light;

import com.backrooms.block.Lights;
import com.backrooms.block.entity.ThinFluorescentLightBlockEntity;
import com.backrooms.client.CameraEffect;
import com.backrooms.sound.ModSounds;
import com.backrooms.world.BackroomsLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/** Port dari {@code com.sp.sounds.ThinFluorescentLightSoundInstance}: dengung neon tipis. */
public class ThinFluorescentLightSoundInstance extends AbstractTickableSoundInstance {

    private final ThinFluorescentLightBlockEntity entity;
    private final Player player;

    public ThinFluorescentLightSoundInstance(ThinFluorescentLightBlockEntity entity, Player player) {
        super(ModSounds.event("fluorescent_light2"), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.x = (float) entity.getBlockPos().getCenter().x;
        this.y = (float) entity.getBlockPos().getCenter().y;
        this.z = (float) entity.getBlockPos().getCenter().z;
        this.entity = entity;
        this.player = player;
        this.looping = true;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        if (this.entity.getLevel() == null) return;
        // Level 0 dan Level 1: lampu dianggap padam selama blackout level.
        BackroomsLevel level = BackroomsLevel.at(this.entity.getLevel(), this.entity.getBlockPos().getX());
        boolean blackedOut = (level == BackroomsLevel.LEVEL0 || level == BackroomsLevel.LEVEL1)
                && com.backrooms.client.skinwalker.ClientWorldEvents.lightState() == com.backrooms.world.events.LightState.BLACKOUT;
        if (!this.entity.isRemoved() && this.entity.getBlockPos().closerToCenterThan(this.player.position(), 15.0D)
                && !blackedOut && this.entity.currentState.getValue(Lights.ON) && !this.entity.currentState.getValue(Lights.BLACKOUT)
                && !CameraEffect.blackScreen) {
            this.pitch = 1.0F;
            this.volume = 0.2F;
        } else {
            this.stop();
            this.entity.playingSound = false;
        }
    }
}
