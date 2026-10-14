package com.backrooms.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Port dari {@code enableDeferredResourcePack} di {@code com.sp.mixin.MinecraftClientMixin}:
 * resource pack bawaan Veil {@code veil:deferred} (renderer deferred yang
 * dibutuhkan semua shader mod asli) selalu dinyalakan kembali.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Unique
    private static final Logger BACKROOMS$LOGGER = LogUtils.getLogger();

    @Shadow
    private static Minecraft instance;

    @Shadow
    @Final
    public Options options;

    @Shadow
    @Final
    private PackRepository resourcePackRepository;

    @Inject(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runTick(Z)V", shift = At.Shift.AFTER))
    private void backrooms$enableDeferredResourcePack(CallbackInfo ci) {
        if (instance != null && this.resourcePackRepository != null) {
            Pack deferred = this.resourcePackRepository.getPack("veil:deferred");
            if (deferred != null && !this.resourcePackRepository.getSelectedPacks().contains(deferred)) {
                BACKROOMS$LOGGER.info("Re-enabled Deferred Resourcepack");
                this.resourcePackRepository.addPack("veil:deferred");
                this.options.updateResourcePacks(this.resourcePackRepository);
            }
        }
    }
}
