package com.backrooms.world.events;

import net.minecraft.sounds.SoundEvent;

/**
 * Port dari {@code com.sp.world.events.AbstractEvent}. Di aslinya event
 * menerima {@code World}; di sini {@link WorldEvents} milik satu salinan
 * level (bangunan utama atau room), karena beberapa room berbagi dimensi.
 */
public abstract class AbstractEvent {
    boolean done = false;

    public abstract void init(WorldEvents world);

    public void finish(WorldEvents world) {
        this.done = true;
    }

    public boolean isDone() {
        return this.done;
    }

    public abstract int duration();

    public void ticks(int ticks, WorldEvents world) {
    }

    protected static void playSound(WorldEvents world, SoundEvent soundEvent) {
        EventSounds.playSound(world, soundEvent);
    }

    protected static void playSoundWithRandLocation(WorldEvents world, SoundEvent soundEvent, int yLevel, int range) {
        EventSounds.playSoundWithRandLocation(world, soundEvent, yLevel, range);
    }

    protected static void playDistantSound(WorldEvents world, SoundEvent soundEvent) {
        EventSounds.playDistantSound(world, soundEvent);
    }
}
