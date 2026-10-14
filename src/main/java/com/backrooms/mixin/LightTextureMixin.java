package com.backrooms.mixin;

import com.backrooms.client.render.PoolroomsDayCycle;
import com.backrooms.world.BackroomsLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Port dari {@code com.sp.mixin.lightmap.PoolroomsSkylightColor}: warna
 * cahaya langit di lightmap tidak lagi kebiruan, dan di Poolrooms mengikuti
 * warna matahari {@link PoolroomsDayCycle}.
 */
@Mixin(LightTexture.class)
public abstract class LightTextureMixin {

    @Redirect(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lorg/joml/Vector3f;lerp(Lorg/joml/Vector3fc;F)Lorg/joml/Vector3f;", ordinal = 0, remap = false))
    private Vector3f backrooms$skylightColor(Vector3f instance, Vector3fc other, float t) {
        ClientLevel clientWorld = Minecraft.getInstance().level;
        float f = clientWorld.getSkyDarken(1.0F);
        Vector3f baseColor = new Vector3f(f);
        return clientWorld.dimension() == BackroomsLevel.POOLROOMS.dimension() ? baseColor.mul(PoolroomsDayCycle.getLightColor()) : baseColor;
    }
}
