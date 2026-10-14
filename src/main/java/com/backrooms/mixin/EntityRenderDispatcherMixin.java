package com.backrooms.mixin;

import com.backrooms.client.CameraEffect;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Port dari {@code com.sp.mixin.EntityRenderDispatcherMixin}: tanpa bayangan bulat entity selama efek kamera. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @Shadow
    private boolean shouldRenderShadow;

    @Inject(method = "setRenderShadow", at = @At("TAIL"))
    private void backrooms$setRenderShadows(boolean renderShadows, CallbackInfo ci) {
        if (CameraEffect.shouldRenderCameraEffect()) {
            this.shouldRenderShadow = false;
        }
    }
}
