package com.backrooms.client.light;

import com.backrooms.block.Lights;
import com.backrooms.block.ModBlocks;
import com.backrooms.block.entity.FluorescentLightBlockEntity;
import com.backrooms.client.CameraEffect;
import com.backrooms.sound.ModSounds;
import com.backrooms.world.BackroomsLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Port dari {@code com.sp.sounds.FluorescentLightSoundInstance}: dengung panel neon Level 0. */
public class FluorescentLightSoundInstance extends AbstractTickableSoundInstance {

    private final FluorescentLightBlockEntity entity;
    private final Player player;

    public FluorescentLightSoundInstance(FluorescentLightBlockEntity entity, Player player) {
        super(ModSounds.event("fluorescent_light"), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.x = (float) entity.getBlockPos().getCenter().x;
        this.y = (double) ((float) entity.getBlockPos().getCenter().y) - 0.5D;
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
        Level world = this.entity.getLevel();
        if (BackroomsLevel.at(this.player.level(), this.player.getX()) == BackroomsLevel.LEVEL0 && world != null) {
            if (!this.entity.isRemoved() && this.entity.getBlockPos().closerToCenterThan(this.player.position(), 16.0D)
                    && com.backrooms.client.skinwalker.ClientWorldEvents.lightState() != com.backrooms.world.events.LightState.BLACKOUT
                    && this.entity.currentState == ModBlocks.FLUORESCENT_LIGHT.get().defaultBlockState().setValue(Lights.ON, true)
                    && !this.entity.currentState.getValue(Lights.BLACKOUT) && !CameraEffect.blackScreen) {
                this.pitch = 1.0F;
                this.volume = 0.4F;
            } else {
                this.stop();
                this.entity.playingSound = false;
            }
        }
    }
}
