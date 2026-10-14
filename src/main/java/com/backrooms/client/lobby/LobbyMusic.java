package com.backrooms.client.lobby;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Suara lobby pilihan (lihat {@link LobbyTrack}): tanpa posisi, berulang kecuali mode Acak. */
final class LobbyMusic extends AbstractTickableSoundInstance {

    LobbyMusic(SoundEvent sound, boolean looping) {
        super(sound, SoundSource.RECORDS, SoundInstance.createUnseededRandom());
        this.looping = looping;
        this.delay = 0;
        this.volume = 0.6F;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
    }

    @Override
    public void tick() {
        if (!LobbyClient.isActive()) {
            stop();
        }
    }
}
