package com.backrooms.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server &rarr; semua klien: apakah seorang pemain sedang jadi korban Skinwalker, supaya
 * daftar pemain (tab) tidak membocorkannya sebagai spectator (lihat {@code PlayerInfoMixin}).
 */
public record CapturedPlayerS2C(UUID player, boolean captured) {

    public static void encode(CapturedPlayerS2C msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.player);
        buf.writeBoolean(msg.captured);
    }

    public static CapturedPlayerS2C decode(FriendlyByteBuf buf) {
        return new CapturedPlayerS2C(buf.readUUID(), buf.readBoolean());
    }

    public static void handle(CapturedPlayerS2C msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.backrooms.client.skinwalker.SkinWalkerClient.setCaptured(msg.player, msg.captured)));
        ctx.get().setPacketHandled(true);
    }
}
