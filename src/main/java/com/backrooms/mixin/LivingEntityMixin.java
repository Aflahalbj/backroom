package com.backrooms.mixin;

import com.backrooms.world.BackroomsLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Di dimensi lobby pemain tidak saling dorong: mereka berdiri berdekatan di
 * panggung, dan dorongan menggeser karakter (dan posenya) sampai server
 * menaruhnya balik. Di klien ini menahan dorongan ke pemain sendiri, di
 * server ke semua pemain.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private void backrooms$noPushInLobby(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player && BackroomsLevel.LOBBY.equals(player.level().dimension())) {
            cir.setReturnValue(false);
        }
    }
}
