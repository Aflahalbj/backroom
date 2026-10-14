package com.backrooms.client.render;

import com.backrooms.world.BackroomsLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/**
 * Port dari {@code com.sp.render.PoolroomsDayCycle}: sudut matahari dan warna
 * cahaya Poolrooms. Event matahari terbenam aslinya tidak di-port, jadi waktu
 * Poolrooms selalu 0 (siang) -- sama seperti aslinya sebelum event pertama.
 */
public final class PoolroomsDayCycle {

    public static float dayTime = 0.0F;
    static float noonAngle = 85.0F;
    static float sunSetAngle = 160.0F;
    static float midnightAngle = 70.0F;
    static float sunriseAngle = 20.0F;

    private PoolroomsDayCycle() {}

    public static float advanceDayTime(Level world) {
        if (world != null) {
            if (com.backrooms.client.CameraEffect.isInLevel(BackroomsLevel.POOLROOMS)) {
                float currentTime = 0.0F;
                dayTime = currentTime;
                return currentTime;
            }
            return 0.0F;
        }
        return dayTime;
    }

    public static float getSunAngle() {
        if (dayTime <= 0.25D) {
            return Mth.lerp((dayTime - 0.0F) / 0.25F, noonAngle, sunSetAngle);
        } else if (dayTime <= 0.35D) {
            return Mth.lerp((dayTime - 0.25F) / 0.15F, sunSetAngle, 220.0F);
        } else if (dayTime <= 0.5D) {
            return Mth.lerp((dayTime - 0.35F) / 0.15F, -40.0F, midnightAngle);
        } else if (dayTime <= 0.65D) {
            return Mth.lerp((dayTime - 0.5F) / 0.15F, midnightAngle, 220.0F);
        } else if (dayTime <= 0.75D) {
            return Mth.lerp((dayTime - 0.65F) / 0.1F, -40.0F, sunriseAngle);
        } else {
            return dayTime <= 1.0D ? Mth.lerp((dayTime - 0.75F) / 0.25F, sunriseAngle, noonAngle) : 90.0F;
        }
    }

    public static Vector3f getLightColor() {
        Vector3f orangeLightColor = new Vector3f(0.9411F, 0.8156F, 0.5803F);
        Vector3f darkColor = new Vector3f(0.3F, 0.3F, 0.5803F);
        Vector3f whiteColor = new Vector3f(1.0F);
        if (dayTime <= 0.25D) {
            return whiteColor.lerp(orangeLightColor, (dayTime - 0.0F) / 0.25F);
        } else if (dayTime <= 0.35D) {
            return orangeLightColor.lerp(darkColor, (dayTime - 0.25F) / 0.1F);
        } else if (dayTime <= 0.65D) {
            return darkColor;
        } else if (dayTime <= 0.75D) {
            return darkColor.lerp(orangeLightColor, (dayTime - 0.65F) / 0.1F);
        } else {
            return dayTime <= 1.0D ? orangeLightColor.lerp(whiteColor, (dayTime - 0.75F) / 0.25F) : whiteColor;
        }
    }

    public static float getDayTime(Level world) {
        return advanceDayTime(world);
    }
}
