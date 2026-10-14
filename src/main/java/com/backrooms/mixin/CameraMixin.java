package com.backrooms.mixin;

import com.backrooms.client.CameraEffect;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Port dari {@code com.sp.mixin.CameraMixin}: guncangan kamera genggam. */
@Mixin(Camera.class)
public class CameraMixin {

    @Inject(method = "setup", at = @At("TAIL"))
    private void backrooms$cameraShake(BlockGetter area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (CameraEffect.shouldRenderCameraEffect()) {
            CameraEffect.getCameraShake().tick((Camera) (Object) this);
        }
    }
}
