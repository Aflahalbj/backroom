package com.backrooms.mixin;

import com.backrooms.client.ClientConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Port dari {@code com.sp.mixin.hudandresolution.DontRenderHandMixin}: tangan kosong tidak tampil (kecuali showHands). */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    @Shadow
    protected abstract void renderPlayerArm(PoseStack poseStack, MultiBufferSource buffer, int light, float equip, float swing, HumanoidArm arm);

    @Redirect(method = "renderArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderPlayerArm(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IFFLnet/minecraft/world/entity/HumanoidArm;)V"))
    private void backrooms$hideHand(ItemInHandRenderer instance, PoseStack poseStack, MultiBufferSource buffer, int light, float equip, float swing, HumanoidArm arm) {
        if (ClientConfig.showHands()) {
            this.renderPlayerArm(poseStack, buffer, light, equip, swing, arm);
        }
    }
}
