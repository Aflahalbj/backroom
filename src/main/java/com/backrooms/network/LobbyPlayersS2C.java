package com.backrooms.network;

import com.backrooms.lobby.RoomInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/** Server &rarr; klien (owner): pemain di lobby yang belum ikut room, untuk layar "Tambah pemain". */
public record LobbyPlayersS2C(List<RoomInfo.Candidate> players) {

    public static void encode(LobbyPlayersS2C msg, FriendlyByteBuf buf) {
        buf.writeCollection(msg.players, (b, player) -> player.write(b));
    }

    public static LobbyPlayersS2C decode(FriendlyByteBuf buf) {
        return new LobbyPlayersS2C(buf.readList(RoomInfo.Candidate::read));
    }

    public static void handle(LobbyPlayersS2C msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.backrooms.client.lobby.LobbyClient.onCandidates(msg.players)));
        ctx.get().setPacketHandled(true);
    }
}
