package com.backrooms.world.events.level0;

import com.backrooms.sound.ModSounds;
import com.backrooms.world.BackroomsLevel;
import com.backrooms.world.events.AbstractEvent;
import com.backrooms.world.events.LightState;
import com.backrooms.world.events.WorldEvents;
import net.minecraft.util.RandomSource;

/** Port dari {@code com.sp.world.events.level0.Level0IntercomBasic}. */
public class Level0IntercomBasic extends AbstractEvent {
    boolean friend = false;
    int duration = 200;

    @Override
    public void init(WorldEvents world) {
        if (world.which() == BackroomsLevel.LEVEL0) {
            int intercomCount = world.getIntercomCount();
            RandomSource random = RandomSource.create();
            if (intercomCount <= 1) {
                int rand = random.nextIntBetweenInclusive(1, 2);
                if (rand == 1) {
                    playSoundWithRandLocation(world, ModSounds.event("intercom_basic1"), 25, 20);
                } else {
                    playSoundWithRandLocation(world, ModSounds.event("intercom_basic2"), 25, 20);
                }
            } else if (intercomCount == 2) {
                playSoundWithRandLocation(world, ModSounds.event("intercom_reversed"), 25, 20);
            } else {
                int rand = random.nextIntBetweenInclusive(1, 1);
                if (rand == 1) {
                    playSoundWithRandLocation(world, ModSounds.event("intercom_friend"), 25, 20);
                    this.friend = true;
                    this.duration = 800;
                } else {
                    playSoundWithRandLocation(world, ModSounds.event("intercom_reversed"), 25, 20);
                }
            }
            world.addIntercomCount();
        }
    }

    @Override
    public void ticks(int ticks, WorldEvents world) {
        if (world.which() == BackroomsLevel.LEVEL0) {
            if (this.friend) {
                if (ticks == 460) {
                    world.setLightState(LightState.FLICKER);
                } else if (ticks == 528) {
                    world.setLightState(LightState.OFF);
                    playSound(world, ModSounds.event("lights_out"));
                } else if (ticks == 656) {
                    world.setLightState(LightState.ON);
                }
            }
        }
    }

    @Override
    public int duration() {
        return this.duration;
    }
}
