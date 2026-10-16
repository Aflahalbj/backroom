package com.backrooms.lobby;

import com.backrooms.network.LobbyPlayersS2C;
import com.backrooms.network.ModNetwork;
import com.backrooms.network.RoomListS2C;
import com.backrooms.network.RoomS2C;
import com.backrooms.world.BackroomsLevel;
import com.backrooms.world.BuildArea;
import com.backrooms.world.LevelBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Room: sekelompok pemain yang masuk ke Backrooms yang sama. Hanya OP yang
 * bisa membuat room dan menjadi <i>owner</i>-nya; yang lain bergabung lewat
 * daftar room. Owner mengatur peran setiap anggota (pemain atau host, termasuk
 * dirinya sendiri), bisa langsung memasukkan pemain di lobby (satu-satu atau
 * semuanya) tanpa mereka bergabung sendiri, mode tim (solo/duo/trio/...), lalu menekan START. Saat itu
 * Backrooms milik room dibangun di slot baru (lihat {@link BuildArea}), dan
 * begitu selesai semua pemain masuk lewat cutscene noclip; host ikut masuk
 * sebagai penonton (spectator), tidak ikut main.
 *
 * <p>Tim juga dipasang sebagai tim scoreboard vanilla (warna nama, tanpa
 * friendly fire) selama pemainnya di room.
 *
 * <p>Room hanya hidup selama server menyala dan selama masih ada anggotanya.
 */
public final class RoomManager {

    /** Awalan nama tim scoreboard room; sisa dari server sebelumnya dibersihkan saat start. */
    private static final String TEAM_PREFIX = "brroom";

    static final class Room {
        final int id;
        final String name;
        /** Semua anggota (pemain dan host), urut bergabung. */
        final List<UUID> members = new ArrayList<>();
        final Set<UUID> hosts = new HashSet<>();
        /** Tim setiap pemain (indeks 0 = Tim 1); host tidak ada di sini. */
        final Map<UUID, Integer> teams = new HashMap<>();
        UUID owner;
        /** Pembuat room: kalau dia keluar lalu masuk lagi, jadi owner lagi. */
        final UUID creator;
        int teamSize = 1;
        RoomState state = RoomState.WAITING;
        /** Latar lobby room ini selama menunggu: pilihan owner. */
        BackroomsLevel background;
        /** Slot Backrooms room ini; 0 sampai owner menekan START. */
        int slot;
        int progress;

        Room(int id, String name, UUID owner, BackroomsLevel background) {
            this.id = id;
            this.name = name;
            this.owner = owner;
            this.creator = owner;
            this.background = background;
        }

        boolean isHost(UUID id) {
            return hosts.contains(id);
        }

        List<UUID> players() {
            return members.stream().filter(id -> !hosts.contains(id)).toList();
        }

        List<UUID> hostList() {
            return members.stream().filter(hosts::contains).toList();
        }

        int teamMembers(int team) {
            int count = 0;
            for (int t : teams.values()) {
                if (t == team) count++;
            }
            return count;
        }

        /** Masukkan pemain ke tim pertama yang masih muat. */
        void assignTeam(UUID id) {
            int count = RoomInfo.teamCount(teamSize);
            for (int team = 0; team < count; team++) {
                if (teamMembers(team) < teamSize) {
                    teams.put(id, team);
                    return;
                }
            }
            teams.put(id, count - 1);
        }

        /** Susun ulang semua tim berurutan (setelah mode tim berubah). */
        void reassignTeams() {
            teams.clear();
            List<UUID> players = players();
            for (int i = 0; i < players.size(); i++) {
                teams.put(players.get(i), i / teamSize);
            }
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

    /** Pemain ini host di room-nya (tidak ikut main). */
    static boolean isHost(ServerPlayer player) {
        Room room = roomOf(player);
        return room != null && room.isHost(player.getUUID());
    }

    static boolean canCreate(ServerPlayer player) {
        return player.hasPermissions(2);
    }

    // ------------------------------------------------------------ aksi

    static void create(ServerPlayer player) {
        if (!canCreate(player)) {
            player.displayClientMessage(Component.literal("Hanya OP yang bisa membuat room.").withStyle(ChatFormatting.RED), true);
            return;
        }
        leave(player, false);
        Room room = new Room(nextId++, "Room " + player.getGameProfile().getName(), player.getUUID(),
                LobbyManager.background(player));
        room.members.add(player.getUUID());
        room.assignTeam(player.getUUID());
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
        if (room.players().size() >= RoomInfo.MAX_PLAYERS) {
            player.displayClientMessage(Component.literal("Room penuh.").withStyle(ChatFormatting.RED), true);
            return;
        }
        leave(player, false);
        room.members.add(player.getUUID());
        room.assignTeam(player.getUUID());
        MEMBERSHIP.put(player.getUUID(), room);
        reclaimOwner(room, player);
        changed(player.getServer(), room);
        if (room.state == RoomState.PLAYING) {
            // Menyusul sendirian: cutscene perkenalannya hanya dia.
            LobbyManager.noclip(player, List.of(player.getUUID()));
        }
    }

    /** Pembuat room yang kembali (gabung lagi atau ditambahkan) mengambil lagi posisi owner. */
    private static void reclaimOwner(Room room, ServerPlayer player) {
        if (!player.getUUID().equals(room.creator) || room.owner.equals(room.creator)) return;
        room.owner = room.creator;
        if (room.state == RoomState.WAITING) room.background = LobbyManager.background(player);
    }

    /**
     * Keluar dari room (kalau ada). Owner yang keluar digantikan anggota berikutnya (OP didahulukan),
     * sampai pembuat room masuk lagi (lihat {@link #reclaimOwner}).
     */
    static void leave(ServerPlayer player, boolean loggingOut) {
        Room room = MEMBERSHIP.remove(player.getUUID());
        if (room == null) return;
        MinecraftServer server = player.getServer();
        UUID id = player.getUUID();
        room.members.remove(id);
        room.hosts.remove(id);
        room.teams.remove(id);
        removeFromTeams(server, player.getScoreboardName());
        if (room.members.isEmpty()) {
            ROOMS.remove(room.id);
            removeTeams(server, room);
        } else if (room.owner.equals(id)) {
            room.owner = nextOwner(server, room);
            ServerPlayer newOwner = server.getPlayerList().getPlayer(room.owner);
            if (newOwner != null && room.state == RoomState.WAITING) {
                room.background = LobbyManager.background(newOwner);
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

    private static UUID nextOwner(MinecraftServer server, Room room) {
        for (UUID id : room.members) {
            ServerPlayer member = server.getPlayerList().getPlayer(id);
            if (member != null && canCreate(member)) return id;
        }
        return room.members.get(0);
    }

    /** Room milik {@code owner} yang masih menunggu, atau {@code null} kalau dia bukan owner-nya. */
    private static Room ownedWaiting(ServerPlayer owner) {
        Room room = roomOf(owner);
        return room != null && room.owner.equals(owner.getUUID()) && room.state == RoomState.WAITING ? room : null;
    }

    static void kick(ServerPlayer owner, UUID target) {
        Room room = ownedWaiting(owner);
        if (room == null || target.equals(owner.getUUID()) || !room.members.contains(target)) return;
        ServerPlayer kicked = owner.getServer().getPlayerList().getPlayer(target);
        if (kicked != null) {
            leave(kicked, false);
            kicked.displayClientMessage(Component.literal("Kamu dikeluarkan dari room.").withStyle(ChatFormatting.RED), true);
            LobbyManager.refresh(kicked);
        } else {
            room.members.remove(target);
            room.hosts.remove(target);
            room.teams.remove(target);
            MEMBERSHIP.remove(target);
            changed(owner.getServer(), room);
        }
    }

    /** Pemain yang bisa ditambahkan owner: sedang di lobby dan belum ikut room mana pun. */
    private static List<ServerPlayer> candidates(MinecraftServer server) {
        return server.getPlayerList().getPlayers().stream()
                .filter(p -> LobbyManager.inLobby(p) && roomOf(p) == null).toList();
    }

    /** Kirim daftar calon anggota ke owner (kosong kalau dia bukan owner room yang menunggu). */
    static void sendCandidates(ServerPlayer owner) {
        List<RoomInfo.Candidate> list = new ArrayList<>();
        if (ownedWaiting(owner) != null) {
            for (ServerPlayer player : candidates(owner.getServer())) {
                list.add(new RoomInfo.Candidate(player.getUUID(), player.getGameProfile().getName()));
            }
        }
        ModNetwork.send(owner, new LobbyPlayersS2C(list));
    }

    /** Owner memasukkan pemain di lobby langsung ke room-nya sebagai pemain, tanpa dia bergabung sendiri. */
    static void add(ServerPlayer owner, UUID target) {
        Room room = ownedWaiting(owner);
        if (room == null) return;
        MinecraftServer server = owner.getServer();
        ServerPlayer added = server.getPlayerList().getPlayer(target);
        if (added != null && LobbyManager.inLobby(added) && roomOf(added) == null) {
            if (room.players().size() >= RoomInfo.MAX_PLAYERS) {
                owner.displayClientMessage(Component.literal("Room penuh.").withStyle(ChatFormatting.RED), true);
                return;
            }
            addMember(room, added, owner);
            changed(server, room);
        }
        sendCandidates(owner);
    }

    /** Owner memasukkan semua pemain di lobby yang belum ikut room, sampai pemainnya penuh. */
    static void addAll(ServerPlayer owner) {
        Room room = ownedWaiting(owner);
        if (room == null) return;
        MinecraftServer server = owner.getServer();
        int added = 0, left = 0;
        for (ServerPlayer player : candidates(server)) {
            if (room.players().size() >= RoomInfo.MAX_PLAYERS) {
                left++;
                continue;
            }
            addMember(room, player, owner);
            added++;
        }
        if (added > 0) changed(server, room);
        if (left > 0) {
            owner.displayClientMessage(Component.literal(added + " pemain ditambahkan, " + left + " tidak muat (room penuh).")
                    .withStyle(ChatFormatting.YELLOW), true);
        } else if (added > 0) {
            owner.displayClientMessage(Component.literal(added + " pemain ditambahkan.").withStyle(ChatFormatting.GREEN), true);
        }
        sendCandidates(owner);
    }

    private static void addMember(Room room, ServerPlayer player, ServerPlayer owner) {
        room.members.add(player.getUUID());
        room.assignTeam(player.getUUID());
        MEMBERSHIP.put(player.getUUID(), room);
        reclaimOwner(room, player);
        player.displayClientMessage(Component.literal("Kamu dimasukkan ke " + room.name + " oleh "
                + owner.getGameProfile().getName() + ".").withStyle(ChatFormatting.GREEN), true);
    }

    /** Owner menjadikan anggota (atau dirinya) pemain atau host. */
    static void setRole(ServerPlayer owner, UUID target, RoomInfo.Role role) {
        Room room = ownedWaiting(owner);
        if (room == null || !room.members.contains(target) || room.isHost(target) == (role == RoomInfo.Role.HOST)) return;
        if (role == RoomInfo.Role.HOST) {
            if (room.hosts.size() >= RoomInfo.MAX_HOSTS) {
                owner.displayClientMessage(Component.literal("Host sudah " + RoomInfo.MAX_HOSTS + " orang.").withStyle(ChatFormatting.RED), true);
                return;
            }
            room.hosts.add(target);
            room.teams.remove(target);
        } else {
            if (room.players().size() >= RoomInfo.MAX_PLAYERS) {
                owner.displayClientMessage(Component.literal("Pemain sudah penuh.").withStyle(ChatFormatting.RED), true);
                return;
            }
            room.hosts.remove(target);
            room.assignTeam(target);
        }
        changed(owner.getServer(), room);
    }

    static void setTeamSize(ServerPlayer owner, int size) {
        Room room = ownedWaiting(owner);
        if (room == null || size < 1 || size > RoomInfo.MAX_TEAM_SIZE || size == room.teamSize) return;
        room.teamSize = size;
        room.reassignTeams();
        changed(owner.getServer(), room);
    }

    /**
     * Owner memindahkan pemain ke tim berikutnya yang masih muat. Kalau semua
     * tim lain penuh, dia bertukar tempat dengan anggota terakhir tim berikutnya.
     */
    static void moveTeam(ServerPlayer owner, UUID target) {
        Room room = ownedWaiting(owner);
        Integer current = room == null ? null : room.teams.get(target);
        if (current == null) return;
        int count = RoomInfo.teamCount(room.teamSize);
        if (count < 2) return;
        for (int step = 1; step < count; step++) {
            int team = (current + step) % count;
            if (room.teamMembers(team) < room.teamSize) {
                room.teams.put(target, team);
                changed(owner.getServer(), room);
                return;
            }
        }
        int next = (current + 1) % count;
        UUID swap = null;
        for (UUID id : room.players()) {
            if (room.teams.get(id) == next) swap = id;
        }
        if (swap == null) return;
        room.teams.put(swap, current);
        room.teams.put(target, next);
        changed(owner.getServer(), room);
    }

    static void start(ServerPlayer owner) {
        Room room = ownedWaiting(owner);
        if (room == null) return;
        if (room.players().isEmpty()) {
            owner.displayClientMessage(Component.literal("Belum ada pemain: jadikan seseorang pemain dulu.").withStyle(ChatFormatting.RED), true);
            return;
        }
        MinecraftServer server = owner.getServer();
        room.slot = RoomSlots.allocate(server);
        room.state = RoomState.BUILDING;
        room.progress = 0;
        changed(server, room);
        LevelBuilder.build(server, BuildArea.room(BackroomsLevel.LEVEL0, room.slot), () -> {
            if (ROOMS.get(room.id) != room) return;
            room.state = RoomState.PLAYING;
            room.progress = 100;
            changed(server, room);
            // Pemain dan host sama-sama ikut cutscene jatuh; mendaratnya beda (lihat LobbyManager.land).
            // Urutan perkenalan = urutan barisan di lobby (pemain dulu, host di ujung).
            List<UUID> lineup = new ArrayList<>();
            for (UUID id : LobbyManager.lineup(room)) {
                ServerPlayer member = server.getPlayerList().getPlayer(id);
                if (member != null && LobbyManager.inLobby(member)) lineup.add(id);
            }
            for (UUID id : List.copyOf(room.members)) {
                ServerPlayer member = server.getPlayerList().getPlayer(id);
                if (member != null) LobbyManager.noclip(member, lineup.contains(id) ? lineup : List.of(id));
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

    // ------------------------------------------------- tim scoreboard

    private static String teamName(Room room, int team) {
        return TEAM_PREFIX + room.id + "_" + (team + 1);
    }

    /** Samakan tim scoreboard dengan tim room: pemain di timnya, host tanpa tim. */
    private static void applyTeams(MinecraftServer server, Room room) {
        ServerScoreboard scoreboard = server.getScoreboard();
        for (UUID id : room.members) {
            ServerPlayer member = server.getPlayerList().getPlayer(id);
            if (member == null) continue;
            String entry = member.getScoreboardName();
            Integer team = room.teams.get(id);
            if (team == null) {
                removeFromTeams(server, entry);
                continue;
            }
            String name = teamName(room, team);
            PlayerTeam target = scoreboard.getPlayerTeam(name);
            if (target == null) {
                target = scoreboard.addPlayerTeam(name);
                ChatFormatting color = RoomInfo.teamFormat(team);
                target.setDisplayName(Component.literal("Tim " + (team + 1)));
                target.setColor(color);
                target.setPlayerPrefix(Component.literal("[T" + (team + 1) + "] ").withStyle(color));
                target.setAllowFriendlyFire(false);
                target.setSeeFriendlyInvisibles(true);
            }
            if (scoreboard.getPlayersTeam(entry) != target) {
                scoreboard.addPlayerToTeam(entry, target);
            }
        }
        // Tim yang sudah kosong (mode tim berubah, anggota pindah) dibuang.
        String prefix = TEAM_PREFIX + room.id + "_";
        for (PlayerTeam team : List.copyOf(scoreboard.getPlayerTeams())) {
            if (team.getName().startsWith(prefix) && team.getPlayers().isEmpty()) scoreboard.removePlayerTeam(team);
        }
    }

    private static void removeFromTeams(MinecraftServer server, String entry) {
        ServerScoreboard scoreboard = server.getScoreboard();
        PlayerTeam team = scoreboard.getPlayersTeam(entry);
        if (team != null && team.getName().startsWith(TEAM_PREFIX)) scoreboard.removePlayerFromTeam(entry, team);
    }

    private static void removeTeams(MinecraftServer server, Room room) {
        removeTeams(server, TEAM_PREFIX + room.id + "_");
    }

    private static void removeTeams(MinecraftServer server, String prefix) {
        ServerScoreboard scoreboard = server.getScoreboard();
        for (PlayerTeam team : List.copyOf(scoreboard.getPlayerTeams())) {
            if (team.getName().startsWith(prefix)) scoreboard.removePlayerTeam(team);
        }
    }

    /** Tim scoreboard room dari sesi server sebelumnya ikut tersimpan di world: buang semuanya. */
    static void removeStaleTeams(MinecraftServer server) {
        removeTeams(server, TEAM_PREFIX);
    }

    // ------------------------------------------------------- sinkronisasi

    /** Isi room berubah: kabari anggotanya, perbarui daftar room, atur ulang barisan di lobby. */
    private static void changed(MinecraftServer server, Room room) {
        applyTeams(server, room);
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
            boolean host = room.isHost(id);
            members.add(new RoomInfo.Member(id, member == null ? "?" : member.getGameProfile().getName(),
                    host ? RoomInfo.Role.HOST : RoomInfo.Role.PLAYER, host ? -1 : room.teams.getOrDefault(id, 0),
                    member == null ? 0 : LobbyManager.pose(member).ordinal(),
                    member == null ? 0 : LobbyManager.weapon(member).ordinal()));
        }
        return new RoomInfo(room.id, room.name, room.owner, room.state, room.progress, room.teamSize, members);
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
            ServerPlayer owner = server.getPlayerList().getPlayer(room.owner);
            list.add(new RoomInfo.Summary(room.id, room.name, owner == null ? "?" : owner.getGameProfile().getName(),
                    room.players().size(), room.teamSize, room.state));
        }
        return list;
    }
}
