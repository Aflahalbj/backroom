package com.backrooms.util;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** Port dari {@code com.sp.util.MathStuff} (hanya bagian yang dipakai efek kamera dan cutscene). */
public final class MathStuff {

    private MathStuff() {}

    public static float mod(float x, float y) {
        return x - y * (float) Math.floor((double) x / (double) y);
    }

    /** Lerp yang tidak bergantung frame rate: {@code delta} = durasi frame terakhir (tick). */
    public static float Lerp(float source, float destination, float smoothingFactor, float delta) {
        return Mth.lerp(1.0F - (float) Math.pow(smoothingFactor, delta), source, destination);
    }

    public static int millisecToTick(long millisec) {
        return (int) ((double) millisec * 0.02D);
    }

    public static float randomFloat(float min, float max, RandomSource random) {
        return min + random.nextFloat() * (max - min);
    }

    public static Vec2 get2DRelativeRotation(Vec3 vec, float degrees) {
        float x = (float) vec.x();
        float y = (float) vec.z();
        float radians = (float) Math.toRadians(degrees);
        float cos = Mth.cos(radians);
        float sin = Mth.sin(radians);
        return new Vec2(x * cos - y * sin, y * cos + x * sin);
    }
}
