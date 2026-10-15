package com.backrooms.mixin.skinstolen;

import com.backrooms.client.skinwalker.SkinWalkerCapturedFlavorText;
import com.backrooms.component.PlayerComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Port dari {@code com.sp.mixin.skinstolen.ChatScreenMixin}: korban tidak bisa chat (kecuali command "release"). */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
    protected ChatScreenMixin(Component title) {
        super(title);
    }

    @Redirect(method = "handleChatInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;sendChat(Ljava/lang/String;)V"))
    private void noOneCanHearYouScream(ClientPacketListener instance, String content) {
        PlayerComponent component = PlayerComponent.get(this.minecraft.player);
        if (!component.hasBeenCaptured()) {
            instance.sendChat(content);
        } else {
            SkinWalkerCapturedFlavorText.triedToChat = true;
        }
    }

    @Redirect(method = "handleChatInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;sendCommand(Ljava/lang/String;)V"))
    private void noOneCanHearYouScream2(ClientPacketListener instance, String content) {
        PlayerComponent component2 = PlayerComponent.get(this.minecraft.player);
        if (component2.hasBeenCaptured() && !content.contains("release")) {
            SkinWalkerCapturedFlavorText.triedToChat = true;
        } else {
            instance.sendCommand(content);
        }
    }
}
