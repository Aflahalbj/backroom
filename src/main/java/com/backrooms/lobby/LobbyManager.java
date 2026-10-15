package com.backrooms.lobby;

import com.backrooms.network.CutsceneS2C;
import com.backrooms.network.LobbyActionC2S;
import com.backrooms.network.LobbyStateS2C;
import com.backrooms.network.ModNetwork;
import com.backrooms.network.RoomS2C;
import com.backrooms.world.BackroomsLevel;
import com.backrooms.world.BuildArea;
import com.backrooms.world.LevelBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lobby: tempat setiap pemain berada begitu masuk server.
 *
 * <p>Latar lobby adalah set kecil (80x80) dari level aslinya, dibangun
 * generator asli di dimensi lobby ({@link BackroomsLevel#LOBBY}); ketiganya
 * berjajar di sana, jadi ganti latar hanya berpindah tempat di dimensi yang
 * sama tanpa layar "Loading terrain" (lihat {@link BuildArea#lobby}). Pemain
 * berdiri diam di "panggung" set itu (lihat {@link StageFinder}) dengan
 * kamera tetap di depannya; pemain satu room berdiri berjajar di latar
 * pilihan owner (pemain dulu, host di ujung barisan). Selama di lobby pemain kebal dan tidak bisa bergerak --
 * layar lobby di klien menahan semua input.
 *
 * <p>Saat Backrooms room selesai dibangun, setiap anggota menjalani
 * {@link #noclip}: karakternya tenggelam menembus lantai (di klien), lalu
 * server memindahkannya ke titik noclip aslinya di Level 0 room itu dan
 * klien memutar intro asli.
 */
public final class LobbyManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String BACKGROUND_KEY = "backrooms:lobby_background";
    /** Disimpan +1 supaya pemain baru (tag belum ada = 0) mendapat pose bawaan (Auto). */
    private static final String POSE_KEY = "backrooms:lobby_pose_v2";
    private static final String WEAPON_KEY = "backrooms:lobby_weapon";
    /** Mode permainan host sebelum dijadikan penonton; dikembalikan begitu dia kembali ke lobby. */
    private static final String HOST_MODE_KEY = "backrooms:host_prev_mode";
    /** Lama animasi tenggelam di klien sebelum pemain dipindahkan. */
    private static final int NOCLIP_TICKS = 60;
    /** Titik mendarat noclip, koordinat asli Level 0 ({@code checkBackroomsTeleport} di mod aslinya). */
    private static final Vec3 NOCLIP_TARGET = new Vec3(1.5D, 22.0D, 1.5D);

    private static final Set<UUID> IN_LOBBY = new LinkedHashSet<>();
    private static final Map<UUID, Integer> NOCLIP = new HashMap<>();
    private static final Map<BackroomsLevel, LobbyStage> STAGES = new EnumMap<>(BackroomsLevel.class);

    private LobbyManager() {}

    // ------------------------------------------------------- server hidup

    public static void onServerStarted(MinecraftServer server) {
        RoomManager.removeStaleTeams(server);
        for (BackroomsLevel which : BackroomsLevel.values()) {
            BuildArea area = BuildArea.lobby(which);
            LevelBuilder.build(server, area, () -> {
                ServerLevel level = server.getLevel(area.dimension());
                if (level == null) return;
                LobbyData data = LobbyData.get(level, which);
                if (data.stage() == null) {
                    data.setStage(StageFinder.find(level, area));
                }
                LobbySet.prepare(level, area);
                STAGES.put(which, data.stage());
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (inLobby(player)) refresh(player);
                }
            }, () -> LOGGER.error("Set lobby {} gagal dibangun.", area.describe()));
        }
    }

    public static void clear() {
        IN_LOBBY.clear();
        NOCLIP.clear();
        STAGES.clear();
        RoomManager.clear();
    }

    public static void tick(MinecraftServer server) {
        RoomManager.tick(server);
        Iterator<Map.Entry<UUID, Integer>> it = NOCLIP.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            if (entry.getValue() > 1) {
                entry.setValue(entry.getValue() - 1);
                continue;
            }
            it.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) land(player);
        }
        if (server.getTickCount() % 10 != 0) return;
        for (UUID id : Set.copyOf(IN_LOBBY)) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null || NOCLIP.containsKey(id)) continue;
            if (STAGES.get(shownBackground(player)) == null) {
                // Set lobby-nya masih dibangun: kirim progresnya.
                sendState(player);
            } else if (server.getTickCount() % 20 == 0) {
                place(player, false);
            }
        }
    }

    public static LobbyStage stage(BackroomsLevel which) {
        return STAGES.get(which);
    }

    // --------------------------------------------------------- event

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) enter(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RoomManager.leave(player, true);
            NOCLIP.remove(player.getUUID());
            if (IN_LOBBY.remove(player.getUUID())) {
                // Dipanggil sebelum data pemain disimpan: jangan tinggalkan flag kebal di save.
                player.setInvulnerable(false);
            }
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isEndConquered()) return;
        if (RoomManager.isPlaying(player)) {
            BuildArea area = RoomManager.areaFor(player, BackroomsLevel.LEVEL0);
            ServerLevel level = player.getServer().getLevel(BackroomsLevel.LEVEL0.dimension());
            if (level != null) {
                Vec3 spawn = area.worldSpawn();
                player.teleportTo(level, spawn.x, spawn.y, spawn.z, player.getYRot(), player.getXRot());
                return;
            }
        }
        enter(player);
    }

    // ---------------------------------------------------- masuk / keluar

    public static boolean inLobby(ServerPlayer player) {
        return IN_LOBBY.contains(player.getUUID());
    }

    public static void enter(ServerPlayer player) {
        NOCLIP.remove(player.getUUID());
        IN_LOBBY.add(player.getUUID());
        player.setInvulnerable(true);
        player.fallDistance = 0;
        restoreMode(player);
        place(player, true);
        sendState(player);
        ModNetwork.send(player, new RoomS2C(RoomManager.info(player)));
        RoomManager.sendList(player);
    }

    /** Keluar dari keadaan lobby (dipindah ke level lain). Keanggotaan room tetap. */
    public static void exit(ServerPlayer player) {
        NOCLIP.remove(player.getUUID());
        if (IN_LOBBY.remove(player.getUUID())) {
            player.setInvulnerable(false);
            sendState(player);
        }
    }

    /** Dari menu pause atau {@code /backroom lobby}: keluar room dan kembali ke lobby. */
    public static void goToLobby(ServerPlayer player) {
        RoomManager.leave(player, false);
        enter(player);
    }

    /** Tempatkan ulang dan kirim ulang keadaan lobby pemain ini (setelah room/latar berubah). */
    static void refresh(ServerPlayer player) {
        if (!inLobby(player)) return;
        place(player, false);
        sendState(player);
    }

    /** Latar yang sedang dilihat pemain ini: milik room (pilihan owner) kalau ikut room. */
    private static BackroomsLevel shownBackground(ServerPlayer player) {
        RoomManager.Room room = RoomManager.roomOf(player);
        return room != null ? room.background : background(player);
    }

    private static void place(ServerPlayer player, boolean force) {
        if (!inLobby(player) || NOCLIP.containsKey(player.getUUID())) return;
        BackroomsLevel which = shownBackground(player);
        LobbyStage stage = STAGES.get(which);
        ServerLevel level = player.getServer().getLevel(BuildArea.lobby(which).dimension());
        if (stage == null || level == null) return;
        RoomManager.Room room = RoomManager.roomOf(player);
        // Barisan: pemain dulu, host di ujung (host tetap tampil dengan posenya).
        List<UUID> lineup = room == null ? List.of() : lineup(room);
        int index = Math.max(0, lineup.indexOf(player.getUUID()));
        Vec3 target = stage.slot(index, Math.max(1, lineup.size()));
        if (force || player.level() != level || player.position().distanceToSqr(target) > 0.09D) {
            player.fallDistance = 0;
            player.teleportTo(level, target.x, target.y, target.z, stage.playerYaw(), 0.0F);
        }
    }

    private static List<UUID> lineup(RoomManager.Room room) {
        List<UUID> lineup = new java.util.ArrayList<>(room.players());
        lineup.addAll(room.hostList());
        return lineup;
    }

    private static void sendState(ServerPlayer player) {
        boolean active = inLobby(player);
        BackroomsLevel which = shownBackground(player);
        LobbyStage stage = active ? STAGES.get(which) : null;
        int percent = stage != null ? 100 : Math.max(0, LevelBuilder.progress(player.getServer(), BuildArea.lobby(which)));
        ModNetwork.send(player, new LobbyStateS2C(active, background(player).ordinal(), pose(player).ordinal(), weapon(player).ordinal(), stage, percent));
    }

    // ------------------------------------------------------------ noclip

    /** Mulai masuk ke Backrooms room: karakter tenggelam di lobby, lalu {@link #land}. */
    static void noclip(ServerPlayer player) {
        if (!inLobby(player)) {
            land(player);
            return;
        }
        NOCLIP.put(player.getUUID(), NOCLIP_TICKS);
        ModNetwork.send(player, new CutsceneS2C(CutsceneS2C.Type.NOCLIP, 0, 0));
    }

    private static void land(ServerPlayer player) {
        if (!RoomManager.isPlaying(player)) {
            if (inLobby(player)) refresh(player);
            return;
        }
        BuildArea area = RoomManager.areaFor(player, BackroomsLevel.LEVEL0);
        ServerLevel level = player.getServer().getLevel(BackroomsLevel.LEVEL0.dimension());
        if (level == null) return;
        exit(player);
        Vec3 target = area.toWorld(NOCLIP_TARGET);
        player.fallDistance = 0;
        if (RoomManager.isHost(player)) {
            // Host tidak ikut main: masuk sebagai penonton, tanpa intro.
            CompoundTag data = persisted(player);
            if (!data.contains(HOST_MODE_KEY)) data.putInt(HOST_MODE_KEY, player.gameMode.getGameModeForPlayer().getId());
            player.setGameMode(GameType.SPECTATOR);
            player.teleportTo(level, target.x, target.y, target.z, player.getYRot(), player.getXRot());
            return;
        }
        player.teleportTo(level, target.x, target.y, target.z, player.getYRot(), player.getXRot());
        ModNetwork.send(player, new CutsceneS2C(CutsceneS2C.Type.INTRO, area.offsetX(), area.offsetZ()));
    }

    /** Host yang kembali ke lobby (atau login lagi) mendapat mode permainannya yang semula. */
    private static void restoreMode(ServerPlayer player) {
        CompoundTag data = persisted(player);
        if (!data.contains(HOST_MODE_KEY)) return;
        player.setGameMode(GameType.byId(data.getInt(HOST_MODE_KEY)));
        data.remove(HOST_MODE_KEY);
    }

    // ------------------------------------------------------------ aksi

    public static void handle(ServerPlayer player, LobbyActionC2S msg) {
        boolean lobby = inLobby(player) && !NOCLIP.containsKey(player.getUUID());
        switch (msg.action()) {
            case CREATE_ROOM -> {
                if (lobby) RoomManager.create(player);
            }
            case JOIN_ROOM -> {
                if (lobby) RoomManager.join(player, msg.value());
            }
            case LEAVE_ROOM -> {
                if (lobby) {
                    RoomManager.leave(player, false);
                    refresh(player);
                }
            }
            case START -> {
                if (lobby) RoomManager.start(player);
            }
            case KICK -> {
                UUID target = uuid(msg.text());
                if (lobby && target != null) RoomManager.kick(player, target);
            }
            case SET_ROLE -> {
                UUID target = uuid(msg.text());
                RoomInfo.Role[] roles = RoomInfo.Role.values();
                if (lobby && target != null && msg.value() >= 0 && msg.value() < roles.length) {
                    RoomManager.setRole(player, target, roles[msg.value()]);
                }
            }
            case SET_TEAM_SIZE -> {
                if (lobby) RoomManager.setTeamSize(player, msg.value());
            }
            case MOVE_TEAM -> {
                UUID target = uuid(msg.text());
                if (lobby && target != null) RoomManager.moveTeam(player, target);
            }
            case ADD_PLAYER -> {
                UUID target = uuid(msg.text());
                if (lobby && target != null) RoomManager.add(player, target);
            }
            case ADD_ALL -> {
                if (lobby) RoomManager.addAll(player);
            }
            case REQUEST_CANDIDATES -> {
                if (lobby) RoomManager.sendCandidates(player);
            }
            case SET_BACKGROUND -> setBackground(player, msg.value());
            case SET_POSE -> setPose(player, msg.value());
            case SET_WEAPON -> setWeapon(player, msg.value());
            case GO_LOBBY -> {
                // Korban Skinwalker tidak bisa kabur ("There Is No Escape").
                if (!inLobby(player) && !com.backrooms.component.PlayerComponent.get(player).hasBeenCaptured()) goToLobby(player);
            }
            case REQUEST_LIST -> RoomManager.sendList(player);
        }
    }

    /** UUID dari klien; {@code null} kalau rusak. */
    private static UUID uuid(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void setBackground(ServerPlayer player, int index) {
        BackroomsLevel[] levels = BackroomsLevel.values();
        if (index < 0 || index >= levels.length) return;
        RoomManager.Room room = RoomManager.roomOf(player);
        // Di dalam room hanya owner yang memilih latar.
        if (room != null && !room.owner.equals(player.getUUID())) return;
        persisted(player).putInt(BACKGROUND_KEY, index);
        if (room != null && room.owner.equals(player.getUUID()) && room.state == RoomState.WAITING) {
            room.background = levels[index];
            for (UUID id : room.members) {
                ServerPlayer member = player.getServer().getPlayerList().getPlayer(id);
                if (member != null) refresh(member);
            }
        } else {
            refresh(player);
        }
        if (!inLobby(player)) sendState(player);
    }

    private static void setPose(ServerPlayer player, int index) {
        persisted(player).putInt(POSE_KEY, LobbyPose.byId(index).ordinal() + 1);
        sendState(player);
        RoomManager.Room room = RoomManager.roomOf(player);
        if (room != null) RoomManager.sync(player.getServer(), room);
    }

    // ------------------------------------------------- pilihan tersimpan

    static BackroomsLevel background(ServerPlayer player) {
        BackroomsLevel[] levels = BackroomsLevel.values();
        int index = persisted(player).getInt(BACKGROUND_KEY);
        return index >= 0 && index < levels.length ? levels[index] : BackroomsLevel.LEVEL0;
    }

    private static void setWeapon(ServerPlayer player, int index) {
        // Disimpan +1 supaya pemain baru (tag belum ada = 0) mendapat senjata bawaan.
        persisted(player).putInt(WEAPON_KEY, LobbyWeapon.byId(index).ordinal() + 1);
        sendState(player);
        RoomManager.Room room = RoomManager.roomOf(player);
        if (room != null) RoomManager.sync(player.getServer(), room);
    }

    static LobbyWeapon weapon(ServerPlayer player) {
        int stored = persisted(player).getInt(WEAPON_KEY);
        return stored == 0 ? LobbyWeapon.DEFAULT : LobbyWeapon.byId(stored - 1);
    }

    static LobbyPose pose(ServerPlayer player) {
        int stored = persisted(player).getInt(POSE_KEY);
        return stored == 0 ? LobbyPose.DEFAULT : LobbyPose.byId(stored - 1);
    }

    private static CompoundTag persisted(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(Player.PERSISTED_NBT_TAG)) {
            data.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return data.getCompound(Player.PERSISTED_NBT_TAG);
    }
}
