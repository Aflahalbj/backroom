package com.backrooms.client.ambience;

import com.backrooms.sound.ModSounds;
import java.util.function.BooleanSupplier;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * Suara latar yang berulang dan menempel di pemain, gabungan dari sound instance asli
 * yang bentuknya sama ({@code AmbientSoundInstance}, {@code SmilerAmbienceSoundInstance},
 * {@code PoolroomsNoonAmbienceSoundInstance}, {@code PoolroomsSunsetAmbienceSoundInstance}):
 * volume 0.5, berhenti kalau kondisinya tidak berlaku lagi atau pemainnya hilang.
 */
public class LoopingAmbienceSoundInstance extends AbstractTickableSoundInstance {
    private final Player player;
    private final BooleanSupplier keepPlaying;

    public LoopingAmbienceSoundInstance(String sound, Player player, BooleanSupplier keepPlaying) {
        super(ModSounds.event(sound), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
        this.player = player;
        this.keepPlaying = keepPlaying;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.5F;
        this.relative = true;
    }

    @Override
    public void tick() {
        if (!this.keepPlaying.getAsBoolean() || this.player.isRemoved()) {
            this.stop();
        }
    }
}
