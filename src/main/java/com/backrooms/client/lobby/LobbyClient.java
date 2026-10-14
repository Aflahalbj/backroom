package com.backrooms.client.lobby;

import com.backrooms.client.CameraEffect;
import com.backrooms.client.ClientConfig;
import com.backrooms.client.lobby.screen.CustomizeScreen;
import com.backrooms.client.lobby.screen.CutsceneScreen;
import com.backrooms.client.lobby.screen.JoinRoomScreen;
import com.backrooms.client.lobby.screen.LobbyScreen;
import com.backrooms.client.lobby.screen.LobbyPageScreen;
import com.backrooms.client.lobby.screen.RoomScreen;
import com.backrooms.lobby.LobbyPose;
import com.backrooms.lobby.LobbyWeapon;
import com.backrooms.lobby.LobbyStage;
import com.backrooms.lobby.RoomInfo;
import com.backrooms.mixin.CameraAccessor;
import com.backrooms.network.CutsceneS2C;
import com.backrooms.network.LobbyActionC2S;
import com.backrooms.network.LobbyStateS2C;
import com.backrooms.network.ModNetwork;
import com.backrooms.sound.ModSounds;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.List;
import java.util.UUID;

/**
 * Sisi klien lobby: keadaan dari server, layar mana yang terbuka, kamera
 * tetap di depan karakter, karakter yang menoleh ke kursor, dan cutscene
 * noclip + intro.
 *
 * <p>Selama di lobby atau cutscene selalu ada layar yang terbuka, jadi semua
 * input gerak tertahan. Layar vanilla yang dibuka dari lobby (menu pause,
 * chat) dibiarkan; begitu ditutup, layar lobby dibuka lagi.
 */
public final class LobbyClient {

    public enum Page { MAIN, JOIN, CUSTOMIZE, ROOM }

    /** Lama animasi noclip di klien (server memindahkan pemain setelah 60 tick). */
    private static final long NOCLIP_SINK_START = 450L;
    private static final long NOCLIP_SINK_END = 2600L;
    private static final double NOCLIP_DEPTH = 2.4D;

    // ------------------------------------------------- keadaan dari server
    private static boolean active;
    private static int background;
    private static int pose;
    private static int weapon;
    private static LobbyStage stage;
    private static int percent;
    private static RoomInfo room;
    private static List<RoomInfo.Summary> rooms = List.of();
    /** Naik setiap ada perubahan; layar membangun ulang tombolnya kalau berubah. */
    private static int version;

    // ------------------------------------------------------- keadaan UI
    private static Page page = Page.MAIN;
    private static Page customizeReturn = Page.MAIN;
    private static long enteredAt;
    private static long tapeChangeAt = -1L;
    /** Ganti latar: layar ditahan hitam sampai set barunya selesai dirender. */
    private static boolean revealPending;
    private static int revealWait;
    private static int revealReady;
    private static float dragYaw;
    private static long noclipStart = -1L;

    // ------------------------------------------------------------ kamera
    private static boolean cameraReady;
    private static Vec3 cameraPos = Vec3.ZERO;
    private static float cameraYaw;
    private static float cameraPitch;
    private static long lastFrame;

    private static LobbyMusic music;
    /** Sound event yang sedang diputar sebagai suara lobby. */
    private static String musicId;
    /** Posisi lagu di daftar putar mode Acak. */
    private static int playlistIndex;
    /** Tick sejak suara lobby terakhir dimulai (supaya tidak diulang tiap tick kalau gagal diputar). */
    private static int musicAge;
    private static boolean pushedSink;

    private LobbyClient() {}

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(LobbyClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(LobbyClient::onRenderTick);
        MinecraftForge.EVENT_BUS.addListener(LobbyClient::onRenderPlayerPre);
        MinecraftForge.EVENT_BUS.addListener(LobbyClient::onRenderPlayerPost);
        MinecraftForge.EVENT_BUS.addListener(LobbyClient::onRenderOverlay);
        MinecraftForge.EVENT_BUS.addListener(LobbyClient::onRenderHand);
        MinecraftForge.EVENT_BUS.addListener(LobbyClient::onScreenRender);
        MinecraftForge.EVENT_BUS.addListener(LobbyClient::onScreenInit);
        MinecraftForge.EVENT_BUS.addListener(LobbyClient::onPlaySound);
        MinecraftForge.EVENT_BUS.addListener(LobbyClient::onLoggingOut);
    }

    // ============================================================ paket

    public static void onState(LobbyStateS2C msg) {
        boolean wasActive = active;
        active = msg.active();
        background = msg.background();
        pose = msg.pose();
        weapon = msg.weapon();
        percent = msg.percent();
        LobbyStage newStage = msg.active() ? msg.stage() : null;
        if (newStage != null && !newStage.equals(stage)) {
            if (stage != null || wasActive) {
                tapeChangeAt = System.currentTimeMillis();
                revealPending = true;
                revealWait = 0;
                revealReady = 0;
            }
            cameraReady = false;
        }
        stage = newStage;
        if (active && !wasActive) {
            enteredAt = System.currentTimeMillis();
            page = room != null ? Page.ROOM : Page.MAIN;
            dragYaw = 0.0F;
            noclipStart = -1L;
            IntroCutscene.cancel();
        }
        version++;
    }

    public static void onRoom(RoomInfo info) {
        room = info;
        if (info != null && (page == Page.MAIN || page == Page.JOIN)) {
            page = Page.ROOM;
        } else if (info == null && (page == Page.ROOM || page == Page.CUSTOMIZE && customizeReturn == Page.ROOM)) {
            page = Page.MAIN;
            customizeReturn = Page.MAIN;
        }
        version++;
    }

    public static void onRoomList(List<RoomInfo.Summary> list) {
        rooms = list;
        version++;
    }

    public static void onCutscene(CutsceneS2C msg) {
        switch (msg.type()) {
            case NOCLIP -> {
                noclipStart = System.currentTimeMillis();
                // Aslinya suara glitch diputar saat mulai tercekik di dalam dinding.
                if (ModSounds.available("glitch")) {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.event("glitch"), 1.0F));
                }
            }
            case INTRO -> {
                noclipStart = -1L;
                IntroCutscene.start(msg.offsetX(), msg.offsetZ());
            }
        }
        version++;
    }

    public static void send(LobbyActionC2S.Action action) {
        ModNetwork.CHANNEL.sendToServer(new LobbyActionC2S(action));
    }

    public static void send(LobbyActionC2S.Action action, int value) {
        ModNetwork.CHANNEL.sendToServer(new LobbyActionC2S(action, value));
    }

    public static void kick(UUID member) {
        ModNetwork.CHANNEL.sendToServer(new LobbyActionC2S(LobbyActionC2S.Action.KICK, 0, member.toString()));
    }

    // ========================================================= getter

    public static boolean isActive() { return active; }
    public static boolean isReady() { return stage != null; }
    public static int percent() { return percent; }
    public static int background() { return background; }
    public static LobbyPose pose() { return LobbyPose.byId(pose); }
    public static LobbyWeapon weapon() { return LobbyWeapon.byId(weapon); }
    public static RoomInfo room() { return room; }
    public static List<RoomInfo.Summary> rooms() { return rooms; }
    public static int version() { return version; }
    public static long enteredAt() { return enteredAt; }
    public static long tapeChangeAt() { return tapeChangeAt; }

    /**
     * Gelapnya layar transisi ganti latar (0..1): hitam penuh sampai set yang
     * baru siap tampil, lalu memudar dalam 450 ms.
     */
    public static float transitionAlpha() {
        if (revealPending) return 1.0F;
        if (tapeChangeAt <= 0L) return 0.0F;
        long since = System.currentTimeMillis() - tapeChangeAt;
        return since < 450L ? 1.0F - since / 450.0F : 0.0F;
    }

    /** Set latar baru sudah dimuat dan dirender di sekitar panggung dan kamera? Kalau ya, mulai memudar. */
    private static void tickReveal(Minecraft client) {
        if (!revealPending) return;
        boolean ready = stage != null && client.level != null && cameraReady
                && client.level.hasChunkAt(stage.pos())
                && client.levelRenderer.isChunkCompiled(stage.pos())
                && client.levelRenderer.isChunkCompiled(BlockPos.containing(cameraPos));
        revealReady = ready ? revealReady + 1 : 0;
        // Tunggu beberapa tick setelah siap (lampu Veil ikut menyala), paling lama 5 detik.
        if (revealReady >= 4 || ++revealWait > 100) {
            revealPending = false;
            tapeChangeAt = System.currentTimeMillis();
        }
    }

    public static boolean isHost() {
        Minecraft client = Minecraft.getInstance();
        return room != null && client.player != null && room.host().equals(client.player.getUUID());
    }

    /** Untuk pratinjau animasi: arah badan karakter relatif ke kamera (derajat). */
    public static void setDragYaw(float yaw) {
        dragYaw = Mth.wrapDegrees(yaw);
    }

    public static void dragCharacter(double dx) {
        dragYaw = Mth.wrapDegrees(dragYaw - (float) dx * 1.5F);
    }

    /** Noclip atau intro sedang berjalan: input ditahan oleh {@link CutsceneScreen}. */
    public static boolean cutsceneActive() {
        return noclipStart >= 0L || IntroCutscene.isRunning();
    }

    /** Kemajuan noclip 0..1 (sebelum/ sesudah tenggelam), atau -1 kalau tidak sedang noclip. */
    public static float noclipProgress() {
        if (noclipStart < 0L) return -1.0F;
        long t = System.currentTimeMillis() - noclipStart;
        return Mth.clamp((float) (t - NOCLIP_SINK_START) / (float) (NOCLIP_SINK_END - NOCLIP_SINK_START), 0.0F, 1.0F);
    }

    /** Milidetik sejak noclip dimulai, atau -1. */
    public static long noclipTime() {
        return noclipStart < 0L ? -1L : System.currentTimeMillis() - noclipStart;
    }

    // ========================================================== layar

    public static void openPage(Page target) {
        if (target == Page.CUSTOMIZE && page != Page.CUSTOMIZE) {
            customizeReturn = page == Page.ROOM ? Page.ROOM : Page.MAIN;
        }
        if (target == Page.JOIN) send(LobbyActionC2S.Action.REQUEST_LIST);
        page = target;
        Minecraft.getInstance().setScreen(createPage());
    }

    /** Kembali dari Customize/Gabung ke layar sebelumnya. */
    public static void back() {
        openPage(page == Page.CUSTOMIZE ? customizeReturn : room != null ? Page.ROOM : Page.MAIN);
    }

    /** Halaman yang seharusnya tampil: selama ikut room, selalu layar room (kecuali Customize). */
    private static Page effectivePage() {
        return room != null && page != Page.CUSTOMIZE ? Page.ROOM : page;
    }

    private static Screen createPage() {
        return switch (effectivePage()) {
            case MAIN -> new LobbyScreen();
            case JOIN -> new JoinRoomScreen();
            case CUSTOMIZE -> new CustomizeScreen();
            case ROOM -> new RoomScreen();
        };
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        Screen screen = client.screen;
        if (cutsceneActive()) {
            if (!(screen instanceof CutsceneScreen) && !(screen instanceof ReceivingLevelScreen)) {
                client.setScreen(new CutsceneScreen());
            }
        } else if (active) {
            if (screen == null || screen instanceof CutsceneScreen
                    || screen instanceof LobbyPageScreen osd && osd.page() != effectivePage()) {
                client.setScreen(createPage());
            }
        } else if (screen instanceof LobbyPageScreen || screen instanceof CutsceneScreen) {
            client.setScreen(null);
        }
        tickMusic(client);
        tickReveal(client);
        LobbyAnimator.tick(client);
    }

    /** Senjata pemain ini di lobby (sendiri atau anggota room). */
    public static LobbyWeapon weaponOf(Player player) {
        Minecraft client = Minecraft.getInstance();
        if (player == client.player) return weapon();
        if (room != null) {
            for (RoomInfo.Member member : room.members()) {
                if (member.id().equals(player.getUUID())) return LobbyWeapon.byId(member.weapon());
            }
        }
        return LobbyWeapon.NONE;
    }

    // ===================================================== kamera, pose

    private static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        IntroCutscene.tick();
        Minecraft client = Minecraft.getInstance();
        if (active && stage != null && client.player != null) {
            updateCamera();
            lookAtCursor(client, client.player);
        }
    }

    /** Dipanggil {@code CameraMixin} setelah kamera vanilla disiapkan. */
    public static boolean applyCamera(Camera camera) {
        if (IntroCutscene.applyCamera(camera)) return true;
        if (!active || stage == null || !cameraReady) return false;
        CameraAccessor accessor = (CameraAccessor) camera;
        accessor.backrooms$setPosition(cameraPos);
        accessor.backrooms$setRotation(cameraYaw, cameraPitch);
        accessor.backrooms$setDetached(true);
        return true;
    }

    /** Kamera dikendalikan lobby atau cutscene (bukan pemain). */
    public static boolean overridesCamera() {
        return active && stage != null || IntroCutscene.isPlaying();
    }

    private static int memberCount() {
        return room == null ? 1 : Math.max(1, room.members().size());
    }

    private static void updateCamera() {
        int count = memberCount();
        double distance = Math.min(count <= 1 ? 3.0D : count == 2 ? 3.7D : count <= 4 ? 4.6D : 5.6D, stage.reach() + 0.3D);
        Vec3 facing = new Vec3(stage.facing().getStepX(), 0.0D, stage.facing().getStepZ());
        Vec3 feet = Vec3.atBottomCenterOf(stage.pos());
        Vec3 aim = feet.add(0.0D, 1.05D, 0.0D).subtract(facing.scale(count > LobbyStage.ROW_SIZE ? 0.75D : 0.0D));
        Vec3 target = feet.add(facing.scale(distance)).add(0.0D, 1.62D, 0.0D);
        // Di Penampilan panelnya di kanan: geser kamera supaya karakter tampil di kiri tengah.
        Vec3 screenRight = new Vec3(facing.z, 0.0D, -facing.x);
        Vec3 shift = screenRight.scale(effectivePage() == Page.CUSTOMIZE ? distance * 0.3D : 0.0D);
        target = target.add(shift);
        aim = aim.add(shift);

        float noclip = noclipProgress();
        if (noclip > 0.0F) {
            // Kamera pelan-pelan mendekat dan menunduk mengikuti karakter yang tenggelam.
            float t = noclip * noclip * (3.0F - 2.0F * noclip);
            Vec3 close = feet.add(facing.scale(Math.max(1.8D, distance - 1.5D))).add(0.0D, 1.4D, 0.0D);
            target = target.lerp(close, t);
            aim = aim.lerp(feet.add(0.0D, 0.2D, 0.0D), t);
        }

        Vec3 dir = aim.subtract(target);
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) (-(Mth.atan2(dir.y, dir.horizontalDistance()) * Mth.RAD_TO_DEG));

        long now = System.nanoTime();
        float dt = lastFrame == 0L ? 0.0F : Math.min(0.1F, (now - lastFrame) / 1.0E9F);
        lastFrame = now;
        if (!cameraReady) {
            cameraPos = target;
            cameraYaw = yaw;
            cameraPitch = pitch;
            cameraReady = true;
            return;
        }
        float alpha = 1.0F - (float) Math.pow(0.02D, dt);
        cameraPos = cameraPos.lerp(target, alpha);
        cameraYaw = cameraYaw + Mth.wrapDegrees(yaw - cameraYaw) * alpha;
        cameraPitch = Mth.lerp(alpha, cameraPitch, pitch);
    }

    /** Karakter menghadap kamera, kepalanya mengikuti kursor; badannya bisa diputar dengan diseret. */
    private static void lookAtCursor(Minecraft client, LocalPlayer player) {
        float body = stage.playerYaw() + dragYaw;
        Vec3 target = cameraPos;
        if (noclipStart < 0L && client.screen instanceof LobbyPageScreen) {
            Window window = client.getWindow();
            double nx = client.mouseHandler.xpos() / Math.max(1, window.getScreenWidth()) * 2.0D - 1.0D;
            double ny = client.mouseHandler.ypos() / Math.max(1, window.getScreenHeight()) * 2.0D - 1.0D;
            float yawRad = cameraYaw * Mth.DEG_TO_RAD;
            Vec3 right = new Vec3(-Mth.cos(yawRad), 0.0D, -Mth.sin(yawRad));
            target = cameraPos.add(right.scale(nx * 2.6D)).add(0.0D, -ny * 1.6D, 0.0D);
        }
        Vec3 dir = target.subtract(player.getEyePosition());
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
        yaw = body + Mth.clamp(Mth.wrapDegrees(yaw - body), -70.0F, 70.0F);
        float pitch = Mth.clamp((float) (-(Mth.atan2(dir.y, dir.horizontalDistance()) * Mth.RAD_TO_DEG)), -50.0F, 50.0F);
        player.setYRot(yaw);
        player.yRotO = yaw;
        player.setYHeadRot(yaw);
        player.yHeadRotO = yaw;
        player.setXRot(pitch);
        player.xRotO = pitch;
        player.yBodyRot = body;
        player.yBodyRotO = body;
    }

    /** Pose pemain ini di lobby, atau {@code null} kalau tidak sedang di lobby. */
    public static LobbyPose poseOf(Player player) {
        if (!active) return null;
        Minecraft client = Minecraft.getInstance();
        if (player == client.player) return pose();
        if (room != null) {
            for (RoomInfo.Member member : room.members()) {
                if (member.id().equals(player.getUUID())) return LobbyPose.byId(member.pose());
            }
        }
        return null;
    }

    /** Pemain ini sedang tampil di set lobby (dirinya sendiri atau anggota room-nya). */
    public static boolean inLobbyScene(Player player) {
        return active && visibleInLobby(player);
    }

    private static boolean visibleInLobby(Player player) {
        if (player == Minecraft.getInstance().player) return true;
        if (room == null) return false;
        for (RoomInfo.Member member : room.members()) {
            if (member.id().equals(player.getUUID())) return true;
        }
        return false;
    }

    private static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        pushedSink = false;
        if (!active) return;
        if (!visibleInLobby(event.getEntity())) {
            // Pemain lain di set lobby yang sama tapi bukan anggota room ini.
            event.setCanceled(true);
            return;
        }
        float noclip = noclipProgress();
        if (noclip > 0.0F) {
            event.getPoseStack().pushPose();
            event.getPoseStack().translate(0.0D, -NOCLIP_DEPTH * noclip * noclip, 0.0D);
            pushedSink = true;
        }
    }

    private static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (pushedSink) {
            event.getPoseStack().popPose();
            pushedSink = false;
        }
    }

    private static void onRenderHand(RenderHandEvent event) {
        if (overridesCamera() || cutsceneActive()) event.setCanceled(true);
    }

    private static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        if (cutsceneActive()) {
            event.setCanceled(true);
        } else if (active && (!event.getOverlay().id().equals(VanillaGuiOverlay.CHAT_PANEL.id())
                || !(Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.ChatScreen))) {
            event.setCanceled(true);
        }
    }

    /** Layar "Loading terrain" saat pindah dimensi diganti layar hitam selama lobby/cutscene. */
    private static void onScreenRender(ScreenEvent.Render.Pre event) {
        if (event.getScreen() instanceof ReceivingLevelScreen && (active || cutsceneActive())) {
            event.setCanceled(true);
            Screen screen = event.getScreen();
            event.getGuiGraphics().fill(0, 0, screen.width, screen.height, 0xFF000000);
        }
    }

    /** Tombol "Kembali ke Lobby" di menu pause selama di dalam Backrooms. */
    private static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof PauseScreen screen && !active && CameraEffect.isInBackrooms()) {
            event.addListener(Button.builder(Component.literal("Kembali ke lobby"), button -> {
                send(LobbyActionC2S.Action.GO_LOBBY);
                Minecraft.getInstance().setScreen(null);
            }).bounds(screen.width / 2 - 102, screen.height - 30, 204, 20).build());
        }
    }

    // ============================================================ suara

    private static void onPlaySound(PlaySoundEvent event) {
        if (!active || event.getSound() == null) return;
        SoundSource source = event.getSound().getSource();
        if (source == SoundSource.MUSIC) {
            // Musik vanilla tidak cocok di lobby.
            event.setSound(null);
        } else if (LobbyTrack.byId(ClientConfig.lobbySound()) == LobbyTrack.HENING && source != SoundSource.MASTER) {
            event.setSound(null);
        }
    }

    private static void tickMusic(Minecraft client) {
        LobbyTrack track = LobbyTrack.byId(ClientConfig.lobbySound());
        boolean shuffle = track == LobbyTrack.ACAK;
        musicAge++;
        boolean playing = music != null && client.getSoundManager().isActive(music);
        // Acak: lagu selesai -> lanjut ke lagu berikutnya.
        if (shuffle && music != null && !playing && musicAge > 20) {
            playlistIndex = (playlistIndex + 1) % LobbyTrack.PLAYLIST.length;
        }
        String wanted = null;
        if (active && !cutsceneActive()) {
            wanted = shuffle ? LobbyTrack.PLAYLIST[playlistIndex] : track.sound(background);
        }
        if (wanted != null && !ModSounds.available(wanted)) wanted = null;
        boolean restart = wanted != null && !playing && musicAge > 20;
        if (!java.util.Objects.equals(wanted, musicId) || restart) {
            if (music != null) client.getSoundManager().stop(music);
            music = null;
            musicId = wanted;
            if (wanted != null) {
                music = new LobbyMusic(ModSounds.event(wanted), !shuffle);
                client.getSoundManager().play(music);
                musicAge = 0;
            }
        }
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        active = false;
        stage = null;
        room = null;
        rooms = List.of();
        page = Page.MAIN;
        noclipStart = -1L;
        cameraReady = false;
        music = null;
        musicId = null;
        revealPending = false;
        IntroCutscene.cancel();
        version++;
    }
}
