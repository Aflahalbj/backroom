package com.backrooms.world.events.generic.lights;

import com.backrooms.world.events.AbstractEvent;
import com.backrooms.world.events.LightState;
import com.backrooms.world.events.WorldEvents;

/** Port dari {@code com.sp.world.events.generic.lights.LightLevelFlicker}. */
public class LightLevelFlicker extends AbstractEvent {
    @Override
    public void init(WorldEvents world) {
        if (world.hasLights()) {
            world.setLightState(LightState.FLICKER);
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
        return 200;
    }
}
