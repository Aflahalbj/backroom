package com.backrooms.world.events.generic.lights;

import com.backrooms.sound.ModSounds;
import com.backrooms.world.events.AbstractEvent;
import com.backrooms.world.events.LightState;
import com.backrooms.world.events.WorldEvents;

/** Port dari {@code com.sp.world.events.generic.lights.LightLevelBlackout}. */
public class LightLevelBlackout extends AbstractEvent {
    @Override
    public void init(WorldEvents world) {
        if (world.hasLights()) {
            if (world.getLightState() != LightState.BLACKOUT) {
                world.setLightState(LightState.BLACKOUT);
                playSound(world, ModSounds.event("lights_out"));
            }
        }
    }

    @Override
    public void finish(WorldEvents world) {
        super.finish(world);
        if (world.hasLights()) {
            world.setLightState(LightState.ON);
        }
    }

    @Override
    public int duration() {
        return 20;
    }
}
