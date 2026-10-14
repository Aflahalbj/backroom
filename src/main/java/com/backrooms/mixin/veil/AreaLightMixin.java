package com.backrooms.mixin.veil;

import foundry.veil.api.client.render.deferred.light.AreaLight;
import foundry.veil.api.client.render.deferred.light.Light;
import net.minecraft.util.Mth;
import org.joml.Matrix4d;
import org.joml.Vector2f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;

/**
 * Port dari {@code com.sp.mixin.arealightfix.AreaLightMixin}: sudut area light
 * disimpan sebagai float (bukan short), sesuai shader {@code spbrevamped:area}.
 */
@Mixin(value = AreaLight.class, remap = false)
public abstract class AreaLightMixin extends Light {

    @Shadow
    @Final
    private Matrix4d matrix;

    @Shadow
    @Final
    protected Vector2f size;

    @Shadow
    protected float angle;

    @Shadow
    protected float distance;

    @Inject(method = "store", at = @At("HEAD"), cancellable = true)
    private void backrooms$lightFix(ByteBuffer buffer, CallbackInfo ci) {
        ci.cancel();
        this.matrix.getFloats(buffer.position(), buffer);
        buffer.position(buffer.position() + 64);
        buffer.putFloat(this.color.x() * this.brightness);
        buffer.putFloat(this.color.y() * this.brightness);
        buffer.putFloat(this.color.z() * this.brightness);
        this.size.get(buffer.position(), buffer);
        buffer.position(buffer.position() + 8);
        buffer.putFloat((float) Mth.clamp((int) (this.angle * 10430.06F), 0, 65535));
        buffer.putFloat(this.distance);
    }
}
