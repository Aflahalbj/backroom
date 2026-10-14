package com.backrooms.mixin.veil;

import foundry.veil.api.client.render.deferred.light.PointLight;
import foundry.veil.api.client.render.deferred.light.renderer.IndirectLightRenderer;
import foundry.veil.impl.client.render.deferred.light.IndirectPointLightRenderer;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL33C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Port dari {@code com.sp.mixin.lightshadows.IndirectPointLightRendererMixin}:
 * atribut instance ke-4 (dibaca shader {@code spbrevamped:point} sebagai
 * {@code renderShadows}).
 */
@Mixin(value = IndirectPointLightRenderer.class, remap = false)
public abstract class IndirectPointLightRendererMixin extends IndirectLightRenderer<PointLight> {

    public IndirectPointLightRendererMixin(int lightSize, int lowResSize, int positionOffset, int rangeOffset) {
        super(lightSize, lowResSize, positionOffset, rangeOffset);
    }

    @Inject(method = "setupBufferState", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL20C;glEnableVertexAttribArray(I)V", ordinal = 2))
    private void backrooms$inject1(CallbackInfo ci) {
        GL20C.glEnableVertexAttribArray(4);
    }

    @Inject(method = "setupBufferState", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL20C;glVertexAttribPointer(IIIZIJ)V", ordinal = 2))
    private void backrooms$inject2(CallbackInfo ci) {
        GL20C.glVertexAttribPointer(4, 1, 5126, false, this.lightSize, 28L);
    }

    @Inject(method = "setupBufferState", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL33C;glVertexAttribDivisor(II)V", ordinal = 2))
    private void backrooms$inject3(CallbackInfo ci) {
        GL33C.glVertexAttribDivisor(4, 1);
    }
}
