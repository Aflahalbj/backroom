package com.backrooms.mixin;

import com.backrooms.client.CameraEffect;
import com.backrooms.client.lobby.LobbyClient;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Port dari {@code com.sp.mixin.CameraMixin}: guncangan kamera genggam.
 * Sebelumnya, kamera lobby / intro Level 0 dipasang kalau sedang aktif
 * (guncangannya tetap berlaku di lobby, seperti kamera yang dipegang tangan).
 */
@Mixin(Camera.class)
public class CameraMixin {

    @Inject(method = "setup", at = @At("TAIL"))
    private void backrooms$cameraShake(BlockGetter area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        LobbyClient.applyCamera((Camera) (Object) this);
        if (CameraEffect.shouldRenderCameraEffect()) {
            CameraEffect.getCameraShake().tick((Camera) (Object) this);
        }
    }
}
