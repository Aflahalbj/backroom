package com.backrooms.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server &rarr; klien: status senter seorang pemain. {@code force} juga
 * berlaku untuk pemain itu sendiri (server mematikan senter semua pemain,
 * seperti {@code setFlashLightOn(false)} + sync komponen di aslinya).
 */
public record FlashlightS2C(UUID player, boolean on, boolean force) {

    public FlashlightS2C(UUID player, boolean on) {
        this(player, on, false);
    }

    public static void encode(FlashlightS2C msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.player);
        buf.writeBoolean(msg.on);
        buf.writeBoolean(msg.force);
    }

    public static FlashlightS2C decode(FriendlyByteBuf buf) {
        return new FlashlightS2C(buf.readUUID(), buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(FlashlightS2C msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.backrooms.client.ClientPackets.flashlight(msg.player, msg.on, msg.force)));
        ctx.get().setPacketHandled(true);
    }
}
