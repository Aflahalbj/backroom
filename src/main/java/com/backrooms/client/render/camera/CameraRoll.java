package com.backrooms.client.render.camera;

import com.backrooms.client.CameraEffect;
import com.backrooms.client.ClientConfig;
import com.backrooms.util.MathStuff;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec2;

/** Port dari {@code com.sp.render.camera.CameraRoll}: kamera miring saat menoleh dan bergeser ke samping. */
public final class CameraRoll {

    static float prevYaw;
    static float rotAmount;
    static float spinRoll;
    static float strafeRoll;

    private CameraRoll() {}

    public static float doCameraRoll(Player player, float tickDelta) {
        if (player != null) {
            float yaw = player.getViewYRot(tickDelta);
            float lastFrameDuration = Minecraft.getInstance().getDeltaFrameTime();
            rotAmount += yaw - prevYaw;
            spinRoll = MathStuff.Lerp(spinRoll, rotAmount * 0.1F * ClientConfig.lookRollMultiplier(), 0.8F, lastFrameDuration);
            rotAmount = MathStuff.Lerp(rotAmount, 0.0F, 0.5F, lastFrameDuration);
            spinRoll = Mth.clamp(spinRoll, -20.0F, 20.0F);
            Vec2 velocity2D = MathStuff.get2DRelativeRotation(player.getDeltaMovement(), 360.0F - player.getYRot());
            strafeRoll = MathStuff.Lerp(strafeRoll, -velocity2D.x * 5.0F * ClientConfig.strafeRollMultiplier(), 0.8F, lastFrameDuration);
            strafeRoll = Mth.clamp(strafeRoll, -20.0F, 20.0F);
            prevYaw = yaw;
        }
        return spinRoll + strafeRoll + CameraEffect.getCameraShake().getCameraZRot();
    }
}
