package com.backrooms.mixin.veil;

import foundry.veil.api.client.render.shader.VeilShaders;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Port dari {@code com.sp.mixin.lightshadows.VeilShadersMixin}: point light
 * dan area light Veil memakai shader mod asli ({@code spbrevamped:point},
 * {@code spbrevamped:area}) yang mendukung bayangan.
 */
@Mixin(value = VeilShaders.class, remap = false)
public abstract class VeilShadersMixin {

    @Shadow
    @Final
    @Mutable
    public static ResourceLocation LIGHT_POINT;

    @Shadow
    @Final
    @Mutable
    public static ResourceLocation LIGHT_AREA;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void backrooms$replaceLightShaders(CallbackInfo ci) {
        LIGHT_POINT = new ResourceLocation("spbrevamped", "point");
        LIGHT_AREA = new ResourceLocation("spbrevamped", "area");
    }
}
