package com.backrooms.world.events.poolrooms;

import com.backrooms.sound.ModSounds;
import com.backrooms.world.BackroomsLevel;
import com.backrooms.world.events.AbstractEvent;
import com.backrooms.world.events.WorldEvents;

/**
 * Port dari {@code com.sp.world.events.poolrooms.PoolroomsSunset}: waktu Poolrooms maju seperempat
 * hari (siang &rarr; senja &rarr; tengah malam &rarr; fajar &rarr; siang). Langitnya berubah pelan
 * di klien ({@code PoolroomsDayCycle}).
 */
public class PoolroomsSunset extends AbstractEvent {
    @Override
    public void init(WorldEvents world) {
        if (world.which() == BackroomsLevel.POOLROOMS) {
            // Aslinya PoolroomsDayCycle.getDayTime(world); di server itu sama dengan getTimeOfDay(),
            // karena transisi sebelumnya selalu sudah selesai (finish).
            float currentDayTime = world.getTimeOfDay();
            world.setSunsetTransitioning(true);
            world.setTimeOfDay(world.getTimeOfDay() + 0.25F);
            world.sync();
            if ((double) currentDayTime == 0.0D) {
                playSound(world, ModSounds.event("sunset_transition"));
            } else if ((double) currentDayTime != 0.25D && (double) currentDayTime != 0.5D) {
                playSound(world, ModSounds.event("sunset_transition_end"));
            } else {
                playSound(world, ModSounds.event("midnight_transition"));
            }
        }
    }

    @Override
    public void finish(WorldEvents world) {
        super.finish(world);
        if (world.which() == BackroomsLevel.POOLROOMS) {
            world.setTimeOfDay(world.getTimeOfDay() >= 1.0F ? 0.0F : world.getTimeOfDay());
            world.setSunsetTransitioning(false);
            world.sync();
        }
    }

    @Override
    public int duration() {
        return 200;
    }
}
