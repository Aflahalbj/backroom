package com.backrooms.network;

import com.backrooms.world.BackroomsLevel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Klien &rarr; server: status senter pemain ini. */
public record FlashlightC2S(boolean on) {

    public static void encode(FlashlightC2S msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.on);
    }

    public static FlashlightC2S decode(FriendlyByteBuf buf) {
        return new FlashlightC2S(buf.readBoolean());
    }

    public static void handle(FlashlightC2S msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            // Poolrooms melarang senter, sama dengan pengecekan di klien.
            boolean allowed = player.level().dimension() != BackroomsLevel.POOLROOMS.dimension();
            ModNetwork.setFlashlight(player, msg.on && allowed);
        });
        ctx.get().setPacketHandled(true);
    }
}
