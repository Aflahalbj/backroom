package com.backrooms.mixin;

import com.backrooms.client.CameraEffect;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Port dari {@code com.sp.mixin.RemoveBlockBreakParticlesMixin}: tanpa partikel retak blok selama efek kamera. */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {

    @Inject(method = "crack", at = @At("HEAD"), cancellable = true)
    private void backrooms$cancel(BlockPos pos, Direction direction, CallbackInfo ci) {
        if (CameraEffect.shouldRenderCameraEffect()) {
            ci.cancel();
        }
    }
}
