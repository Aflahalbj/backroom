package com.backrooms.mixin.skinstolen;

import com.backrooms.component.PlayerComponent;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Korban Skinwalker (spectator) tidak bisa memakai menu teleport spectator (tombol 1) untuk kabur,
 * termasuk ke dimensi lain. Tambahan port ini; aslinya hanya kameranya yang dikunci.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleTeleportToEntityPacket", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
            shift = At.Shift.AFTER), cancellable = true)
    private void backrooms$noEscapeTeleport(ServerboundTeleportToEntityPacket packet, CallbackInfo ci) {
        PlayerComponent component = PlayerComponent.get(this.player);
        if (component.hasBeenCaptured() || component.isBeingCaptured()) {
            ci.cancel();
        }
    }
}
