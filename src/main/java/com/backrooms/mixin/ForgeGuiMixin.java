package com.backrooms.mixin;

import com.backrooms.client.CameraEffect;
import com.backrooms.client.ClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bagian bar lapar dari {@code InGameHudMixin} asli. Di vanilla bar lapar
 * ada di {@code renderStatusBars}; di Forge digambar oleh {@code ForgeGui.renderFood}.
 */
@Mixin(value = ForgeGui.class, remap = false)
public abstract class ForgeGuiMixin {

    @Inject(method = "renderFood", at = @At("HEAD"))
    private void backrooms$setHungerOpacity1(int width, int height, GuiGraphics context, CallbackInfo ci) {
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

    @Inject(method = "renderFood", at = @At("TAIL"))
    private void backrooms$setHungerOpacity2(int width, int height, GuiGraphics context, CallbackInfo ci) {
        if (CameraEffect.shouldRenderCameraEffect()) {
            context.pose().popPose();
            if (!ClientConfig.useDefaultGUI()) {
                context.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }
    }
}
