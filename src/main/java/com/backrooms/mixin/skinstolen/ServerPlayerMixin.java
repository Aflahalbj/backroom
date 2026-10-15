package com.backrooms.mixin.skinstolen;

import com.backrooms.component.PlayerComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Port dari {@code com.sp.mixin.skinstolen.ServerPlayerEntityMixin}: korban terkunci melihat lewat mata Skinwalker. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;setCamera(Lnet/minecraft/world/entity/Entity;)V", ordinal = 0))
    private void cantEscapeSpectating(ServerPlayer instance, Entity entity) {
        PlayerComponent component = PlayerComponent.get(instance);
        if (!component.hasBeenCaptured()) {
            instance.setCamera(entity);
        }
    }

    @Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
    private void stopSpectatorPlayersFromNotBeingCounted(ServerPlayer spectator, CallbackInfoReturnable<Boolean> cir) {
        PlayerComponent component = PlayerComponent.get((ServerPlayer) (Object) this);
        if (component.hasBeenCaptured() || component.isBeingCaptured()) {
            cir.setReturnValue(true);
        }
    }

    @Redirect(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;setCamera(Lnet/minecraft/world/entity/Entity;)V"))
    private void dontChangeTargets(ServerPlayer instance, Entity entity) {
        PlayerComponent component = PlayerComponent.get((ServerPlayer) (Object) this);
        if (!component.hasBeenCaptured() && !component.isBeingCaptured()) {
            instance.setCamera(entity);
        }
    }
}
