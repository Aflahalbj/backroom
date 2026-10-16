package com.backrooms.client.smiler;

import com.backrooms.component.PlayerComponent;
import com.backrooms.sound.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/** Port dari {@code com.sp.sounds.entity.SmilerGlitchSoundInstance}: makin keras seiring glitch. */
public class SmilerGlitchSoundInstance extends AbstractTickableSoundInstance {
    private final Player player;
    private final PlayerComponent component;

    public SmilerGlitchSoundInstance(Player player) {
        super(ModSounds.event("smiler_glitch"), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
        this.player = player;
        this.component = PlayerComponent.get(player);
        this.looping = true;
        this.delay = 0;
        this.volume = 1.0F;
        this.relative = true;
    }

    @Override
    public void tick() {
        if (this.player.isRemoved()) {
            this.stop();
        }

        this.volume = this.component.getGlitchTimer() + 0.1F;
    }
}
