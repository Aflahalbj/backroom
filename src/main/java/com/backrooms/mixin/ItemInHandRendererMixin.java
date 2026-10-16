package com.backrooms.mixin;

import com.backrooms.client.ClientConfig;
import com.backrooms.client.lobby.LobbyClient;
import com.backrooms.client.lobby.LobbyFx;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Port dari {@code com.sp.mixin.hudandresolution.DontRenderHandMixin}: tangan kosong tidak tampil (kecuali showHands).
 * Ditambah api moncong senjata karakter lobby.
 */
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

    /**
     * Api moncong digambar di ruang item tangan kanan (setelah semua
     * transformasi lengan, tekuk siku, dan item dari Player Animator), jadi
     * selalu menempel di ujung senjata.
     */
    @Inject(method = "renderItem", at = @At("HEAD"))
    private void backrooms$lobbyMuzzleFlash(LivingEntity entity, ItemStack stack, ItemDisplayContext context, boolean leftHand,
                                            PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (entity instanceof Player player && LobbyClient.inLobbyScene(player)) {
            if (context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
                LobbyFx.renderAtHand(player, poseStack, buffers, false);
            } else if (context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
                LobbyFx.renderAtHand(player, poseStack, buffers, true);
            }
        }
    }
}
