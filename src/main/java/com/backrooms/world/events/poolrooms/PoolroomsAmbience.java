package com.backrooms.world.events.poolrooms;

import com.backrooms.sound.ModSounds;
import com.backrooms.world.events.AbstractEvent;
import com.backrooms.world.events.WorldEvents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;

/** Port dari {@code com.sp.world.events.poolrooms.PoolroomsAmbience}: cipratan/tetesan air di kejauhan. */
public class PoolroomsAmbience extends AbstractEvent {
    @Override
    public void init(WorldEvents world) {
        RandomSource random = RandomSource.create();
        int rand = random.nextIntBetweenInclusive(1, 4);
        SoundEvent soundEvent = switch (rand) {
            case 1 -> ModSounds.event("poolrooms_splash1");
            case 2 -> ModSounds.event("poolrooms_splash2");
            case 3 -> ModSounds.event("poolrooms_drip1");
            default -> ModSounds.event("poolrooms_drip2");
        };
        playDistantSound(world, soundEvent);
    }

    @Override
    public int duration() {
        return 200;
    }
}
