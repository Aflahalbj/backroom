package com.backrooms.world.events.level0;

import com.backrooms.sound.ModSounds;
import com.backrooms.world.BackroomsLevel;
import com.backrooms.world.events.AbstractEvent;
import com.backrooms.world.events.WorldEvents;
import net.minecraft.util.RandomSource;

/** Port dari {@code com.sp.world.events.level0.Level0Music}. */
public class Level0Music extends AbstractEvent {
    int duration = 980;

    @Override
    public void init(WorldEvents world) {
        if (world.which() == BackroomsLevel.LEVEL0) {
            int intercomCount = world.getIntercomCount();
            RandomSource random = RandomSource.create();
            if (intercomCount > 2) {
                int rand = random.nextIntBetweenInclusive(1, 3);
                if (rand == 1) {
                    playSoundWithRandLocation(world, ModSounds.event("creepy_music1"), 25, 20);
                } else if (rand == 2) {
                    playSoundWithRandLocation(world, ModSounds.event("creepy_music2"), 25, 20);
                } else {
                    playSoundWithRandLocation(world, ModSounds.event("far_crowd"), 25, 20);
                }
            } else {
                if (world.getIntercomCount() == 2) {
                    playSoundWithRandLocation(world, ModSounds.event("intercom_reversed"), 25, 20);
                } else {
                    int rand = random.nextIntBetweenInclusive(1, 2);
                    if (rand == 1) {
                        playSoundWithRandLocation(world, ModSounds.event("intercom_basic1"), 25, 20);
                    } else {
                        playSoundWithRandLocation(world, ModSounds.event("intercom_basic2"), 25, 20);
                    }
                }
                this.duration = 200;
                world.addIntercomCount();
            }
        }
    }

    @Override
    public int duration() {
        return this.duration;
    }
}
