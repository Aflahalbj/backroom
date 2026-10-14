package com.backrooms.mixin;

import com.backrooms.client.CameraEffect;
import com.backrooms.client.ClientConfig;
import com.backrooms.util.TickTimer;
import com.backrooms.util.Timer;
import foundry.veil.api.client.util.Easings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Port dari {@code com.sp.mixin.hudandresolution.InGameHudMixin}: HUD ala
 * kamera -- hotbar meluncur turun 3 detik setelah slot terakhir diganti, bar
 * darah dan lapar transparan (20%), bar XP dan crosshair disembunyikan.
 * Semuanya hanya selama efek kamera aktif dan {@code useDefaultGUI} mati.
 * Bar lapar Forge digambar oleh {@code ForgeGui.renderFood} (lihat {@link ForgeGuiMixin}).
 */
@Mixin(Gui.class)
public abstract class GuiMixin {

    @Unique
    private Timer backrooms$hotbarSlideTimer;
    @Unique
    private TickTimer backrooms$hotbarHoldTimer;
    @Unique
    private Integer backrooms$prevSelectedSlot = 0;
    @Unique
    private double backrooms$hotbarPosition;

    @Inject(method = "renderHotbar", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER))
    private void backrooms$hotbarSlide1(float tickDelta, GuiGraphics context, CallbackInfo ci) {
        if (CameraEffect.shouldRenderCameraEffect()) {
            // Dibuat saat pertama dipakai: penginisialisasi field mixin di Gui tidak selalu jalan
            // (terlihat sebagai NullPointerException di overlay hotbar).
            if (this.backrooms$hotbarSlideTimer == null) {
                this.backrooms$hotbarSlideTimer = new Timer(500L, Easings.Easing.easeInCirc, Easings.Easing.easeOutCirc);
                this.backrooms$hotbarHoldTimer = new TickTimer();
            }
            this.backrooms$hotbarPosition = 45.0F * this.backrooms$hotbarSlideTimer.getCurrentTime();
            if (!ClientConfig.useDefaultGUI()) {
                context.pose().translate(0.0D, this.backrooms$hotbarPosition, 0.0D);
            }
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                int selectedSlot = client.player.getInventory().selected;
                if (this.backrooms$prevSelectedSlot != null) {
                    if (this.backrooms$prevSelectedSlot != selectedSlot) {
                        this.backrooms$hotbarSlideTimer.reverse();
                        this.backrooms$hotbarSlideTimer.startTimer();
                        this.backrooms$hotbarHoldTimer.resetToZero();
                    } else if (this.backrooms$hotbarHoldTimer.getCurrentTick() >= 60) {
                        this.backrooms$hotbarSlideTimer.forward();
                    }
                }
                this.backrooms$prevSelectedSlot = selectedSlot;
            }
        }
    }

    @Inject(method = "renderSlot", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 1, shift = At.Shift.AFTER))
    private void backrooms$itemCountFix(GuiGraphics context, int x, int y, float f, Player player, ItemStack stack, int seed, CallbackInfo ci) {
        if (!ClientConfig.useDefaultGUI()) {
            context.pose().translate(0.0D, this.backrooms$hotbarPosition, 0.0D);
        }
    }

    @Inject(method = "renderSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;III)V"))
    private void backrooms$hotbarSlide2(GuiGraphics context, int x, int y, float f, Player player, ItemStack stack, int seed, CallbackInfo ci) {
        if (CameraEffect.shouldRenderCameraEffect()) {
            context.pose().pushPose();
            if (!ClientConfig.useDefaultGUI()) {
                context.pose().translate(0.0D, this.backrooms$hotbarPosition, 0.0D);
            }
        }
    }

    @Inject(method = "renderSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V", shift = At.Shift.AFTER))
    private void backrooms$hotbarSlide3(GuiGraphics context, int x, int y, float f, Player player, ItemStack stack, int seed, CallbackInfo ci) {
        if (CameraEffect.shouldRenderCameraEffect()) {
            context.pose().popPose();
        }
    }

    @Inject(method = "renderHearts", at = @At("HEAD"))
    private void backrooms$setHealthOpacity1(GuiGraphics context, Player player, int x, int y, int lines, int regeneratingHeartIndex, float maxHealth,
                                             int lastHealth, int health, int absorption, boolean blinking, CallbackInfo ci) {
        backrooms$beginFaded(context);
    }

    @Inject(method = "renderHearts", at = @At("TAIL"))
    private void backrooms$setHealthOpacity2(GuiGraphics context, Player player, int x, int y, int lines, int regeneratingHeartIndex, float maxHealth,
                                             int lastHealth, int health, int absorption, boolean blinking, CallbackInfo ci) {
        backrooms$endFaded(context);
    }

    @Inject(method = {"renderExperienceBar", "renderCrosshair"}, at = @At("HEAD"), cancellable = true)
    private void backrooms$disable(CallbackInfo ci) {
        if (CameraEffect.shouldRenderCameraEffect() && !ClientConfig.useDefaultGUI()) {
            ci.cancel();
        }
    }

    @Unique
    private static void backrooms$beginFaded(GuiGraphics context) {
        if (CameraEffect.shouldRenderCameraEffect()) {
            if (!ClientConfig.useDefaultGUI()) {
                context.setColor(1.0F, 1.0F, 1.0F, 0.2F);
            }
            context.pose().pushPose();
            if (!ClientConfig.useDefaultGUI()) {
                context.pose().translate(0.0F, 5.0F, 0.0F);
            }
        }
    }

    @Unique
    private static void backrooms$endFaded(GuiGraphics context) {
        if (CameraEffect.shouldRenderCameraEffect()) {
            context.pose().popPose();
            if (!ClientConfig.useDefaultGUI()) {
                context.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }
    }
}
