package com.backrooms.network;

import com.backrooms.lobby.LobbyManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Klien &rarr; server: tombol-tombol di lobby dan room. */
public record LobbyActionC2S(Action action, int value, String text) {

    public enum Action {
        CREATE_ROOM,
        /** {@code value} = id room. */
        JOIN_ROOM,
        LEAVE_ROOM,
        START,
        /** Owner: {@code text} = UUID anggota yang dikeluarkan. */
        KICK,
        /** Owner: {@code text} = UUID anggota, {@code value} = indeks {@link com.backrooms.lobby.RoomInfo.Role}. */
        SET_ROLE,
        /** Owner: {@code value} = jumlah pemain per tim (1 = solo, 2 = duo, ...). */
        SET_TEAM_SIZE,
        /** Owner: {@code text} = UUID pemain yang dipindah ke tim berikutnya. */
        MOVE_TEAM,
        /** Owner: {@code text} = UUID pemain di lobby yang dimasukkan langsung ke room. */
        ADD_PLAYER,
        /** Owner: masukkan semua pemain di lobby yang belum ikut room (sampai room penuh). */
        ADD_ALL,
        /** Owner: minta daftar pemain yang bisa ditambahkan ({@link LobbyPlayersS2C}). */
        REQUEST_CANDIDATES,
        /** {@code value} = indeks {@link com.backrooms.world.BackroomsLevel}. */
        SET_BACKGROUND,
        /** {@code value} = indeks {@link com.backrooms.lobby.LobbyPose}. */
        SET_POSE,
        /** {@code value} = indeks {@link com.backrooms.lobby.LobbyWeapon}. */
        SET_WEAPON,
        /** Dari menu pause di dalam level: keluar room, kembali ke lobby. */
        GO_LOBBY,
        REQUEST_LIST
    }

    public LobbyActionC2S(Action action) {
        this(action, 0, "");
    }

    public LobbyActionC2S(Action action, int value) {
        this(action, value, "");
    }

    public static void encode(LobbyActionC2S msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.action);
        buf.writeVarInt(msg.value);
        buf.writeUtf(msg.text, 64);
    }

    public static LobbyActionC2S decode(FriendlyByteBuf buf) {
        return new LobbyActionC2S(buf.readEnum(Action.class), buf.readVarInt(), buf.readUtf(64));
    }

    public static void handle(LobbyActionC2S msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) LobbyManager.handle(player, msg);
        });
        ctx.get().setPacketHandled(true);
    }
}
