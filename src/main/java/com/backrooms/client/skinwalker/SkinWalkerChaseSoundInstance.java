package com.backrooms.client.skinwalker;

import com.backrooms.component.SkinWalkerComponent;
import com.backrooms.entity.custom.SkinWalkerEntity;
import com.backrooms.sound.ModSounds;
import foundry.veil.api.client.util.Easings;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/** Port dari {@code com.sp.sounds.entity.SkinWalkerChaseSoundInstance}: musik kejar, memudar saat kejaran berhenti. */
public class SkinWalkerChaseSoundInstance extends AbstractTickableSoundInstance {
    private final SkinWalkerEntity entity;
    private final SkinWalkerComponent component;
    private boolean beginFade;
    private int ticksToFade = 80;

    public SkinWalkerChaseSoundInstance(SkinWalkerEntity entity) {
        super(ModSounds.event("skinwalker_chase"), SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
        this.entity = entity;
        this.component = entity.component;
        this.setPosition(entity);
        this.looping = true;
        this.delay = 0;
        this.volume = 100.0F;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public boolean isLooping() {
        return true;
    }

    @Override
    public void tick() {
        if (this.entity.isRemoved()) {
            this.beginFade = true;
        } else if (!this.component.isChasing()) {
            this.beginFade = true;
        }

        if (this.component.isChasing() && this.beginFade) {
            this.beginFade = false;
            this.ticksToFade = 80;
        }

        if (this.beginFade) {
            --this.ticksToFade;
            this.volume = 10.0F * Easings.Easing.easeInSine.ease((float) this.ticksToFade / 80.0F);
            if (this.ticksToFade <= 0) {
                this.stop();
            }
        }

        this.setPosition(this.entity);
    }

    private void setPosition(SkinWalkerEntity entity) {
        this.x = entity.getX();
        this.y = entity.getY();
        this.z = entity.getZ();
    }
}
