package com.backrooms.network;

import com.backrooms.lobby.RoomInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server &rarr; klien: room tempat pemain ini bergabung ({@code null} = tidak di room mana pun). */
public record RoomS2C(RoomInfo room) {

    public static void encode(RoomS2C msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.room != null);
        if (msg.room != null) msg.room.write(buf);
    }

    public static RoomS2C decode(FriendlyByteBuf buf) {
        return new RoomS2C(buf.readBoolean() ? RoomInfo.read(buf) : null);
    }

    public static void handle(RoomS2C msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.backrooms.client.lobby.LobbyClient.onRoom(msg.room)));
        ctx.get().setPacketHandled(true);
    }
}
