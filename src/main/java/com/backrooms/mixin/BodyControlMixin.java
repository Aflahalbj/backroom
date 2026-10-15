package com.backrooms.mixin;

import com.backrooms.entity.custom.SkinWalkerEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Port dari {@code com.sp.mixin.BodyControlMixin}: Skinwalker tidak memutar badannya pelan-pelan ke arah kepala. */
@Mixin(BodyRotationControl.class)
public class BodyControlMixin {
    @Shadow
    @Final
    private Mob mob;

    @Inject(method = "rotateHeadTowardsFront", at = @At("HEAD"), cancellable = true)
    private void redirect(CallbackInfo ci) {
        if (this.mob instanceof SkinWalkerEntity) {
            ci.cancel();
        }
    }
}
