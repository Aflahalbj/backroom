package com.backrooms.mixin;

import com.backrooms.client.CameraEffect;
import com.backrooms.client.ClientConfig;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Port dari {@code com.sp.mixin.GameOptionsMixin}: selama efek kamera aktif
 * bayangan entity mati, grafik Fabulous diturunkan ke Fast, dan perubahan FOV
 * saat berlari dimatikan (real camera).
 */
@Mixin(Options.class)
public abstract class OptionsMixin {

    @Unique
    private Double backrooms$prevFovEffectScale;

    @Shadow
    @Final
    private OptionInstance<Double> fovEffectScale;

    @Inject(method = "entityShadows", at = @At("RETURN"))
    private void backrooms$disableEntityShadows(CallbackInfoReturnable<OptionInstance<Boolean>> cir) {
        if (CameraEffect.shouldRenderCameraEffect()) {
            cir.getReturnValue().set(false);
        }
    }

    @Inject(method = "graphicsMode", at = @At("RETURN"))
    private void backrooms$disableGraphicsMode(CallbackInfoReturnable<OptionInstance<GraphicsStatus>> cir) {
        OptionInstance<GraphicsStatus> original = cir.getReturnValue();
        if (CameraEffect.shouldRenderCameraEffect() && original.get() == GraphicsStatus.FABULOUS) {
            original.set(GraphicsStatus.FAST);
        }
    }

    @Inject(method = "fovEffectScale", at = @At("HEAD"), cancellable = true)
    private void backrooms$disableSprintFOVChange(CallbackInfoReturnable<OptionInstance<Double>> cir) {
        if (CameraEffect.shouldRenderCameraEffect()) {
            if (ClientConfig.enableRealCamera()) {
                if (this.backrooms$prevFovEffectScale == null) {
                    this.backrooms$prevFovEffectScale = this.fovEffectScale.get();
                }
                this.fovEffectScale.set(0.0D);
            } else if (this.backrooms$prevFovEffectScale != null) {
                this.fovEffectScale.set(this.backrooms$prevFovEffectScale);
                this.backrooms$prevFovEffectScale = null;
            }
            cir.setReturnValue(this.fovEffectScale);
        }
    }
}
