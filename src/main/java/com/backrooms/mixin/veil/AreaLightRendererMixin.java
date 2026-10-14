package com.backrooms.mixin.veil;

import foundry.veil.api.client.render.deferred.light.AreaLight;
import foundry.veil.api.client.render.deferred.light.renderer.InstancedLightRenderer;
import foundry.veil.impl.client.render.deferred.light.AreaLightRenderer;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL33C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Port dari {@code com.sp.mixin.arealightfix.AreaLightRendererMixin}: ukuran
 * data area light 92 byte dan atribut sudut dibaca sebagai float.
 */
@Mixin(value = AreaLightRenderer.class, remap = false)
public abstract class AreaLightRendererMixin extends InstancedLightRenderer<AreaLight> {

    public AreaLightRendererMixin(int lightSize) {
        super(lightSize);
    }

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lfoundry/veil/api/client/render/deferred/light/renderer/InstancedLightRenderer;<init>(I)V"))
    private static int backrooms$modify(int lightSize) {
        return 92;
    }

    @Inject(method = "setupBufferState", at = @At("HEAD"), cancellable = true)
    private void backrooms$lightFix(CallbackInfo ci) {
        ci.cancel();
        GL20C.glEnableVertexAttribArray(1);
        GL20C.glEnableVertexAttribArray(2);
        GL20C.glEnableVertexAttribArray(3);
        GL20C.glEnableVertexAttribArray(4);
        GL20C.glEnableVertexAttribArray(5);
        GL20C.glEnableVertexAttribArray(6);
        GL20C.glEnableVertexAttribArray(7);
        GL20C.glEnableVertexAttribArray(8);
        GL20C.glVertexAttribPointer(1, 4, 5126, false, this.lightSize, 0L);
        GL20C.glVertexAttribPointer(2, 4, 5126, false, this.lightSize, 16L);
        GL20C.glVertexAttribPointer(3, 4, 5126, false, this.lightSize, 32L);
        GL20C.glVertexAttribPointer(4, 4, 5126, false, this.lightSize, 48L);
        GL20C.glVertexAttribPointer(5, 3, 5126, false, this.lightSize, 64L);
        GL20C.glVertexAttribPointer(6, 2, 5126, false, this.lightSize, 76L);
        GL20C.glVertexAttribPointer(7, 1, 5126, false, this.lightSize, 84L);
        GL20C.glVertexAttribPointer(8, 1, 5126, false, this.lightSize, 88L);
        GL33C.glVertexAttribDivisor(1, 1);
        GL33C.glVertexAttribDivisor(2, 1);
        GL33C.glVertexAttribDivisor(3, 1);
        GL33C.glVertexAttribDivisor(4, 1);
        GL33C.glVertexAttribDivisor(5, 1);
        GL33C.glVertexAttribDivisor(6, 1);
        GL33C.glVertexAttribDivisor(7, 1);
        GL33C.glVertexAttribDivisor(8, 1);
    }
}
