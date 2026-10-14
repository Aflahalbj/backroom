package com.backrooms.client.render.camera;

import com.backrooms.client.ClientConfig;
import com.backrooms.mixin.CameraAccessor;
import com.backrooms.util.MathStuff;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;

/**
 * Port dari {@code com.sp.render.camera.CameraShake}: goyangan kamera genggam
 * (noise Perlin) yang makin kuat saat berlari.
 */
public class CameraShake {

    public double trauma = 0.1D;
    private double traumaGoal;
    public double noiseSpeed = 0.1D;
    private double noiseSpeedGoal;
    private double noiseY = 0.0D;
    private double amplitude = 5.0D;
    private final ImprovedNoise noiseSampler = new ImprovedNoise(RandomSource.create());
    private float cameraZRot = 0.0F;

    public void tick(Camera camera) {
        if (ClientConfig.enableRealCamera() && !com.backrooms.client.lobby.IntroCutscene.isPlaying()) {
            float frameDelta = Minecraft.getInstance().getDeltaFrameTime();
            if (this.noiseY >= 1000.0D) {
                this.noiseY = 0.0D;
            }
            Player player = Minecraft.getInstance().player;
            if (player != null) {
                float playerSpeed = (player.walkDist - player.walkDistO) * 6.0F;
                if (player.isFallFlying()) {
                    playerSpeed /= 6.0F;
                }
                this.traumaGoal = Mth.clamp(0.6D * (double) playerSpeed, 0.5D, 1.5D);
                this.noiseSpeedGoal = Mth.clamp(0.25D * (double) playerSpeed, 0.1D, 1.0D);
                this.amplitude = 4.0D;
                this.trauma = Math.max(MathStuff.Lerp((float) this.trauma, (float) this.traumaGoal, 0.93F, frameDelta), 0.5D);
                this.noiseSpeed = Math.max(MathStuff.Lerp((float) this.noiseSpeed, (float) this.noiseSpeedGoal, 0.93F, frameDelta), 0.1D);
                this.noiseY += this.noiseSpeed * (double) frameDelta;
                double pitchOffset = this.amplitude * this.getShakeIntensity() * this.noiseSampler.noise(1.0D, this.noiseY, 0.0D);
                double yawOffset = this.amplitude * this.getShakeIntensity() * this.noiseSampler.noise(73.0D, this.noiseY, 0.0D);
                double rollOffset = this.amplitude * this.getShakeIntensity() * this.noiseSampler.noise(146.0D, this.noiseY, 0.0D);
                ((CameraAccessor) camera).backrooms$setRotation((float) ((double) camera.getYRot() + yawOffset), (float) ((double) camera.getXRot() + pitchOffset));
                this.cameraZRot = (float) rollOffset * 2.0F;
            }
        }
    }

    private double getShakeIntensity() {
        return this.trauma * this.trauma;
    }

    public float getCameraZRot() {
        return this.cameraZRot;
    }
}
