package com.backrooms.lobby;

import com.backrooms.network.ModNetwork;
import com.backrooms.network.RoomListS2C;
import com.backrooms.network.RoomS2C;
import com.backrooms.world.BackroomsLevel;
import com.backrooms.world.BuildArea;
import com.backrooms.world.LevelBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Room: sekelompok pemain yang masuk ke Backrooms yang sama. Host membuat
 * room, yang lain bergabung lewat daftar room, lalu host menekan START.
 * Saat itu Backrooms milik room dibangun di slot baru (lihat {@link BuildArea}),
 * dan begitu selesai semua anggota masuk lewat cutscene noclip.
 *
 * <p>Room hanya hidup selama server menyala dan selama masih ada anggotanya.
 */
public final class RoomManager {

    static final class Room {
        final int id;
        final String name;
        final List<UUID> members = new ArrayList<>();
        UUID host;
        RoomState state = RoomState.WAITING;
        /** Latar lobby room ini selama menunggu: pilihan host. */
        BackroomsLevel background;
        /** Slot Backrooms room ini; 0 sampai host menekan START. */
        int slot;
        int progress;

        Room(int id, String name, UUID host, BackroomsLevel background) {
            this.id = id;
            this.name = name;
            this.host = host;
            this.background = background;
        }
    }

    private static final Map<Integer, Room> ROOMS = new LinkedHashMap<>();
    private static final Map<UUID, Room> MEMBERSHIP = new HashMap<>();
    private static int nextId = 1;

    private RoomManager() {}

    static Room roomOf(ServerPlayer player) {
        return MEMBERSHIP.get(player.getUUID());
    }

    /** Area level {@code which} untuk pemain ini: milik room-nya kalau sudah mulai, selain itu bangunan utama. */
    public static BuildArea areaFor(ServerPlayer player, BackroomsLevel which) {
        Room room = roomOf(player);
        return room != null && room.slot > 0 ? BuildArea.room(which, room.slot) : BuildArea.main(which);
    }

    static boolean isPlaying(ServerPlayer player) {
        Room room = roomOf(player);
        return room != null && room.state == RoomState.PLAYING;
    }

    // ------------------------------------------------------------ aksi

    static void create(ServerPlayer player) {
        leave(player, false);
        Room room = new Room(nextId++, "Room " + player.getGameProfile().getName(), player.getUUID(),
                LobbyManager.background(player));
        room.members.add(player.getUUID());
        ROOMS.put(room.id, room);
        MEMBERSHIP.put(player.getUUID(), room);
        changed(player.getServer(), room);
    }

    static void join(ServerPlayer player, int id) {
        Room room = ROOMS.get(id);
        if (room == null) {
            player.displayClientMessage(Component.literal("Room itu sudah bubar.").withStyle(ChatFormatting.RED), true);
            sendList(player);
            return;
        }
        if (room == roomOf(player)) return;
        if (room.members.size() >= RoomInfo.MAX_MEMBERS) {
            player.displayClientMessage(Component.literal("Room penuh.").withStyle(ChatFormatting.RED), true);
            return;
        }
        leave(player, false);
        room.members.add(player.getUUID());
        MEMBERSHIP.put(player.getUUID(), room);
        changed(player.getServer(), room);
        if (room.state == RoomState.PLAYING) {
            LobbyManager.noclip(player);
        }
    }

    /** Keluar dari room (kalau ada). Host yang keluar digantikan anggota berikutnya. */
    static void leave(ServerPlayer player, boolean loggingOut) {
        Room room = MEMBERSHIP.remove(player.getUUID());
        if (room == null) return;
        MinecraftServer server = player.getServer();
        room.members.remove(player.getUUID());
        if (room.members.isEmpty()) {
            ROOMS.remove(room.id);
        } else if (room.host.equals(player.getUUID())) {
            room.host = room.members.get(0);
            ServerPlayer newHost = server.getPlayerList().getPlayer(room.host);
            if (newHost != null && room.state == RoomState.WAITING) {
                room.background = LobbyManager.background(newHost);
            }
        }
        if (!loggingOut) {
            ModNetwork.send(player, new RoomS2C(null));
        }
        if (room.members.isEmpty()) {
            broadcastList(server);
        } else {
            changed(server, room);
        }
    }

    static void kick(ServerPlayer host, UUID target) {
        Room room = roomOf(host);
        if (room == null || !room.host.equals(host.getUUID()) || target.equals(host.getUUID())
                || !room.members.contains(target) || room.state != RoomState.WAITING) {
            return;
        }
        ServerPlayer kicked = host.getServer().getPlayerList().getPlayer(target);
        if (kicked != null) {
            leave(kicked, false);
            kicked.displayClientMessage(Component.literal("Kamu dikeluarkan dari room.").withStyle(ChatFormatting.RED), true);
            LobbyManager.refresh(kicked);
        } else {
            room.members.remove(target);
            MEMBERSHIP.remove(target);
            changed(host.getServer(), room);
        }
    }

    static void start(ServerPlayer host) {
        Room room = roomOf(host);
        if (room == null || !room.host.equals(host.getUUID()) || room.state != RoomState.WAITING) return;
        MinecraftServer server = host.getServer();
        room.slot = RoomSlots.allocate(server);
        room.state = RoomState.BUILDING;
        room.progress = 0;
        changed(server, room);
        LevelBuilder.build(server, BuildArea.room(BackroomsLevel.LEVEL0, room.slot), () -> {
            if (ROOMS.get(room.id) != room) return;
            room.state = RoomState.PLAYING;
            room.progress = 100;
            changed(server, room);
            for (UUID id : List.copyOf(room.members)) {
                ServerPlayer member = server.getPlayerList().getPlayer(id);
                if (member != null) LobbyManager.noclip(member);
            }
        }, () -> {
            room.state = RoomState.WAITING;
            room.slot = 0;
            changed(server, room);
        });
    }

    static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) return;
        for (Room room : ROOMS.values()) {
            if (room.state != RoomState.BUILDING) continue;
            int progress = Math.max(0, LevelBuilder.progress(server, BuildArea.room(BackroomsLevel.LEVEL0, room.slot)));
            if (progress != room.progress) {
                room.progress = progress;
                sync(server, room);
            }
        }
    }

    static void clear() {
        ROOMS.clear();
        MEMBERSHIP.clear();
        nextId = 1;
    }

    // ------------------------------------------------------- sinkronisasi

    /** Isi room berubah: kabari anggotanya, perbarui daftar room, atur ulang barisan di lobby. */
    private static void changed(MinecraftServer server, Room room) {
        sync(server, room);
        broadcastList(server);
        for (UUID id : room.members) {
            ServerPlayer member = server.getPlayerList().getPlayer(id);
            if (member != null) LobbyManager.refresh(member);
        }
    }

    static void sync(MinecraftServer server, Room room) {
        RoomInfo info = info(server, room);
        for (UUID id : room.members) {
            ServerPlayer member = server.getPlayerList().getPlayer(id);
            if (member != null) ModNetwork.send(member, new RoomS2C(info));
        }
    }

    static RoomInfo info(ServerPlayer player) {
        Room room = roomOf(player);
        return room == null ? null : info(player.getServer(), room);
    }

    private static RoomInfo info(MinecraftServer server, Room room) {
        List<RoomInfo.Member> members = new ArrayList<>();
        for (UUID id : room.members) {
            ServerPlayer member = server.getPlayerList().getPlayer(id);
            members.add(new RoomInfo.Member(id, member == null ? "?" : member.getGameProfile().getName(),
                    member == null ? 0 : LobbyManager.pose(member).ordinal(),
                    member == null ? 0 : LobbyManager.weapon(member).ordinal()));
        }
        return new RoomInfo(room.id, room.name, room.host, room.state, room.progress, members);
    }

    static void sendList(ServerPlayer player) {
        ModNetwork.send(player, new RoomListS2C(summaries(player.getServer())));
    }

    static void broadcastList(MinecraftServer server) {
        RoomListS2C list = new RoomListS2C(summaries(server));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (LobbyManager.inLobby(player)) ModNetwork.send(player, list);
        }
    }

    private static List<RoomInfo.Summary> summaries(MinecraftServer server) {
        List<RoomInfo.Summary> list = new ArrayList<>();
        for (Room room : ROOMS.values()) {
            ServerPlayer host = server.getPlayerList().getPlayer(room.host);
            list.add(new RoomInfo.Summary(room.id, room.name, host == null ? "?" : host.getGameProfile().getName(),
                    room.members.size(), room.state));
        }
        return list;
    }
}
