package com.backrooms.util;

import foundry.veil.api.client.util.Easings;
import net.minecraft.util.Mth;

/** Port dari {@code com.sp.util.Timer}: timer berbasis jam dinding dengan easing masuk/keluar. */
public class Timer {

    private boolean started;
    private boolean reversed;
    private long startTime;
    private final long duration;
    private float currentTime = 0.0F;
    private final Easings.Easing easingIn;
    private final Easings.Easing easingOut;
    private boolean done;

    public Timer(long duration, Easings.Easing easing) {
        this(duration, easing, easing);
    }

    public Timer(long duration, Easings.Easing easeIn, Easings.Easing easeOut) {
        this.duration = duration;
        this.easingIn = easeIn;
        this.easingOut = easeOut;
    }

    public void startTimer() {
        if (!this.started) {
            this.currentTime = 0.0F;
            this.started = true;
            this.done = false;
            this.startTime = System.currentTimeMillis();
        }
    }

    public float getCurrentTime() {
        if (this.started) {
            this.currentTime = Mth.clamp((float) (System.currentTimeMillis() - this.startTime) / (float) this.duration, 0.0F, 1.0F);
            if (this.currentTime >= 1.0F) {
                this.done = true;
            }
        }
        return this.reversed ? 1.0F - this.easingOut.ease(this.currentTime) : this.easingIn.ease(this.currentTime);
    }

    public void forward() {
        if (this.reversed) {
            this.started = false;
            this.startTimer();
            this.reversed = false;
        }
    }

    public void reverse() {
        if (!this.reversed) {
            this.started = false;
            this.startTimer();
            this.reversed = true;
        }
    }

    public boolean hasStarted() {
        return this.started;
    }

    public boolean isDone() {
        return this.done;
    }
}
