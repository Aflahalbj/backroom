package com.backrooms.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server &rarr; klien: status {@code WorldEvents} salinan level tempat pemain berada (sync CCA). */
public record WorldEventsS2C(CompoundTag tag) {

    public static void encode(WorldEventsS2C msg, FriendlyByteBuf buf) {
        buf.writeNbt(msg.tag);
    }

    public static WorldEventsS2C decode(FriendlyByteBuf buf) {
        return new WorldEventsS2C(buf.readNbt());
    }

    public static void handle(WorldEventsS2C msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.backrooms.client.skinwalker.ClientWorldEvents.read(msg.tag)));
        ctx.get().setPacketHandled(true);
    }
}
