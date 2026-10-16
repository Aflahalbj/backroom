package com.backrooms.world.events.level1;

import com.backrooms.sound.ModSounds;
import com.backrooms.world.events.AbstractEvent;
import com.backrooms.world.events.WorldEvents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;

/** Port dari {@code com.sp.world.events.level1.Level1Ambience}: suara jauh acak. */
public class Level1Ambience extends AbstractEvent {
    @Override
    public void init(WorldEvents world) {
        RandomSource random = RandomSource.create();
        int rand = random.nextIntBetweenInclusive(1, 4);
        SoundEvent soundEvent = switch (rand) {
            case 1 -> ModSounds.event("level1_ambience1");
            case 2 -> ModSounds.event("level1_ambience2");
            case 3 -> ModSounds.event("level1_ambience3");
            default -> ModSounds.event("level1_ambience4");
        };
        playDistantSound(world, soundEvent);
    }

    @Override
    public int duration() {
        return 1200;
    }
}
