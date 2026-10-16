package com.backrooms.client.lobby;

import com.backrooms.client.light.LightTicks;
import com.backrooms.lobby.DropTiming;
import com.backrooms.lobby.LobbyStage;
import com.backrooms.lobby.LobbyWeapon;
import com.backrooms.lobby.RoomInfo;
import com.backrooms.mixin.CameraAccessor;
import com.backrooms.sound.ModSounds;
import foundry.veil.api.client.render.deferred.light.PointLight;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cutscene perkenalan room: begitu Backrooms room siap, lantai lobby di bawah
 * barisan anggota bolong dan semuanya jatuh; selama jatuh kamera menyambar ke
 * tiap anggota satu per satu, waktu melambat (bullet time), dia beraksi
 * dengan senjatanya, dan kartu namanya dibanting masuk. Ditutup bingkai
 * bersama lalu semua menyelam ke kegelapan; server lalu memindahkan pemain ke
 * Level 0 dan intro aslinya berjalan. Jadwalnya di {@link DropTiming}.
 *
 * <p>Rujukan: dolly zoom <i>Vertigo</i> saat lantai hilang, "hang time" kartun
 * sebelum jatuh, <i>Leap of Faith</i> (Spider-Verse) untuk pose terbalik,
 * shootdodge <i>Max Payne</i>/bullet time <i>The Matrix</i> untuk gerak lambat,
 * kartu nama ala <i>Suicide Squad</i>/<i>Borderlands</i>/Apex, dan serpihan
 * dunia yang melayang ala <i>Inception</i>.
 *
 * <p>Semua di sisi klien: pemain tetap berdiri di panggung di server; yang
 * digeser hanya gambarnya ({@link #renderOffset}), arah badannya
 * ({@link #bodyYaw}), dan kameranya. Waktu adegan ({@link #scene}) berjalan
 * lebih lambat saat bullet time; animasi Player Animator ikut melambat lewat
 * {@link #animSpeed}.
 */
public final class DropCutscene {

    /** Gravitasi dan kecepatan terminal jatuh (blok/detik waktu adegan). */
    private static final double GRAVITY = 24.0D;
    private static final double TERMINAL = 26.0D;
    /** Kamera berpindah ke anggota berikutnya (ms nyata): pelan dan halus, bukan menyambar. */
    private static final long WHIP = DropTiming.SLOW_IN_END;
    /** Akhir giliran: waktu kembali normal sebelum menyambar lagi. */
    private static final long SPEEDUP = 320L;
    /** Bagian akhir: semua menyelam (ms sejak finale mulai). */
    /** Lama bingkai bersama (kartu BACKROOM WAR) sebelum kamera menyelam. */
    static final long DIVE = 3700L;
    /** Selesai cutscene, layar tetap hitam paling lama sekian ms menunggu server memindahkan pemain. */
    private static final long WAIT_TELEPORT = 5000L;

    /** Pistol tangan kiri untuk gerakan akimbo (TACZ). */
    static final String PISTOL = "tacz:deagle_golden";
    static final String PISTOL_SOUND = "tacz:deagle/deagle_shoot_3p";
    /** Bunyi selongsong jatuh per jenis senjata (TACZ). */
    private static final String CASING_SOUND = "tacz:springfield1873/springfield1873_shell_casing";
    private static final String SHELL_SOUND = "tacz:m870/12_gauge_shotgun_shell_drop";
    private static final String SNIPER_CASING_SOUND = "tacz:m700/m700_shell_drop";

    /** Satu anggota yang ikut jatuh. Posisi kaki relatif ke titik jatuh bersama. */
    static final class Member {
        final UUID id;
        final int index;
        final String name;
        final String animation;
        final LobbyWeapon weapon;
        /** {@code null} kalau anggota ini tidak ada di data room (mis. pratinjau). */
        final RoomInfo.Member info;
        final double seed;
        Vec3 start = Vec3.ZERO;
        Vec3 ring = Vec3.ZERO;
        /** Tempat di formasi V bagian akhir (menghadap kamera). */
        Vec3 line = Vec3.ZERO;
        float startYaw;
        float ringYaw;
        // keadaan frame ini
        Vec3 rel = Vec3.ZERO;
        float yaw;

        Member(UUID id, int index, String name, String animation, LobbyWeapon weapon, RoomInfo.Member info) {
            this.id = id;
            this.index = index;
            this.name = name;
            this.animation = animation;
            this.weapon = weapon;
            this.info = info;
            this.seed = (id.getLeastSignificantBits() & 0xFFFF) / 6553.6D;
        }

        DropMoves.Move move() {
            DropMoves.Move move = DropMoves.get(animation);
            if (forcedCam == null || index != 0) return move;
            double[] c = forcedCam;
            return new DropMoves.Move(move.animation(), move.title(), move.akimbo(), c[0], c[0] - 0.4D,
                    c[1], c[1] - 6.0D, c[2], c[2] + 4.0D, c[3], 0.0F, 0.0F);
        }
    }

    /** Posisi kamera, titik yang ditatap, roll, dan FOV (koordinat dunia). */
    private record Shot(Vec3 pos, Vec3 target, float roll, float fov) {}

    private static boolean running;
    private static long startNanos;
    private static long lastNanos;
    private static long time;
    private static double scene;
    /** Waktu nyata (ms cutscene) yang sudah dihitung ke {@link #scene}. */
    private static long sceneClock;
    private static float speed = 1.0F;
    private static List<Member> members = List.of();
    private static final Map<UUID, Member> BY_ID = new HashMap<>();
    private static ClientLevel level;
    private static Vec3 origin0 = Vec3.ZERO;
    private static Vec3 forward = new Vec3(0, 0, 1);
    private static Vec3 right = new Vec3(-1, 0, 0);
    private static double ringRadius;
    /** Jarak kamera ke formasi V di bagian akhir (cukup jauh supaya semua masuk bingkai). */
    private static double lineDistance;
    private static Shot lobbyShot;
    private static float baseFov = 70.0F;
    private static Vec3 anchor;
    private static long lastEventTime = -1L;
    private static long levelChangedAt = -1L;
    private static WindSound wind;
    /**
     * Tata cahaya tiga titik selama di kegelapan bawah lantai: key light hangat
     * di atas kamera, rim light biru di belakang yang sedang disorot, dan
     * cahaya kuning neon dari lubang di atas kelompok.
     */
    private static PointLight keyLight;
    private static PointLight rimLight;
    private static PointLight holeLight;
    private static Vec3 subject = Vec3.ZERO;

    // ---- efek: guncangan, kilatan, waktu membeku
    /** "Trauma" guncangan kamera 0..1 (meluruh), dikuadratkan saat dipakai. */
    private static float trauma;
    private static long fovKickAt = -1L;
    private static float glitch;
    private static float distortion;
    /** Sampai kapan (ms cutscene) impact frame tampil (layar dibalik warnanya). */
    private static long impactUntil = -1L;
    /** Waktu (ms cutscene) sampai hitstop berakhir. */
    private static long hitstopUntil = -1L;
    /** Waktu adegan saat giliran anggota yang sedang tampil dimulai (untuk kamera per tick animasi). */
    private static double slotScene;
    private static int slotIndex = -1;
    private static long nextHeartbeat;
    private static final List<Scheduled> SCHEDULED = new ArrayList<>();
    /** Suara panjang (terompet, dengung) yang dipotong paksa saat layar hitam. */
    private static final List<SoundInstance> LONG_SOUNDS = new ArrayList<>();

    private record Scheduled(long at, Runnable action) {}

    /** Pratinjau (AnimDev {@code dropmove}): gerakan anggota pertama dipaksa, atau {@code null}. */
    static String forcedMove;
    /** AnimDev: kamera anggota pertama (jarak, sudut, elevasi, tinggi), untuk mencari sudut tanpa build ulang. */
    static double[] forcedCam;

    /** Gaya jatuh sebelum giliran: penerjun melengkung, duduk santai, jungkir balik pelan. */
    private static final String[] IDLES = {"drop_jatuh", "drop_jatuh_duduk", "drop_jatuh_perut"};

    private static Vec3 camPos = Vec3.ZERO;
    private static float camYaw;
    private static float camPitch;
    private static float camRoll;
    private static float fov = 70.0F;

    private DropCutscene() {}

    // ============================================================ mulai

    /**
     * Mulai cutscene untuk anggota {@code lineup} (urutan perkenalan). Dipanggil
     * saat paket dari server datang, di set lobby dengan kamera lobby saat ini.
     */
    static void start(List<UUID> lineup, LobbyStage stage, Vec3 lobbyCamPos, float lobbyCamYaw, float lobbyCamPitch) {
        stop();
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || stage == null || lineup.isEmpty()) return;
        level = client.level;
        forward = new Vec3(stage.facing().getStepX(), 0.0D, stage.facing().getStepZ());
        right = new Vec3(-forward.z, 0.0D, forward.x);
        RoomInfo room = LobbyClient.room();
        List<Member> list = new ArrayList<>();
        List<LobbyWeapon> weapons = new ArrayList<>();
        for (UUID id : lineup) {
            RoomInfo.Member info = room == null ? null : room.member(id);
            Player player = level.getPlayerByUUID(id);
            weapons.add(info != null ? LobbyWeapon.byId(info.weapon()) : player == client.player ? LobbyClient.weapon() : LobbyWeapon.DEFAULT);
        }
        List<String> moves = DropMoves.assign(lineup, room == null ? 0L : room.id());
        if (forcedMove != null && !moves.isEmpty()) moves.set(0, forcedMove);
        for (int i = 0; i < lineup.size(); i++) {
            UUID id = lineup.get(i);
            RoomInfo.Member info = room == null ? null : room.member(id);
            Player player = level.getPlayerByUUID(id);
            String name = info != null ? info.name() : player != null ? player.getGameProfile().getName() : nameOf(id);
            list.add(new Member(id, i, name, moves.get(i), weapons.get(i), info));
        }
        members = List.copyOf(list);
        BY_ID.clear();
        for (Member m : members) BY_ID.put(m.id, m);

        // Titik jatuh bersama: tengah barisan yang terlihat di layar (setelah pertukaran tempat lobby).
        Vec3 sum = Vec3.ZERO;
        List<Vec3> drawn = new ArrayList<>();
        for (Member m : members) {
            Player player = level.getPlayerByUUID(m.id);
            Vec3 at;
            if (player != null) {
                Vec3 shift = LobbyClient.lobbyOffset(player);
                at = player.position().add(shift == null ? Vec3.ZERO : shift);
            } else {
                at = stage.slot(m.index, members.size());
            }
            drawn.add(at);
            sum = sum.add(at);
        }
        Vec3 center = sum.scale(1.0D / members.size());
        origin0 = new Vec3(center.x, Vec3.atBottomCenterOf(stage.pos()).y, center.z);

        // Formasi lingkaran penerjun: anggota pertama di sisi kamera, menghadap ke luar.
        int n = members.size();
        ringRadius = n <= 1 ? 0.0D : Mth.clamp(1.1D + 0.36D * n, 1.9D, 4.4D);
        for (Member m : members) {
            m.start = drawn.get(m.index).subtract(origin0);
            m.startYaw = stage.playerYaw();
            double angle = Math.atan2(forward.z, forward.x) + Math.PI * 2.0D * m.index / Math.max(1, n);
            Vec3 out = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
            double lift = n <= 1 ? 0.0D : 0.9D * Math.sin(m.index * 2.4D);
            m.ring = out.scale(ringRadius).add(0.0D, lift, 0.0D);
            m.ringYaw = n <= 1 ? stage.playerYaw() : yawOf(out);
            m.rel = m.start;
            m.yaw = m.startYaw;
        }
        // Formasi akhir: V menghadap kamera, yang tengah paling depan; lebih dari 6 jadi dua baris.
        int rows = n > 6 ? 2 : 1;
        int perRow = (n + rows - 1) / rows;
        double widest = 0.0D;
        for (Member m : members) {
            int row = m.index / perRow;
            int inRow = Math.min(perRow, n - row * perRow);
            double lateral = (m.index % perRow - (inRow - 1) / 2.0D) * 1.35D;
            widest = Math.max(widest, Math.abs(lateral));
            m.line = right.scale(lateral).add(forward.scale(-Math.abs(lateral) * 0.45D - row * 1.6D))
                    .add(0.0D, -Math.abs(lateral) * 0.18D + row * 1.25D, 0.0D);
        }
        lineDistance = Math.max(3.0D, widest * 1.05D + 2.3D);

        baseFov = client.options.fov().get().floatValue();
        Vec3 look = Vec3.directionFromRotation(lobbyCamPitch, lobbyCamYaw);
        lobbyShot = new Shot(lobbyCamPos, lobbyCamPos.add(look.scale(4.0D)), 0.0F, baseFov);
        anchor = client.player.position();
        client.player.setNoGravity(true);

        DropScenery.prepare(level, drawn, stage);
        running = true;
        startNanos = System.nanoTime();
        lastNanos = startNanos;
        time = 0L;
        scene = 0.0D;
        sceneClock = 0L;
        speed = 1.0F;
        lastEventTime = -1L;
        levelChangedAt = -1L;
        trauma = 0.0F;
        fovKickAt = -1L;
        glitch = 0.0F;
        distortion = 0.0F;
        impactUntil = -1L;
        hitstopUntil = -1L;
        slotIndex = -1;
        slotScene = 0.0D;
        nextHeartbeat = 0L;
        SCHEDULED.clear();
        LONG_SOUNDS.clear();
        DropFx.clear();
        camPos = lobbyCamPos;
        camYaw = lobbyCamYaw;
        camPitch = lobbyCamPitch;
        camRoll = 0.0F;
        fov = baseFov;
        playUi(ModSounds.available("glitch") ? ModSounds.event("glitch") : null, 1.0F, 1.0F);
    }

    /** Selesai atau dibatalkan: lantai dikembalikan, kontrol dilepas. */
    static void stop() {
        if (!running) return;
        running = false;
        DropScenery.restore();
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) client.player.setNoGravity(false);
        if (wind != null) wind.end();
        wind = null;
        removeLights();
        DropFx.clear();
        SCHEDULED.clear();
        cutLongSounds();
        members = List.of();
        BY_ID.clear();
        speed = 1.0F;
    }

    private static String nameOf(UUID id) {
        var connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(id);
        return info != null ? info.getProfile().getName() : "???";
    }

    // ======================================================== keadaan

    public static boolean isRunning() {
        return running;
    }

    /** Kamera dipegang cutscene ini (bukan lobby). */
    public static boolean ownsCamera() {
        return running && levelChangedAt < 0L && time < total();
    }

    /** Kamera sudah di jurang di bawah lantai lobby. */
    public static boolean inAbyss() {
        return ownsCamera() && time > DropTiming.DROP + 900L;
    }

    /**
     * Sejak lantai runtuh: langit dan kabut di bawah lantai gelap (set Poolrooms
     * memakai biome Poolrooms yang langit/kabutnya putih, jadi lubangnya tampak abu-abu).
     */
    public static boolean darkVoid() {
        return ownsCamera() && time > DropTiming.BREAK;
    }

    /** Pengali kabut cahaya matahari Poolrooms: memudar dari lantai runtuh sampai kamera masuk jurang. */
    public static float sunHaze() {
        if (!ownsCamera()) return 1.0F;
        float u = Mth.clamp((float) (time - DropTiming.BREAK) / (float) (DropTiming.DROP + 600L - DropTiming.BREAK), 0.0F, 1.0F);
        return 1.0F - u * u * (3.0F - 2.0F * u);
    }

    /**
     * Kamera sudah jauh di bawah lubang: blok dunia tidak digambar, supaya
     * struktur set latar di bawah lantai (tiang, dinding kolam) tidak lewat di jurang.
     */
    public static boolean hideTerrain() {
        return ownsCamera() && time > DropTiming.DROP + 1500L;
    }

    /** Server sudah memindahkan pemain; tinggal layar hitam sampai intro Level 0 mengambil alih. */
    static boolean landed() {
        return running && levelChangedAt >= 0L;
    }

    static List<Member> members() {
        return members;
    }

    static long time() {
        return time;
    }

    static long total() {
        return DropTiming.total(members.size());
    }

    static long slot() {
        return DropTiming.slot(members.size());
    }

    static long finaleStart() {
        return DropTiming.finaleStart(members.size());
    }

    /** Anggota yang sedang diperkenalkan (indeks), -1 sebelum/sesudah giliran. */
    static int currentIndex() {
        if (time < DropTiming.INTRO_START || time >= finaleStart()) return -1;
        return (int) ((time - DropTiming.INTRO_START) / slot());
    }

    /** ms sejak giliran anggota yang sedang diperkenalkan dimulai. */
    static long slotTime() {
        return (time - DropTiming.INTRO_START) % slot();
    }

    static long whip() {
        return WHIP;
    }

    static float speed() {
        return speed;
    }

    /** Kecepatan animasi karakter (1 = normal); melambat saat bullet time. */
    static float animSpeed() {
        return running ? speed : 1.0F;
    }

    /**
     * Kecepatan animasi pemain ini: anggota yang sedang disorot memutar aksinya
     * dengan kecepatan normal (gerak lambatnya sudah dipanggang di animasinya,
     * jadi tetap mulus); yang lain ikut melambat bersama dunia.
     */
    static float animSpeed(Player player) {
        if (!running) return 1.0F;
        Member m = BY_ID.get(player.getUUID());
        if (m != null && (m.index == slotIndex || time >= finaleStart())) return 1.0F;
        return speed;
    }

    public static boolean isMember(Entity entity) {
        return running && entity instanceof Player && BY_ID.containsKey(entity.getUUID());
    }

    /** Animasi Player Animator yang harus diputar pemain ini sekarang, atau {@code null}. */
    static String animation(Player player) {
        if (!running) return null;
        Member m = BY_ID.get(player.getUUID());
        if (m == null) return null;
        long fin = finaleStart();
        if (time >= fin + DIVE) return "drop_selam";
        if (time >= fin) return "drop_salvo";
        if (time >= DropTiming.INTRO_START + slot() * m.index) return m.animation;
        // Sebelum gilirannya: tiap anggota jatuh dengan gayanya sendiri.
        return IDLES[m.index % IDLES.length];
    }

    /** Tick sejak animasi {@link #animation} seharusnya mulai (untuk menyusul kalau terlambat). */
    static int animationTick(Player player) {
        Member m = BY_ID.get(player.getUUID());
        if (m == null) return 0;
        long fin = finaleStart();
        long since = time >= fin + DIVE ? time - fin - DIVE
                : time >= fin ? time - fin
                : time >= DropTiming.INTRO_START + slot() * m.index ? time - DropTiming.INTRO_START - slot() * m.index : time;
        return (int) Math.min(4L, since / 50L);
    }

    /** Geser gambar pemain ini dari posisi aslinya (di panggung) ke posisinya di udara. */
    public static Vec3 renderOffset(Entity entity, float partialTick) {
        Member m = running ? BY_ID.get(entity.getUUID()) : null;
        if (m == null) return null;
        return origin().add(m.rel).subtract(entity.getPosition(partialTick));
    }

    /**
     * Saat perkenalan hanya anggota yang sedang mendapat giliran yang tampil;
     * yang lain disembunyikan (mereka sudah lewat atau belum datang).
     */
    public static boolean hiddenInCutscene(Entity entity) {
        Member m = running && ownsCamera() ? BY_ID.get(entity.getUUID()) : null;
        if (m == null) return false;
        if (time >= DropTiming.DROP + 1300L && time < DropTiming.INTRO_START) return true;
        return time >= DropTiming.INTRO_START && time < finaleStart() && m.index != slotIndex;
    }

    /** Arah badan yang digambar untuk pemain ini, atau NaN. */
    public static float bodyYaw(Entity entity) {
        Member m = running ? BY_ID.get(entity.getUUID()) : null;
        return m == null ? Float.NaN : m.yaw;
    }

    // ===================================================== tiap frame

    /** Dipanggil tiap frame sebelum dunia digambar. */
    static void frame() {
        if (!running) return;
        Minecraft client = Minecraft.getInstance();
        long nowNanos = System.nanoTime();
        double dt = Math.min(0.1D, (nowNanos - lastNanos) / 1.0E9D);
        lastNanos = nowNanos;
        time = (nowNanos - startNanos) / 1_000_000L;
        if (client.level != level) {
            // Sudah dipindahkan server: intro Level 0 mengambil alih (host langsung main sebagai penonton).
            if (levelChangedAt < 0L) levelChangedAt = time;
            if (IntroCutscene.isRunning() || time - levelChangedAt > 2000L) stop();
            return;
        }
        if (time > total() + WAIT_TELEPORT) {
            stop();
            return;
        }
        speed = speedAt(time);
        // Waktu adegan dihitung dari waktu nyata langkah 5 ms (bukan per frame),
        // jadi tetap tepat walau FPS turun drastis.
        while (sceneClock + 5L <= time) {
            scene += speedAt(sceneClock) * 0.005D;
            sceneClock += 5L;
        }
        int index = currentIndex();
        if (index != slotIndex) {
            slotIndex = index;
            slotScene = scene;
        }
        // Efek meluruh dalam waktu nyata.
        float decay = (float) dt;
        trauma = Math.max(0.0F, trauma - 2.2F * decay);
        glitch = Math.max(0.0F, glitch - 4.0F * decay);
        distortion = Math.max(0.0F, distortion - 5.0F * decay);
        holdPlayer(client.player);
        updateMembers();
        updateCamera();
        events();
        runScheduled();
        quakeDust(dt);
    }

    static void clientTick() {
        if (!running) return;
        holdPlayer(Minecraft.getInstance().player);
        if (wind != null) wind.target(windVolume(), 0.55F + 0.45F * speed);
    }

    /** Pemain sendiri tidak ikut jatuh betulan (lantai klien bolong): ditahan di tempatnya. */
    private static void holdPlayer(LocalPlayer player) {
        if (player == null || anchor == null || player.level() != level) return;
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        if (player.position().distanceToSqr(anchor) > 1.0E-4D) player.setPos(anchor);
    }

    /** Kecepatan waktu: bullet time di tengah tiap giliran dan di bingkai bersama. */
    private static float speedAt(long t) {
        // Hitstop: waktu nyaris membeku sesaat pada puncak aksi.
        if (t < hitstopUntil) return 0.06F;
        long first = DropTiming.INTRO_START;
        long fin = finaleStart();
        if (t >= first && t < fin) {
            // Sama persis dengan jadwal yang dipanggang ke animasi aksi.
            return DropTiming.slotSpeed((t - first) % slot());
        }
        if (t >= fin && t < fin + DIVE) {
            long u = t - fin;
            if (u < WHIP) return 1.0F;
            if (u < WHIP + 300L) return Mth.lerp(smooth((u - WHIP) / 300.0F), 1.0F, 0.5F);
            if (u < DIVE - 400L) return 0.5F;
            return Mth.lerp(smooth((u - (DIVE - 400L)) / 400.0F), 0.5F, 1.0F);
        }
        return 1.0F;
    }

    private static double dustBudget;

    /** Debu dan serpihan plafon berjatuhan selama gempa, makin deras menjelang runtuh. */
    private static void quakeDust(double dt) {
        if (time < DropTiming.TREMOR || time > DropTiming.DROP + 400L) return;
        float k = Mth.clamp((time - DropTiming.TREMOR) / (float) (DropTiming.BREAK - DropTiming.TREMOR), 0.0F, 1.0F);
        dustBudget += dt * (12.0D + 70.0D * k);
        java.util.Random random = new java.util.Random();
        while (dustBudget >= 1.0D) {
            dustBudget -= 1.0D;
            Vec3 at = origin0.add(right.scale((random.nextDouble() - 0.5D) * 8.0D))
                    .add(forward.scale((random.nextDouble() - 0.5D) * 5.0D)).add(0.0D, 3.6D, 0.0D);
            DropFx.dust(at);
        }
    }

    /** Jarak jatuh bebas setelah {@code x} detik (waktu adegan). */
    private static double fallDistance(double x) {
        if (x <= 0.0D) return 0.0D;
        double reach = TERMINAL / GRAVITY;
        if (x < reach) return 0.5D * GRAVITY * x * x;
        return 0.5D * GRAVITY * reach * reach + TERMINAL * (x - reach);
    }

    /** Kecepatan jatuh saat ini (blok/detik waktu adegan). */
    static double fallSpeed() {
        double x = scene - DropTiming.DROP / 1000.0D;
        return x <= 0.0D ? 0.0D : Math.min(TERMINAL, GRAVITY * x);
    }

    static double scene() {
        return scene;
    }

    /** Titik jatuh bersama sekarang (dunia). */
    static Vec3 origin() {
        return origin0.subtract(0.0D, fallDistance(scene - DropTiming.DROP / 1000.0D), 0.0D);
    }

    static Vec3 origin0() {
        return origin0;
    }

    private static void updateMembers() {
        long fin = finaleStart();
        long diveAt = fin + DIVE;
        double dive = time > diveAt ? 0.5D * 46.0D * Math.pow((time - diveAt) / 1000.0D, 2.0D) : 0.0D;
        // Bagian akhir: semua masuk bingkai dari atas dalam formasi V menghadap kamera.
        float enter = time >= fin ? ease((time - fin) / 1100.0F) : 0.0F;
        float faceCamera = yawOf(forward);
        double since = (scene - slotScene) * 20.0D;
        for (Member m : members) {
            if (time < DropTiming.INTRO_START) {
                // Jatuh bersama menembus lubang.
                m.rel = m.start;
                m.yaw = m.startYaw;
            } else if (time < fin) {
                if (m.index != slotIndex) {
                    m.rel = new Vec3(0.0D, 40.0D, 0.0D);
                    continue;
                }
                // Giliran sendiri: jatuh masuk bingkai dari atas, melambat di tengah, lalu jatuh keluar ke bawah.
                DropMoves.Move f = m.move();
                float e = easeInOut((float) (since / 34.0D));
                Vec3 sway = new Vec3(0.05D * Math.sin(scene * 0.8D + m.seed), 0.0D, 0.05D * Math.cos(scene * 0.6D + m.seed));
                m.rel = new Vec3(0.0D, descent(since), 0.0D).add(sway);
                // Kamera diam; karakternya yang diputar supaya terlihat dari sisi terbaiknya.
                m.yaw = faceCamera - Mth.lerp(e, (float) f.az(), (float) f.azEnd());
            } else {
                double stagger = Math.max(0.0D, dive - 1.2D * (m.index % 3));
                m.rel = m.line.add(0.0D, 9.0D * (1.0D - enter), 0.0D).subtract(0.0D, stagger, 0.0D);
                m.yaw = faceCamera + 6.0F * (float) Math.sin(scene * 0.5D + m.seed);
            }
        }
    }

    /**
     * Tinggi anggota relatif ke titik sorot selama gilirannya ({@code s} tick
     * waktu adegan): masuk dari atas dan mengerem (gerak lambat mulai), nyaris
     * diam selama aksi, lalu makin cepat jatuh keluar bingkai ke bawah.
     */
    private static double descent(double s) {
        if (s < 14.0D) {
            double u = s / 14.0D;
            return 7.5D * Math.pow(1.0D - u, 3.0D);
        }
        if (s < 24.0D) return -0.35D * (s - 14.0D) / 10.0D;
        double u = (s - 24.0D) / 11.0D;
        return -0.35D - 11.0D * u * u;
    }

    private static Vec3 groupCenter() {
        Vec3 sum = Vec3.ZERO;
        for (Member m : members) sum = sum.add(m.rel);
        return origin().add(sum.scale(1.0D / Math.max(1, members.size())));
    }

    // ========================================================= kamera

    private static void updateCamera() {
        Shot shot = shotAt(time);
        Vec3 pos = shot.pos;
        Vec3 target = shot.target;
        float roll = shot.roll;
        // Kamera genggam: selalu sedikit bergoyang; berguncang keras saat lantai bergemuruh dan runtuh.
        double rumble = time < DropTiming.TREMOR ? 0.0D
                : time < DropTiming.BREAK ? 0.012D + 0.06D * smooth((time - DropTiming.TREMOR) / (float) (DropTiming.BREAK - DropTiming.TREMOR))
                : time < DropTiming.DROP + 600L ? 0.1D * Math.exp(-(time - DropTiming.BREAK) / 350.0D) : 0.0D;
        double hand = 0.018D + 0.01D * (speed > 0.9F ? 1.0D : 0.0D) + rumble;
        double s = time / 1000.0D;
        pos = pos.add(hand * noise(s, 1.3D), hand * noise(s, 2.1D), hand * noise(s, 3.7D));
        roll += (float) (0.5D * noise(s * 0.4D, 5.0D) + rumble * 30.0D * noise(s * 3.0D, 6.0D));
        // Guncangan dari tembakan dan hantaman (trauma kuadrat, noise cepat).
        double shake = trauma * trauma;
        double fast = scene * 25.0D + s * 6.0D;
        pos = pos.add(0.15D * shake * noise(fast, 7.1D), 0.15D * shake * noise(fast, 8.3D), 0.15D * shake * noise(fast, 9.7D));
        roll += (float) (6.0D * shake * noise(fast, 10.9D));
        Vec3 dir = target.subtract(pos);
        if (dir.horizontalDistanceSqr() < 1.0E-4D) dir = dir.add(forward.scale(0.02D));
        camPos = pos;
        // Yaw dibuat bersambung dengan frame sebelumnya: atan2 melompat 360 derajat di
        // batas -180/180, dan penghalus kamera mod akan memutar kamera sekeliling.
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
        camYaw = camYaw + Mth.wrapDegrees(yaw - camYaw);
        camPitch = (float) (-(Mth.atan2(dir.y, dir.horizontalDistance()) * Mth.RAD_TO_DEG));
        camRoll = roll;
        fov = shot.fov;
        if (fovKickAt >= 0L) fov -= 6.0F * (float) Math.exp(-(time - fovKickAt) / 120.0D);
        updateLights(shot);
    }

    private static void addLights() {
        removeLights();
        keyLight = new PointLight();
        keyLight.setColor(1.0F, 0.9F, 0.72F).setRadius(14.0F).setBrightness(0.0F);
        rimLight = new PointLight();
        rimLight.setColor(1.0F, 0.9F, 0.78F).setRadius(5.0F).setBrightness(0.0F);
        holeLight = new PointLight();
        holeLight.setColor(1.0F, 0.86F, 0.42F).setRadius(16.0F).setBrightness(0.0F);
        LightTicks.addLight(keyLight);
        LightTicks.addLight(rimLight);
        LightTicks.addLight(holeLight);
    }

    private static void removeLights() {
        for (PointLight light : new PointLight[]{keyLight, rimLight, holeLight}) {
            if (light != null) LightTicks.removeLight(light);
        }
        keyLight = rimLight = holeLight = null;
    }

    private static void updateLights(Shot shot) {
        if (keyLight == null) return;
        // Menyala begitu lampu plafon meledak (adegan tidak pernah gelap total).
        float on = smooth((time - DropTiming.BREAK) / 300.0F);
        float off = 1.0F - smooth((time - (total() - DropTiming.BLACK_TAIL - 500L)) / 500.0F);
        float k = on * off;
        Vec3 aim = shot.target.subtract(shot.pos).normalize();
        // Key light di antara kamera dan subjek, sedikit di atas (cahaya utama dari arah kamera).
        Vec3 key = shot.pos.lerp(subject, 0.55D).add(0.0D, 1.3D, 0.0D);
        keyLight.setPosition(new Vector3d(key.x, key.y, key.z)).setBrightness(2.5F * k);
        Vec3 rim = subject.add(aim.scale(1.8D)).add(0.0D, 1.4D, 0.0D);
        rimLight.setPosition(new Vector3d(rim.x, rim.y, rim.z)).setBrightness(1.6F * k);
        Vec3 hole = origin().add(0.0D, 6.0D, 0.0D);
        holeLight.setPosition(new Vector3d(hole.x, hole.y, hole.z)).setBrightness(1.4F * k);
    }

    private static double noise(double t, double seed) {
        return 0.6D * Math.sin(t * 1.7D + seed) + 0.3D * Math.sin(t * 3.1D + seed * 2.0D) + 0.1D * Math.sin(t * 7.3D + seed * 3.0D);
    }

    private static Shot shotAt(long t) {
        long brk = DropTiming.BREAK;
        long drop = DropTiming.DROP;
        // Lantai runtuh: kamera lobby diam, hanya mendorong pelan dan menunduk sedikit
        // supaya retakan dan pecahan lantai terlihat jelas.
        if (t < drop) return lobbyPush(smooth((t - DropTiming.TREMOR) / (float) (drop - DropTiming.TREMOR)));
        if (t < drop + 500L) return blend(lobbyPush(1.0F), edge(), easeInOut((t - drop) / 500.0F), 0.0F);
        if (t < drop + 1300L) return edge();
        if (t < DropTiming.INTRO_START) {
            return blend(edge(), slotShot(members.get(0).move(), 0.0F),
                    easeInOut((t - drop - 1300L) / (float) (DropTiming.INTRO_START - drop - 1300L)), 0.0F);
        }
        long fin = finaleStart();
        if (t < fin) {
            int k = (int) ((t - DropTiming.INTRO_START) / slot());
            long u = (t - DropTiming.INTRO_START) % slot();
            Member m = members.get(k);
            float hero = k == slotIndex ? Mth.clamp((float) ((scene - slotScene) * 20.0D / 34.0D), 0.0F, 1.0F) : 0.0F;
            Shot to = slotShot(m.move(), hero);
            subject = origin().add(m.rel).add(0.0D, m.move().height(), 0.0D);
            if (k == 0 || u >= 600L) return to;
            return blend(slotShot(members.get(k - 1).move(), 1.0F), to, easeInOut(u / 600.0F), 0.0F);
        }
        long u = t - fin;
        if (u < 700L) return blend(slotShot(members.get(members.size() - 1).move(), 1.0F), group(u), easeInOut(u / 700.0F), 0.0F);
        if (u < DIVE) return group(u);
        if (u < DIVE + 650L) return blend(group(u), abyss(u), easeInOut((u - DIVE) / 650.0F), 0.0F);
        return abyss(u);
    }

    /** Kamera lobby, makin maju dan menunduk ke lantai ({@code w} 0..1). */
    private static Shot lobbyPush(float w) {
        Vec3 toward = origin0.subtract(lobbyShot.pos).multiply(1.0D, 0.0D, 1.0D).normalize();
        Vec3 pos = lobbyShot.pos.add(toward.scale(0.6D * w)).add(0.0D, 0.25D * w, 0.0D);
        Vec3 target = lobbyShot.target.lerp(origin0.add(0.0D, 0.4D, 0.0D), 0.5D * w);
        subject = groupCenter().add(0.0D, 0.9D, 0.0D);
        return new Shot(pos, target, 0.0F, baseFov);
    }

    /** Di bibir lubang menatap ke bawah mengikuti yang jatuh. */
    private static Shot edge() {
        subject = groupCenter().add(0.0D, 0.9D, 0.0D);
        Vec3 pos = origin0.add(forward.scale(1.7D)).add(0.0D, 2.3D, 0.0D);
        return new Shot(pos, groupCenter().add(0.0D, 0.6D, 0.0D), 0.0F, baseFov + 12.0F);
    }

    /**
     * Bingkai perkenalan: kamera tetap di depan titik sorot (tidak mengejar
     * siapa pun); hanya jarak, ketinggian, dan kemiringan yang sedikit
     * menyesuaikan gerakan. Karakter di sepertiga kanan layar, kartu nama di kiri.
     */
    private static Shot slotShot(DropMoves.Move f, float hero) {
        float e = easeInOut(hero);
        Vec3 center = origin().add(0.0D, f.height(), 0.0D);
        double d = Mth.lerp(e, f.dist(), f.distEnd());
        double el = Math.toRadians(Mth.lerp(e, f.el(), f.elEnd()));
        Vec3 pos = center.add(forward.scale(d * Math.cos(el))).add(0.0D, d * Math.sin(el), 0.0D);
        Vec3 dir = center.subtract(pos);
        Vec3 camRight = new Vec3(-dir.z, 0.0D, dir.x).normalize();
        Vec3 target = center.subtract(camRight.scale(0.22D * d));
        return new Shot(pos, target, Mth.lerp(e, f.roll(), f.rollEnd()), baseFov - 6.0F + 3.0F * hero);
    }

    /**
     * Semua anggota dalam satu bingkai: formasi V jatuh ke arah kamera yang
     * sedikit di bawah mereka (sudut pahlawan), kamera mendorong pelan.
     */
    private static Shot group(long u) {
        Vec3 o = origin();
        subject = o.add(0.0D, 0.9D, 0.0D);
        float push = smooth(u / (float) DIVE);
        double d = lineDistance * (1.12D - 0.12D * push);
        // Bidikan di atas kepala: anggota turun ke tengah-bawah bingkai, tidak tertutup judul BACKROOM WAR.
        Vec3 pos = o.add(forward.scale(d)).add(0.0D, 0.1D, 0.0D);
        return new Shot(pos, o.add(0.0D, 1.6D, 0.0D), -2.0F + 3.0F * push, baseFov - 2.0F);
    }

    /** Menatap jurang: anggota menyelam melewati kamera ke kegelapan. */
    private static Shot abyss(long u) {
        Shot g = group(u);
        return new Shot(g.pos, g.pos.add(forward.scale(-1.2D)).add(0.0D, -30.0D, 0.0D), g.roll + 8.0F, baseFov + 22.0F);
    }

    /**
     * Baurkan dua bidikan. Posisi diputar mengelilingi titik jatuh (koordinat
     * silinder), jadi sambaran dari satu anggota ke anggota lain melengkung di
     * luar formasi, tidak menembus orang. {@code whipRoll}: kemiringan
     * tambahan di tengah sambaran.
     */
    private static Shot blend(Shot a, Shot b, float w, float whipRoll) {
        Vec3 o = origin();
        Vec3 ra = a.pos.subtract(o);
        Vec3 rb = b.pos.subtract(o);
        double angA = Math.atan2(ra.z, ra.x);
        double angB = Math.atan2(rb.z, rb.x);
        double dAng = Mth.wrapDegrees(Math.toDegrees(angB - angA));
        double ang = angA + Math.toRadians(dAng) * w;
        double radius = Mth.lerp(w, ra.horizontalDistance(), rb.horizontalDistance());
        double y = Mth.lerp(w, ra.y, rb.y);
        Vec3 pos = o.add(Math.cos(ang) * radius, y, Math.sin(ang) * radius);
        Vec3 target = a.target.lerp(b.target, w);
        float bump = (float) Math.sin(Math.PI * w);
        float roll = Mth.lerp(w, a.roll, b.roll) + whipRoll * bump;
        float f = Mth.lerp(w, a.fov, b.fov) + (whipRoll != 0.0F ? 14.0F * bump : 0.0F);
        return new Shot(pos, target, roll, f);
    }

    static float smooth(float x) {
        x = Mth.clamp(x, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    static float ease(float x) {
        x = Mth.clamp(x, 0.0F, 1.0F);
        return 1.0F - (1.0F - x) * (1.0F - x) * (1.0F - x);
    }

    static float easeInOut(float x) {
        x = Mth.clamp(x, 0.0F, 1.0F);
        return x < 0.5F ? 4.0F * x * x * x : 1.0F - (float) Math.pow(-2.0F * x + 2.0F, 3.0D) / 2.0F;
    }

    /** Sambaran: lambat di awal (antisipasi), sangat cepat di tengah, mengerem tajam di akhir. */
    private static float whipEase(float x) {
        x = Mth.clamp(x, 0.0F, 1.0F);
        return x < 0.5F ? 16.0F * x * x * x * x * x : 1.0F - (float) Math.pow(-2.0F * x + 2.0F, 5.0D) / 2.0F;
    }

    /** Pasang kamera cutscene; {@code false} kalau sedang tidak memegang kamera. */
    static boolean applyCamera(Camera camera) {
        if (!ownsCamera()) return false;
        CameraAccessor accessor = (CameraAccessor) camera;
        accessor.backrooms$setPosition(camPos);
        accessor.backrooms$setRotation(camYaw, camPitch);
        accessor.backrooms$setDetached(true);
        return true;
    }

    /**
     * Kekuatan motion blur shader VHS selama cutscene: tipis saja. Shader
     * menghitung blur dari gerak kamera, padahal anggota ikut jatuh bersama
     * kamera, dan satu frame yang lambat (lantai dilubangi) membuatnya
     * mencoreng seluruh layar.
     */
    public static float motionBlur(float configured) {
        return ownsCamera() ? configured * 0.1F : configured;
    }

    /** Denyut glitch shader VHS (RGB split, distorsi garis) 0..1. */
    public static float glitch() {
        return running ? Math.min(1.0F, glitch) : 0.0F;
    }

    /** Tambahan distorsi lensa shader VHS (gelombang kejut). */
    public static float distortionPulse() {
        return running ? distortion : 0.0F;
    }

    /** Impact frame sedang tampil (layar dibalik, ~70 ms). */
    static boolean impactFrame() {
        return running && time < impactUntil;
    }

    /** Sedang hitstop / bullet time (untuk grade warna overlay). */
    static float slowness() {
        return running ? Mth.clamp((1.0F - speed) / 0.7F, 0.0F, 1.0F) : 0.0F;
    }

    /** Anggota yang sedang disorot; di bagian akhir anggota pertama mewakili salvo bersama. */
    private static boolean isHero(Member m) {
        return m.index == slotIndex || slotIndex < 0 && time >= finaleStart() && m.index == 0;
    }

    private static void impact(float shake, float glitchAmount) {
        impactUntil = time + 70L;
        trauma = Math.min(1.0F, trauma + shake);
        glitch = Math.max(glitch, glitchAmount);
        fovKickAt = time;
    }

    /** Pistol tangan kiri pemain ini, atau {@code null}. */
    public static String offhandGun(Player player) {
        Member m = running ? BY_ID.get(player.getUUID()) : null;
        if (m == null || !m.move().akimbo()) return null;
        return time >= DropTiming.INTRO_START + slot() * m.index ? PISTOL : null;
    }

    /** Satu tembakan anggota (dari {@link LobbyFx#fire}): guncangan, dentum lambat, bunyi selongsong. */
    static void onShot(Player player, boolean left) {
        Member m = running ? BY_ID.get(player.getUUID()) : null;
        if (m == null) return;
        boolean hero = isHero(m);
        trauma = Math.min(1.0F, trauma + (hero ? 0.07F : 0.0F));
        if (!hero) return;
        if (speed < 0.6F) playUi(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 0.5F, 0.35F);
        schedule(60L, () -> playUi(SoundEvents.TRIDENT_THROW, 0.6F, 0.3F));
        String casing = left ? CASING_SOUND : m.weapon == LobbyWeapon.M870 ? SHELL_SOUND
                : m.weapon == LobbyWeapon.AWP ? SNIPER_CASING_SOUND : CASING_SOUND;
        schedule(350L + (long) (Math.random() * 150.0D), () -> LobbyFx.taczSound(player, casing, 0.5F));
    }

    /**
     * Kejadian dari animasi gerakan (lihat {@code tools/drop_moves_*.py}):
     * hitstop, impact frame, efek (fx_*), dan bunyi gerakan.
     */
    static void animEvent(Player player, String kind) {
        Member m = running ? BY_ID.get(player.getUUID()) : null;
        if (m == null) return;
        boolean hero = isHero(m);
        Vec3 center = origin().add(m.rel).add(0.0D, 0.9D, 0.0D);
        if (kind.startsWith("hitstop_")) {
            if (hero) {
                int ticks = Integer.parseInt(kind.substring(8));
                hitstopUntil = time + ticks * 70L;
                playUi(SoundEvents.WARDEN_ATTACK_IMPACT, 0.6F, 0.7F);
            }
            return;
        }
        switch (kind) {
            case "impact" -> {
                if (hero) impact(0.35F, 0.6F);
            }
            case "fx_shockwave" -> {
                DropFx.shockwave(center, camPos.subtract(center), 1.0F);
                distortion = Math.max(distortion, 1.2F);
                if (hero) playUi(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 0.5F, 0.6F);
            }
            case "fx_glint", "glint" -> {
                DropFx.glint(center.add(0.0D, 0.55D, 0.0D).add(camPos.subtract(center).normalize().scale(0.4D)));
                if (hero) playUi(SoundEvents.AMETHYST_BLOCK_CHIME, 1.6F, 1.0F);
            }
            case "fx_sparks" -> DropFx.sparks(center, 24, 255, 200, 110);
            case "fx_crack" -> {
                DropFx.sparks(center.subtract(0.0D, 0.8D, 0.0D), 30, 220, 240, 255);
                if (hero) playUi(SoundEvents.GLASS_BREAK, 0.5F, 0.8F);
            }
            case "fx_afterimage" -> DropFx.sparks(center, 6, 120, 220, 255);
            case "fx_smoke" -> DropFx.sparks(center.add(0.0D, 0.6D, 0.0D), 8, 160, 150, 140);
            case "shotL" -> LobbyFx.fire(player, true);
            case "pump" -> {
                LobbyFx.taczSound(player, "tacz:m870/m870_bolt_out", 0.9F);
                schedule(140L, () -> LobbyFx.taczSound(player, "tacz:m870/m870_bolt_in", 0.9F));
            }
            case "bolt" -> LobbyFx.taczSound(player, "tacz:m95/m95_boltback_shell", 0.9F);
            case "toss" -> {
                LobbyFx.taczSound(player, "tacz:mk23/mk23_inspect_toss", 0.9F);
                if (hero) playUi(SoundEvents.TRIDENT_THROW, 1.3F, 0.5F);
            }
            case "catch" -> {
                LobbyFx.taczSound(player, "tacz:deagle/deagle_inspect_slideclose", 1.0F);
                if (hero) playUi(SoundEvents.ARMOR_EQUIP_IRON, 1.4F, 0.8F);
            }
            case "twirl" -> LobbyFx.taczSound(player, "tacz:timeless50/timeless50_inspect_twirl", 0.9F);
            case "whoosh" -> {
                if (hero) playUi(SoundEvents.PLAYER_ATTACK_SWEEP, 0.6F, 0.6F);
            }
            case "slam" -> {
                DropFx.shockwave(center.subtract(0.0D, 0.85D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D), 1.3F);
                if (hero) {
                    impact(0.6F, 0.7F);
                    distortion = Math.max(distortion, 1.5F);
                    playUi(SoundEvents.WARDEN_ATTACK_IMPACT, 0.5F, 1.0F);
                    playUi(SoundEvents.GENERIC_EXPLODE, 0.6F, 0.5F);
                }
            }
            case "coin" -> {
                DropFx.glint(center.add(0.0D, 1.3D, 0.0D));
                if (hero) playUi(SoundEvents.AMETHYST_BLOCK_HIT, 2.0F, 1.0F);
            }
            case "magout", "magin", "end" -> LobbyFx.reloadSound(player, kind);
            default -> {
            }
        }
    }

    private static void schedule(long delayMs, Runnable action) {
        SCHEDULED.add(new Scheduled(time + delayMs, action));
    }

    private static void runScheduled() {
        if (SCHEDULED.isEmpty()) return;
        List<Scheduled> due = new ArrayList<>();
        SCHEDULED.removeIf(s -> {
            if (s.at > time) return false;
            due.add(s);
            return true;
        });
        for (Scheduled s : due) s.action.run();
    }

    public static float roll() {
        return ownsCamera() ? camRoll : 0.0F;
    }

    public static float fov() {
        return fov;
    }

    // ===================================================== suara, kejadian

    private static void events() {
        long last = lastEventTime;
        long t = time;
        lastEventTime = t;
        if (last < 0L) return;
        long brk = DropTiming.BREAK;
        long drop = DropTiming.DROP;
        // Antisipasi: dengung naik tepat sampai lantai pecah, detak jantung, lampu berkedip.
        // Gempa: hentakan pertama, gemuruh yang makin kuat, retakan, debu, lampu berkedip;
        // dengung naik tepat sampai lantai pecah.
        long tremor = DropTiming.TREMOR;
        if (crossed(last, t, tremor)) {
            trauma = Math.min(1.0F, trauma + 0.35F);
            playUi(SoundEvents.WARDEN_DIG, 0.5F, 0.9F);
            playUi(SoundEvents.DEEPSLATE_BREAK, 0.6F, 0.7F);
            playUi(SoundEvents.WARDEN_HEARTBEAT, 0.8F, 0.6F);
        }
        for (long at = tremor + 450L; at < brk; at += 420L) {
            if (crossed(last, t, at)) {
                float k = (at - tremor) / (float) (brk - tremor);
                trauma = Math.min(1.0F, trauma + 0.08F + 0.15F * k);
                playUi(SoundEvents.WARDEN_DIG, 0.45F + 0.1F * k, 0.5F + 0.4F * k);
            }
        }
        for (long at : new long[]{tremor + 300L, tremor + 700L, tremor + 1050L, tremor + 1350L, tremor + 1600L, tremor + 1800L}) {
            if (crossed(last, t, at)) {
                playUi(SoundEvents.DEEPSLATE_BREAK, 0.6F + (at % 7) * 0.05F, 0.6F);
                playUi(SoundEvents.GRAVEL_FALL, 0.7F, 0.4F);
            }
        }
        for (long at : new long[]{tremor + 200L, tremor + 650L, tremor + 1150L, tremor + 1500L, tremor + 1750L, tremor + 1900L}) {
            if (crossed(last, t, at) && ModSounds.available("light_blink")) {
                playUi(ModSounds.event("light_blink"), 0.85F + (at % 5) * 0.06F, 0.45F);
            }
        }
        if (crossed(last, t, tremor + 900L)) playUi(SoundEvents.WARDEN_HEARTBEAT, 0.75F, 0.7F);
        if (crossed(last, t, brk - 1420L)) playLong(SoundEvents.WARDEN_SONIC_CHARGE, 1.15F, 0.6F);
        if (crossed(last, t, brk - 250L)) playUi(SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, 0.5F, 0.7F);
        if (crossed(last, t, brk)) {
            DropScenery.breakFloor();
            trauma = Math.min(1.0F, trauma + 0.3F);
            playLong(SoundEvents.WARDEN_SONIC_BOOM, 0.5F, 0.9F);
            playUi(SoundEvents.GENERIC_EXPLODE, 0.5F, 0.6F);
            playUi(SoundEvents.DEEPSLATE_BREAK, 0.5F, 1.0F);
            playUi(SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 0.55F, 0.9F);
            playUi(SoundEvents.ANVIL_LAND, 0.5F, 0.3F);
            if (ModSounds.available("lights_out")) playUi(ModSounds.event("lights_out"), 1.0F, 0.8F);
            schedule(60L, () -> playUi(SoundEvents.DEEPSLATE_BREAK, 0.7F, 0.8F));
            schedule(80L, () -> {
                playUi(SoundEvents.FLINTANDSTEEL_USE, 1.6F, 0.4F);
                playUi(SoundEvents.FIRE_EXTINGUISH, 1.8F, 0.25F);
            });
            for (int i = 0; i < 4; i++) {
                long d = 150L + i * 120L;
                float pitch = 0.6F + i * 0.1F;
                schedule(d, () -> playUi(SoundEvents.GRAVEL_BREAK, pitch, 0.6F));
                schedule(d + 50L, () -> playUi(SoundEvents.POINTED_DRIPSTONE_BREAK, 0.8F + pitch * 0.4F, 0.4F));
            }
            schedule(200L, () -> playUi(SoundEvents.DECORATED_POT_SHATTER, 0.7F, 0.5F));
        }
        if (crossed(last, t, brk - 100L)) addLights();
        if (crossed(last, t, drop)) {
            // "BRAAAM" ala Inception: terompet raid direndahkan, dua kali supaya lebih tebal.
            trauma = Math.min(1.0F, trauma + 0.4F);
            playLong(SoundEvents.RAID_HORN.value(), 0.5F, 1.0F);
            playLong(SoundEvents.RAID_HORN.value(), 0.5F, 1.0F);
            playLong(SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(7).value(), 0.5F, 0.8F);
            playUi(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.5F, 0.4F);
            if (ModSounds.available("falling")) playUi(ModSounds.event("falling"), 1.0F, 0.6F);
            wind = new WindSound();
            Minecraft.getInstance().getSoundManager().play(wind);
        }
        if (crossed(last, t, drop + 2600L)) cutLongSounds();
        if (crossed(last, t, drop + 1650L)) {
            playUi(SoundEvents.ENDER_DRAGON_FLAP, 0.7F, 0.6F);
            playUi(SoundEvents.PHANTOM_SWOOP, 1.2F, 0.5F);
        }
        long fin = finaleStart();
        long slot = slot();
        for (Member m : members) {
            long at = DropTiming.INTRO_START + slot * m.index;
            if (crossed(last, t, at + 40L)) {
                playUi(SoundEvents.TRIDENT_RIPTIDE_1, 1.25F + 0.05F * (m.index % 3), 0.6F);
                playUi(SoundEvents.ENDER_DRAGON_FLAP, 1.6F, 0.35F);
                playMod("intro_whoosh", 0.95F + 0.05F * (m.index % 3), 0.8F);
            }
            if (crossed(last, t, at + 120L)) playUi(SoundEvents.PLAYER_ATTACK_SWEEP, 0.6F, 0.6F);
            if (crossed(last, t, at + WHIP)) {
                // Waktu melambat: sub drop (bas turun, mengisi seluruh pose) + "whoom" turun.
                playMod("intro_subdrop", 1.0F, 1.0F);
                playUi(SoundEvents.BEACON_DEACTIVATE, 0.75F, 0.6F);
                nextHeartbeat = t + 200L;
            }
            if (crossed(last, t, at + WHIP + 40L)) {
                // Kartu nama dibanting: hantaman sinematik (bergantian tiap anggota supaya tidak berulang).
                trauma = Math.min(1.0F, trauma + 0.12F);
                playMod(HITS[m.index % HITS.length], 1.0F, 1.0F);
                playUi(SoundEvents.PLAYER_ATTACK_STRONG, 0.55F, 0.5F);
                if (ModSounds.available("glitch")) playUi(ModSounds.event("glitch"), 1.4F, 0.3F);
            }
            if (crossed(last, t, at + WHIP + 210L)) playUi(SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.8F, 0.25F);
            // Lapisan tegang yang membengkak pelan selama pose ditahan.
            if (crossed(last, t, at + WHIP + 250L)) playMod("intro_swell", 1.0F, 0.5F);
            // Senjata anggota ini "diperkenalkan": magasin masuk, lalu kokang.
            Player owner = level.getPlayerByUUID(m.id);
            if (owner != null) {
                if (crossed(last, t, at + WHIP + 300L)) LobbyFx.reloadSound(owner, "magin");
                if (crossed(last, t, at + WHIP + 700L)) LobbyFx.reloadSound(owner, "end");
            }
            if (crossed(last, t, at + slot - SPEEDUP)) playUi(SoundEvents.BEACON_ACTIVATE, 1.5F, 0.45F);
        }
        // Detak jantung selama bullet time.
        int index = currentIndex();
        if (index >= 0 && slotTime() > WHIP && slotTime() < slot - SPEEDUP && t >= nextHeartbeat && nextHeartbeat > 0L) {
            playUi(SoundEvents.WARDEN_HEARTBEAT, 0.7F, 0.8F);
            nextHeartbeat = t + 850L;
        }
        if (crossed(last, t, fin + 40L)) playUi(SoundEvents.TRIDENT_RIPTIDE_2, 1.0F, 0.7F);
        if (crossed(last, t, fin + WHIP + 40L)) {
            trauma = Math.min(1.0F, trauma + 0.25F);
            playUi(SoundEvents.PLAYER_ATTACK_STRONG, 0.5F, 1.0F);
            playMod("intro_hit_braam", 1.0F, 1.0F);
            playMod("intro_subdrop", 0.9F, 1.0F);
            playLong(SoundEvents.TRIDENT_THUNDER, 0.6F, 0.7F);
            playLong(SoundEvents.RAID_HORN.value(), 0.5F, 0.7F);
            playUi(SoundEvents.ANVIL_LAND, 0.6F, 0.35F);
        }
        long end = total() - DropTiming.BLACK_TAIL;
        if (crossed(last, t, end - 1630L)) playLong(SoundEvents.WARDEN_SONIC_CHARGE, 1.0F, 0.7F);
        if (crossed(last, t, fin + DIVE)) {
            playUi(SoundEvents.ENDER_DRAGON_FLAP, 0.5F, 0.8F);
            playUi(SoundEvents.PHANTOM_SWOOP, 0.7F, 0.7F);
        }
        if (crossed(last, t, end)) {
            // Potong semua, lalu satu dentum besar yang terbawa ke Level 0.
            cutLongSounds();
            if (wind != null) wind.end();
            impact(1.0F, 0.8F);
            playUi(SoundEvents.WARDEN_SONIC_BOOM, 0.5F, 1.0F);
            playUi(SoundEvents.GENERIC_EXPLODE, 0.5F, 0.6F);
            playUi(SoundEvents.TRIDENT_THUNDER, 0.5F, 0.5F);
        }
    }

    /** Suara panjang yang nanti bisa dipotong ({@link #cutLongSounds}). */
    private static void playLong(SoundEvent sound, float pitch, float volume) {
        SoundInstance instance = SimpleSoundInstance.forUI(sound, pitch, volume);
        LONG_SOUNDS.add(instance);
        Minecraft.getInstance().getSoundManager().play(instance);
    }

    private static void cutLongSounds() {
        for (SoundInstance instance : LONG_SOUNDS) Minecraft.getInstance().getSoundManager().stop(instance);
        LONG_SOUNDS.clear();
    }

    private static boolean crossed(long last, long now, long at) {
        return last < at && now >= at;
    }

    /** Hantaman kartu nama per anggota (sound/intro, CC0 dari freesound.org). */
    private static final String[] HITS = {"intro_hit_brass", "intro_hit_hard", "intro_hit_drama"};

    /** Sound mod (sounds.json) kalau berkasnya ada. */
    private static void playMod(String name, float pitch, float volume) {
        if (ModSounds.available(name)) playUi(ModSounds.event(name), pitch, volume);
    }

    private static void playUi(SoundEvent sound, float pitch, float volume) {
        if (sound == null) return;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    private static float windVolume() {
        if (time < DropTiming.DROP) return 0.0F;
        float in = smooth((time - DropTiming.DROP) / 600.0F);
        float out = 1.0F - smooth((time - (total() - DropTiming.BLACK_TAIL - 300L)) / 300.0F);
        // Gerak lambat: angin teredam, tapi tetap terdengar (tidak sepi).
        return 0.9F * in * out * (0.55F + 0.45F * speed);
    }

    /** Deru angin selama jatuh; volume dan nada mengikuti kecepatan waktu. */
    private static final class WindSound extends AbstractTickableSoundInstance {
        private float targetVolume;
        private float targetPitch = 1.0F;

        WindSound() {
            super(SoundEvents.ELYTRA_FLYING, SoundSource.MASTER, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01F;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        void target(float volume, float pitch) {
            this.targetVolume = volume;
            this.targetPitch = pitch;
        }

        void end() {
            stop();
        }

        @Override
        public void tick() {
            if (!running) {
                stop();
                return;
            }
            volume = Math.max(0.01F, volume + (targetVolume - volume) * 0.35F);
            pitch = pitch + (targetPitch - pitch) * 0.35F;
        }
    }

    // ======================================================= layar

    /** Tirai hitam: kedipan lampu saat gemuruh, dan layar hitam di akhir. */
    static float blackAlpha() {
        if (!running) return 0.0F;
        if (landed()) return 1.0F;
        long t = time;
        long end = total() - DropTiming.BLACK_TAIL;
        if (t >= end) return 1.0F;
        long fade = finaleStart() + DIVE + 450L;
        if (t > fade) return smooth((t - fade) / (float) (end - fade));
        return 0.0F;
    }

    static float yawOf(Vec3 dir) {
        return (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
    }
}
