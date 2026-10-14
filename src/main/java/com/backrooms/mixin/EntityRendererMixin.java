package com.backrooms.mixin;

import com.backrooms.world.BackroomsLevel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Port dari {@code com.sp.mixin.EntityRendererMixin}: nama pemain tidak tampil di dalam backrooms. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity> {

    @Inject(method = "renderNameTag", at = @At("HEAD"), cancellable = true)
    private void backrooms$disablePlayerTags(T entity, Component text, PoseStack matrices, MultiBufferSource vertexConsumers, int light, CallbackInfo ci) {
        if (entity instanceof AbstractClientPlayer && BackroomsLevel.at(entity.level(), entity.getX()) != null) {
            ci.cancel();
        }
    }
}
