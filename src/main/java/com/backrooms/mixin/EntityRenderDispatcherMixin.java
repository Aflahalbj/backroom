package com.backrooms.mixin;

import com.backrooms.client.CameraEffect;
import com.backrooms.client.lobby.DropCutscene;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Port dari {@code com.sp.mixin.EntityRenderDispatcherMixin}: tanpa bayangan bulat entity selama efek kamera.
 *
 * <p>Tambahan: selama cutscene perkenalan anggota room digambar jauh dari
 * posisi aslinya (lihat {@link DropCutscene#renderOffset}), jadi jangan
 * disisihkan karena posisi aslinya di luar pandangan kamera.
 */
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

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void backrooms$dropMember(Entity entity, Frustum frustum, double camX, double camY, double camZ,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (DropCutscene.isMember(entity)) cir.setReturnValue(true);
    }
}
