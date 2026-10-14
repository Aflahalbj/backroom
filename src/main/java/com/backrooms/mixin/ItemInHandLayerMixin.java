package com.backrooms.mixin;

import com.backrooms.client.lobby.LobbyClient;
import com.backrooms.client.lobby.LobbyWeapons;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Di lobby karakter memegang senjata pilihannya (TACZ) di tangan kanan dan
 * tangan kirinya kosong, apa pun isi inventorinya.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {

    @Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack backrooms$lobbyMainHand(LivingEntity entity) {
        if (entity instanceof Player player && LobbyClient.inLobbyScene(player)) {
            return LobbyWeapons.stack(LobbyClient.weaponOf(player));
        }
        return entity.getMainHandItem();
    }

    @Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getOffhandItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack backrooms$lobbyOffhand(LivingEntity entity) {
        if (entity instanceof Player player && LobbyClient.inLobbyScene(player)) {
            return ItemStack.EMPTY;
        }
        return entity.getOffhandItem();
    }
}
