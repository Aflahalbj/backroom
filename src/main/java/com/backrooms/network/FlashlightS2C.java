package com.backrooms.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Server &rarr; klien: status senter seorang pemain. */
public record FlashlightS2C(UUID player, boolean on) {

    public static void encode(FlashlightS2C msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.player);
        buf.writeBoolean(msg.on);
    }

    public static FlashlightS2C decode(FriendlyByteBuf buf) {
        return new FlashlightS2C(buf.readUUID(), buf.readBoolean());
    }

    public static void handle(FlashlightS2C msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.backrooms.client.ClientPackets.flashlight(msg.player, msg.on)));
        ctx.get().setPacketHandled(true);
    }
}
