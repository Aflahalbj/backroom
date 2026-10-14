package com.backrooms.client.render;

import com.backrooms.util.MathStuff;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Vector3f;

/** Port dari {@code com.sp.render.SSAOSamples}: kernel sampel SSAO (50 titik, radius 0.5). */
public final class SSAOSamples {

    private static final int MAX_SAMPLES = 50;
    private static Vector3f[] samples;

    private SSAOSamples() {}

    public static Vector3f[] getSSAOSamples() {
        if (samples == null) {
            samples = generateSSAOSamples();
        }
        return samples;
    }

    public static Vector3f[] generateSSAOSamples() {
        RandomSource random = RandomSource.create();
        Vector3f[] list = new Vector3f[MAX_SAMPLES];
        for (int i = 0; i < MAX_SAMPLES; ++i) {
            list[i] = new Vector3f(MathStuff.randomFloat(-1.0F, 1.0F, random), MathStuff.randomFloat(-1.0F, 1.0F, random), MathStuff.randomFloat(0.0F, 1.0F, random));
            list[i].normalize();
            list[i].mul(MathStuff.randomFloat(0.0F, 0.5F, random));
            float scale = Math.min((float) i / 50.0F * 3.0F, 1.0F);
            list[i].mul(Mth.lerp(scale, 0.0F, 0.5F));
        }
        return list;
    }
}
