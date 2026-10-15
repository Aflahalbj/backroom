package com.backrooms.world.events;

/** Port dari {@code com.sp.world.events.EmptyEvent}: dipakai level yang tidak punya event. */
public class EmptyEvent extends AbstractEvent {
    @Override
    public void init(WorldEvents world) {
        this.done = true;
    }

    @Override
    public int duration() {
        return 0;
    }
}
