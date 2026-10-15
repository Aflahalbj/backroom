package com.backrooms.mixin.skinstolen;

import com.backrooms.client.skinwalker.SkinWalkerClient;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Di daftar pemain (tab) saja, korban Skinwalker yang sebenarnya spectator ditampilkan sebagai
 * pemain biasa. Di luar tab (kamera, render pemain) mode aslinya tetap dipakai.
 */
@Mixin(PlayerInfo.class)
public class PlayerInfoMixin {
    @Shadow
    @Final
    private GameProfile profile;

    @Inject(method = "getGameMode", at = @At("HEAD"), cancellable = true)
    private void backrooms$hideCapturedSpectator(CallbackInfoReturnable<GameType> cir) {
        if (SkinWalkerClient.renderingTab && SkinWalkerClient.isCaptured(this.profile.getId())) {
            cir.setReturnValue(GameType.SURVIVAL);
        }
    }
}
