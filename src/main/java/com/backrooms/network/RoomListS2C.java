package com.backrooms.network;

import com.backrooms.lobby.RoomInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/** Server &rarr; klien: semua room yang aktif, untuk layar "Gabung Room". */
public record RoomListS2C(List<RoomInfo.Summary> rooms) {

    public static void encode(RoomListS2C msg, FriendlyByteBuf buf) {
        buf.writeCollection(msg.rooms, (b, room) -> room.write(b));
    }

    public static RoomListS2C decode(FriendlyByteBuf buf) {
        return new RoomListS2C(buf.readList(RoomInfo.Summary::read));
    }

    public static void handle(RoomListS2C msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.backrooms.client.lobby.LobbyClient.onRoomList(msg.rooms)));
        ctx.get().setPacketHandled(true);
    }
}
