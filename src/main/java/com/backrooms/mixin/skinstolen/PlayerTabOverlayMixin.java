package com.backrooms.mixin.skinstolen;

import com.backrooms.client.skinwalker.SkinWalkerClient;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Selama daftar pemain digambar (termasuk pengurutannya), korban Skinwalker tidak dianggap
 * spectator -- lihat {@link PlayerInfoMixin}. Tambahan port ini; aslinya korban terlihat
 * sebagai spectator di tab.
 */
@Mixin(PlayerTabOverlay.class)
public class PlayerTabOverlayMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void backrooms$beginTab(CallbackInfo ci) {
        SkinWalkerClient.renderingTab = true;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void backrooms$endTab(CallbackInfo ci) {
        SkinWalkerClient.renderingTab = false;
    }
}
