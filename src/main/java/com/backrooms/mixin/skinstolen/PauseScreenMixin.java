package com.backrooms.mixin.skinstolen;

import com.backrooms.client.skinwalker.SkinWalkerCapturedFlavorText;
import com.backrooms.component.PlayerComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Port dari {@code com.sp.mixin.skinstolen.GameMenuScreenMixin}: tombol
 * keluar/putus diganti "There Is No Escape" selama ditangkap.
 *
 * <p>Aslinya {@code ordinal = 1} (tombol kedua yang dibuat {@code Button.builder}
 * di menu pause vanilla = tombol keluar). Forge menyisipkan tombol "Mods"
 * sebelum tombol itu, jadi di sini tombol yang sama ada di {@code ordinal = 2}.
 */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Redirect(method = "createPauseMenu", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/Button;builder(Lnet/minecraft/network/chat/Component;Lnet/minecraft/client/gui/components/Button$OnPress;)Lnet/minecraft/client/gui/components/Button$Builder;",
            ordinal = 2))
    private Button.Builder noEscape(Component message, Button.OnPress onPress) {
        Minecraft client1 = Minecraft.getInstance();
        if (client1.player != null) {
            PlayerComponent component = PlayerComponent.get(client1.player);
            if (component.hasBeenCaptured()) {
                return new Button.Builder(Component.translatable("skinwalker.flavor-text.leave").withStyle(ChatFormatting.RED), button -> {
                    button.active = true;
                    SkinWalkerCapturedFlavorText.triedToLeave = true;
                });
            }
        }
        return new Button.Builder(message, onPress);
    }
}
