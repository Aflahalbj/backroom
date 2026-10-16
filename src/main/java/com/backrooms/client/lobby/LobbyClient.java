package com.backrooms.client.lobby;

import com.backrooms.client.CameraEffect;
import com.backrooms.client.ClientConfig;
import com.backrooms.client.lobby.screen.AddPlayerScreen;
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
import com.backrooms.lobby.RoomState;
import com.backrooms.mixin.CameraAccessor;
import com.backrooms.network.CutsceneS2C;
import com.backrooms.network.LobbyActionC2S;
import com.backrooms.network.LobbyStateS2C;
import com.backrooms.network.ModNetwork;
import com.backrooms.sound.ModSounds;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Camera;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ViewportEvent;
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
 * perkenalan + intro.
 *
 * <p>Selama di lobby atau cutscene selalu ada layar yang terbuka, jadi semua
 * input gerak tertahan. Layar vanilla yang dibuka dari lobby (menu pause,
 * chat) dibiarkan; begitu ditutup, layar lobby dibuka lagi.
 *
 * <p>Saat room mulai, cutscene perkenalan ({@link DropCutscene}) memegang
 * kamera dan gambar anggota room sampai server memindahkan pemain.
 */
public final class LobbyClient {

    public enum Page { MAIN, JOIN, CUSTOMIZE, ROOM, ADD_PLAYER }

    // ------------------------------------------------- keadaan dari server
    private static boolean active;
    private static int background;
    private static int pose;
    private static int weapon;
    private static LobbyStage stage;
    private static int percent;
    private static RoomInfo room;
    private static List<RoomInfo.Summary> rooms = List.of();
    /** Owner: pemain di lobby yang bisa dimasukkan ke room. */
    private static List<RoomInfo.Candidate> candidates = List.of();
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
    private static boolean pushedDrop;
    /** Arah kepala asli pemain yang sedang digambar selama cutscene (dikembalikan sesudahnya). */
    private static final float[] SAVED_HEAD = new float[4];

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
        MinecraftForge.EVENT_BUS.addListener(LobbyClient::onComputeFov);
        MinecraftForge.EVENT_BUS.addListener(LobbyClient::onFogColor);
        MinecraftForge.EVENT_BUS.addListener(LobbyClient::onNameTag);
        MinecraftForge.EVENT_BUS.addListener(DropScenery::onRenderStage);
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
            DropCutscene.stop();
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
        if (page == Page.ADD_PLAYER && !canAddPlayers()) {
            page = info != null ? Page.ROOM : Page.MAIN;
        }
        if (info == null) candidates = List.of();
        version++;
    }

    public static void onCandidates(List<RoomInfo.Candidate> list) {
        candidates = list;
        version++;
    }

    public static void onRoomList(List<RoomInfo.Summary> list) {
        rooms = list;
        version++;
    }

    public static void onCutscene(CutsceneS2C msg) {
        switch (msg.type()) {
            case DROP -> {
                if (stage != null) {
                    if (!cameraReady) updateCamera();
                    DropCutscene.start(msg.members(), stage, cameraPos, cameraYaw, cameraPitch);
                }
            }
            case INTRO -> IntroCutscene.start(msg.offsetX(), msg.offsetZ());
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

    /** Owner: jadikan anggota (atau diri sendiri) pemain atau host. */
    public static void setRole(UUID member, RoomInfo.Role role) {
        ModNetwork.CHANNEL.sendToServer(new LobbyActionC2S(LobbyActionC2S.Action.SET_ROLE, role.ordinal(), member.toString()));
    }

    /** Owner: masukkan pemain di lobby langsung ke room. */
    public static void addPlayer(UUID player) {
        ModNetwork.CHANNEL.sendToServer(new LobbyActionC2S(LobbyActionC2S.Action.ADD_PLAYER, 0, player.toString()));
    }

    /** Owner: pindahkan pemain ke tim berikutnya. */
    public static void moveTeam(UUID member) {
        ModNetwork.CHANNEL.sendToServer(new LobbyActionC2S(LobbyActionC2S.Action.MOVE_TEAM, 0, member.toString()));
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
    public static List<RoomInfo.Candidate> candidates() { return candidates; }
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

    /** Pemain ini owner room-nya (pembuat room: yang mengatur dan menekan Mulai). */
    public static boolean isOwner() {
        Minecraft client = Minecraft.getInstance();
        return room != null && client.player != null && room.isOwner(client.player.getUUID());
    }

    /** Owner room yang masih menunggu boleh memasukkan pemain langsung (server juga memeriksanya). */
    public static boolean canAddPlayers() {
        return isOwner() && room.state() == RoomState.WAITING;
    }

    /** Hanya OP yang boleh membuat room (server juga memeriksanya). */
    public static boolean canCreateRoom() {
        Minecraft client = Minecraft.getInstance();
        return client.player != null && client.player.hasPermissions(2);
    }

    /** Untuk pratinjau animasi: arah badan karakter relatif ke kamera (derajat). */
    public static void setDragYaw(float yaw) {
        dragYaw = Mth.wrapDegrees(yaw);
    }

    public static void dragCharacter(double dx) {
        dragYaw = Mth.wrapDegrees(dragYaw - (float) dx * 1.5F);
    }

    /** Cutscene perkenalan atau intro sedang berjalan: input ditahan oleh {@link CutsceneScreen}. */
    public static boolean cutsceneActive() {
        return DropCutscene.isRunning() || IntroCutscene.isRunning();
    }

    /**
     * Pratinjau (AnimDev/uji): cutscene perkenalan room ini tanpa server; pemain
     * tidak dipindahkan sesudahnya, jadi cutscene berakhir sendiri dan lantai dikembalikan.
     */
    public static void previewDrop() {
        Minecraft client = Minecraft.getInstance();
        if (stage == null || client.player == null) return;
        List<UUID> lineup = new java.util.ArrayList<>();
        if (room != null) {
            room.players().forEach(m -> lineup.add(m.id()));
            room.hosts().forEach(m -> lineup.add(m.id()));
        }
        if (!lineup.contains(client.player.getUUID())) lineup.add(0, client.player.getUUID());
        if (!cameraReady) updateCamera();
        DropCutscene.start(lineup, stage, cameraPos, cameraYaw, cameraPitch);
    }

    // ========================================================== layar

    public static void openPage(Page target) {
        if (target == Page.CUSTOMIZE && page != Page.CUSTOMIZE) {
            customizeReturn = page == Page.ROOM ? Page.ROOM : Page.MAIN;
        }
        if (target == Page.JOIN) send(LobbyActionC2S.Action.REQUEST_LIST);
        if (target == Page.ADD_PLAYER) send(LobbyActionC2S.Action.REQUEST_CANDIDATES);
        page = target;
        Minecraft.getInstance().setScreen(createPage());
    }

    /** Kembali dari Customize/Gabung ke layar sebelumnya. */
    public static void back() {
        openPage(page == Page.CUSTOMIZE ? customizeReturn : room != null ? Page.ROOM : Page.MAIN);
    }

    /** Halaman yang seharusnya tampil: selama ikut room, selalu layar room (kecuali Customize dan Tambah pemain). */
    private static Page effectivePage() {
        if (page == Page.ADD_PLAYER) return canAddPlayers() ? page : room != null ? Page.ROOM : Page.MAIN;
        return room != null && page != Page.CUSTOMIZE ? Page.ROOM : page;
    }

    private static Screen createPage() {
        return switch (effectivePage()) {
            case MAIN -> new LobbyScreen();
            case JOIN -> new JoinRoomScreen();
            case CUSTOMIZE -> new CustomizeScreen();
            case ROOM -> new RoomScreen();
            case ADD_PLAYER -> new AddPlayerScreen();
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
        DropCutscene.clientTick();
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
        DropCutscene.frame();
        IntroCutscene.tick();
        Minecraft client = Minecraft.getInstance();
        if (active && stage != null && client.player != null) {
            updateCamera();
            lookAtCursor(client, client.player);
        }
    }

    /** Dipanggil {@code CameraMixin} setelah kamera vanilla disiapkan. */
    public static boolean applyCamera(Camera camera) {
        if (DropCutscene.applyCamera(camera)) return true;
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
        return active && stage != null || DropCutscene.ownsCamera() || IntroCutscene.isPlaying();
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
        if (DropCutscene.isRunning()) {
            // Selama cutscene arah badan/kepala diatur animasi dan cutscene, bukan kursor.
            body = stage.playerYaw();
            player.setYRot(body);
            player.yRotO = body;
            player.setYHeadRot(body);
            player.yHeadRotO = body;
            player.setXRot(0.0F);
            player.xRotO = 0.0F;
            player.yBodyRot = body;
            player.yBodyRotO = body;
            return;
        }
        Vec3 target = cameraPos;
        if (client.screen instanceof LobbyPageScreen) {
            Window window = client.getWindow();
            double nx = client.mouseHandler.xpos() / Math.max(1, window.getScreenWidth()) * 2.0D - 1.0D;
            double ny = client.mouseHandler.ypos() / Math.max(1, window.getScreenHeight()) * 2.0D - 1.0D;
            float yawRad = cameraYaw * Mth.DEG_TO_RAD;
            Vec3 right = new Vec3(-Mth.cos(yawRad), 0.0D, -Mth.sin(yawRad));
            target = cameraPos.add(right.scale(nx * 2.6D)).add(0.0D, -ny * 1.6D, 0.0D);
        }
        // Dari tempat karakter ini tampil (bisa digeser ke depan tengah, lihat renderOffset).
        Vec3 offset = lobbyOffset(player);
        Vec3 eye = offset == null ? player.getEyePosition() : player.getEyePosition().add(offset);
        Vec3 dir = target.subtract(eye);
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

    /**
     * Geseran gambar pemain ini (dipakai {@code LobbyLevelRendererMixin}):
     * selama cutscene perkenalan ke posisinya di udara, selain itu
     * {@link #lobbyOffset}. {@code null} = tidak digeser.
     */
    public static Vec3 renderOffset(Entity entity, float partialTick) {
        if (DropCutscene.isRunning()) return DropCutscene.renderOffset(entity, partialTick);
        return lobbyOffset(entity);
    }

    /**
     * Geseran gambar pemain ini di barisan room: setiap klien melihat
     * karakternya sendiri di depan tengah (slot pertama), bertukar tempat
     * dengan anggota yang ditaruh server di sana. {@code null} = tidak digeser.
     */
    public static Vec3 lobbyOffset(Entity entity) {
        Minecraft client = Minecraft.getInstance();
        if (!active || stage == null || room == null || client.player == null || !(entity instanceof Player)) return null;
        // Urutan barisan sama dengan server: pemain dulu, host di ujung.
        List<RoomInfo.Member> lineup = new java.util.ArrayList<>(room.players());
        lineup.addAll(room.hosts());
        int self = -1;
        for (int i = 0; i < lineup.size(); i++) {
            if (lineup.get(i).id().equals(client.player.getUUID())) self = i;
        }
        if (self <= 0) return null;
        int count = lineup.size();
        if (entity == client.player) return stage.slot(0, count).subtract(stage.slot(self, count));
        if (lineup.get(0).id().equals(entity.getUUID())) return stage.slot(self, count).subtract(stage.slot(0, count));
        return null;
    }

    /** Pemain ini sedang tampil di set lobby (dirinya sendiri atau anggota room-nya). */
    public static boolean inLobbyScene(Player player) {
        return active && visibleInLobby(player);
    }

    /** Yang tampil di set lobby: diri sendiri, dan anggota room ini (pemain maupun host). */
    private static boolean visibleInLobby(Player player) {
        if (player == Minecraft.getInstance().player) return true;
        return room != null && room.member(player.getUUID()) != null;
    }

    private static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        pushedDrop = false;
        if (!active) return;
        Player player = event.getEntity();
        if (!visibleInLobby(player)) {
            // Pemain lain di set lobby yang sama tapi bukan anggota room ini.
            event.setCanceled(true);
            return;
        }
        if (DropCutscene.hiddenInCutscene(player)) {
            event.setCanceled(true);
            return;
        }
        float yaw = DropCutscene.bodyYaw(player);
        if (!Float.isNaN(yaw)) {
            // Putar seluruh badan ke arah hadapnya di formasi; kepala lurus mengikuti badan (diatur animasi).
            float body = Mth.rotLerp(event.getPartialTick(), player.yBodyRotO, player.yBodyRot);
            event.getPoseStack().pushPose();
            event.getPoseStack().mulPose(com.mojang.math.Axis.YP.rotationDegrees(body - yaw));
            SAVED_HEAD[0] = player.yHeadRot;
            SAVED_HEAD[1] = player.yHeadRotO;
            SAVED_HEAD[2] = player.getXRot();
            SAVED_HEAD[3] = player.xRotO;
            player.yHeadRot = player.yBodyRot;
            player.yHeadRotO = player.yBodyRotO;
            player.setXRot(0.0F);
            player.xRotO = 0.0F;
            pushedDrop = true;
        }
    }

    private static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (pushedDrop) {
            event.getPoseStack().popPose();
            Player player = event.getEntity();
            player.yHeadRot = SAVED_HEAD[0];
            player.yHeadRotO = SAVED_HEAD[1];
            player.setXRot(SAVED_HEAD[2]);
            player.xRotO = SAVED_HEAD[3];
            pushedDrop = false;
        }
    }

    /** FOV kamera cutscene (dolly zoom, sentakan saat menyambar). */
    private static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (DropCutscene.ownsCamera()) event.setFOV(DropCutscene.fov());
    }

    /** Sejak lantai runtuh: latar gelap netral (langit lobby biru tua, langit/kabut Poolrooms putih). */
    private static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (DropCutscene.darkVoid()) {
            event.setRed(0.02F);
            event.setGreen(0.018F);
            event.setBlue(0.016F);
        }
    }

    /** Selama cutscene nama di atas kepala disembunyikan (diganti kartu nama). */
    private static void onNameTag(RenderNameTagEvent event) {
        if (DropCutscene.isMember(event.getEntity())) event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
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

    /**
     * Tombol "Kembali ke Lobby" di menu pause selama di dalam Backrooms. Selama
     * kulit pemain dicuri Skinwalker, tombol ini (seperti tombol keluar) menjadi
     * "There Is No Escape".
     */
    private static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof PauseScreen screen && !active && CameraEffect.isInBackrooms()) {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null && com.backrooms.component.PlayerComponent.get(client.player).hasBeenCaptured()) {
                event.addListener(Button.builder(Component.translatable("skinwalker.flavor-text.leave").withStyle(ChatFormatting.RED),
                        button -> com.backrooms.client.skinwalker.SkinWalkerCapturedFlavorText.triedToLeave = true)
                        .bounds(screen.width / 2 - 102, screen.height - 30, 204, 20).build());
                return;
            }
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
        candidates = List.of();
        page = Page.MAIN;
        DropCutscene.stop();
        cameraReady = false;
        music = null;
        musicId = null;
        revealPending = false;
        IntroCutscene.cancel();
        version++;
    }
}
