package com.backrooms.util;

import java.util.Vector;

/**
 * Port dari {@code com.sp.util.TickTimer}: penghitung tick klien. Semua
 * instance dimajukan satu kali per tick dunia klien (lihat {@code CameraEffect}).
 */
public class TickTimer {

    private static final Vector<TickTimer> ALL_INSTANCES = new Vector<>();
    private int currentTick = 0;
    private boolean on = true;

    public TickTimer() {
        ALL_INSTANCES.add(this);
    }

    public int getCurrentTick() {
        return this.currentTick;
    }

    public void addCurrentTick() {
        if (this.on) {
            ++this.currentTick;
        }
    }

    public void resetToZero() {
        this.currentTick = 0;
    }

    public void setOnOrOff(boolean on) {
        this.on = on;
    }

    @SuppressWarnings("unchecked")
    public static synchronized Vector<TickTimer> getAllInstances() {
        return (Vector<TickTimer>) ALL_INSTANCES.clone();
    }
}
