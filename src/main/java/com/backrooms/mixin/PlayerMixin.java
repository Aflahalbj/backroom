package com.backrooms.mixin;

import com.backrooms.component.PlayerComponent;
import com.backrooms.world.events.WorldEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bagian Skinwalker dari {@code com.sp.mixin.PlayerEntityMixin.onDeath}:
 * pemain yang mati saat ditangkap Skinwalker dilepas. Seperti aslinya
 * disuntikkan ke {@code Player.die} (yang tidak dipanggil {@code ServerPlayer.die}).
 */
@Mixin(Player.class)
public abstract class PlayerMixin {

    @Inject(method = "die", at = @At("HEAD"))
    private void backrooms$onDeath(DamageSource damageSource, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (!(player.level() instanceof ServerLevel level)) return;
        PlayerComponent playerComponent = PlayerComponent.get(player);
        WorldEvents events = WorldEvents.get(level, player.getX());
        if (playerComponent.hasBeenCaptured() || playerComponent.isBeingCaptured()
                || events.activeSkinWalkerEntity != null && events.activeSkinWalkerEntity.getTarget() == player) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.setCamera(serverPlayer);
            }
            if (events.activeSkinWalkerEntity != null) {
                events.activeSkinWalkerEntity.discard();
            }
            events.activeSkinWalkerEntity = null;
            playerComponent.setBeingCaptured(false);
            playerComponent.setBeingReleased(false);
            playerComponent.setHasBeenCaptured(false);
            playerComponent.setShouldBeMuted(false);
            playerComponent.sync();
        }
    }
}
