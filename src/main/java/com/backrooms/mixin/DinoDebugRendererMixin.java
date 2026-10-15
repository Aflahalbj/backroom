package com.backrooms.mixin;

import com.backrooms.entity.ik.util.PrAnCommonClass;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Port dari {@code com.sp.mixin.DinoDebugRendererMixin}: F3+J (hanya di lingkungan dev) menyalakan/mematikan debug kaki IK. */
@Mixin(KeyboardHandler.class)
public abstract class DinoDebugRendererMixin {
    @Unique
    private static final int L = 74;

    @Shadow
    private void debugFeedbackComponent(Component message) {
    }

    @Inject(method = "handleDebugKeys", at = @At("HEAD"), cancellable = true)
    private void onHandleDebugKeys(int keyCode, CallbackInfoReturnable<Boolean> cir) {
        if (!FMLEnvironment.production && keyCode == 74) {
            PrAnCommonClass.shouldRenderDebugLegs = !PrAnCommonClass.shouldRenderDebugLegs;
            this.debugFeedbackComponent(Component.translatable("debug.toggled_joint_debug.message"));
            cir.setReturnValue(true);
        }
    }
}
