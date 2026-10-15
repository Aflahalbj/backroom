package com.backrooms.mixin.skinstolen;

import com.backrooms.client.skinwalker.SkinWalkerCapturedFlavorText;
import com.backrooms.component.PlayerComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Port dari {@code com.sp.mixin.skinstolen.MinecraftClientMixin}: korban tidak bisa membuka inventori. */
@Mixin(Minecraft.class)
public class MinecraftSkinStolenMixin {
    @Redirect(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V", ordinal = 1))
    private void disableInventory(Minecraft instance, Screen screen) {
        if (instance.player != null) {
            PlayerComponent component = PlayerComponent.get(instance.player);
            if (!component.hasBeenCaptured()) {
                instance.setScreen(screen);
            } else {
                SkinWalkerCapturedFlavorText.triedToOpenInventory = true;
            }
        }
    }
}
