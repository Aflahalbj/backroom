package com.backrooms.client;

import com.backrooms.client.lobby.IntroCutscene;
import com.backrooms.client.lobby.LobbyClient;
import com.backrooms.lobby.RoomInfo;
import com.backrooms.lobby.RoomState;
import com.backrooms.network.LobbyActionC2S;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Uji klien otomatis khusus development ({@code gradlew runClient -Pclienttest=true}):
 * masuk ke world uji, menjalani lobby (pose, latar, room, START, noclip,
 * intro), berkunjung ke tiap level, menyalakan/mematikan senter dan efek
 * kamera, mengambil screenshot, lalu menutup game. Tidak aktif di permainan
 * biasa.
 */
public final class ClientSelfTest {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<Step> STEPS = new ArrayList<>();
    private static int index;
    private static int wait;

    /** Jalankan {@code action} setelah {@code delayTicks} dan {@code ready} terpenuhi (paling lama {@code timeoutTicks}). */
    private record Step(int delayTicks, BooleanSupplier ready, int timeoutTicks, String name, Runnable action) {}

    private ClientSelfTest() {}

    public static void init() {
        if (!Boolean.getBoolean("backrooms.clienttest")) return;
        if ("release".equals(System.getProperty("backrooms.skinwalkertest"))) {
            skinwalkerRelease();
            MinecraftForge.EVENT_BUS.addListener(ClientSelfTest::onTick);
            return;
        }
        if ("natural".equals(System.getProperty("backrooms.skinwalkertest"))) {
            skinwalkerNatural();
            MinecraftForge.EVENT_BUS.addListener(ClientSelfTest::onTick);
            return;
        }
        if (Boolean.getBoolean("backrooms.skinwalkertest")) {
            skinwalker();
            MinecraftForge.EVENT_BUS.addListener(ClientSelfTest::onTick);
            return;
        }
        until("lobby siap", LobbyClient::isReady, 6000);
        shot(60, "lobby_main");
        step(1, ClientSelfTest::diagnose);
        lobby(LobbyActionC2S.Action.SET_POSE, 1);
        shot(30, "lobby_pose_bersilang");
        lobby(LobbyActionC2S.Action.SET_POSE, 2);
        shot(30, "lobby_pose_melambai");
        lobby(LobbyActionC2S.Action.SET_POSE, 4);
        shot(30, "lobby_pose_siaga");
        lobby(LobbyActionC2S.Action.SET_POSE, 0);
        step(5, () -> LobbyClient.openPage(LobbyClient.Page.CUSTOMIZE));
        shot(20, "lobby_customize");
        lobby(LobbyActionC2S.Action.SET_BACKGROUND, 1);
        until("latar level 1", LobbyClient::isReady, 2400);
        shot(80, "lobby_bg_level1");
        lobby(LobbyActionC2S.Action.SET_BACKGROUND, 2);
        until("latar poolrooms", LobbyClient::isReady, 2400);
        shot(80, "lobby_bg_poolrooms");
        lobby(LobbyActionC2S.Action.SET_BACKGROUND, 0);
        until("latar level 0", LobbyClient::isReady, 2400);
        shot(100, "lobby_bg_level0");
        step(1, ClientSelfTest::diagnose);
        step(10, LobbyClient::back);
        step(5, () -> LobbyClient.openPage(LobbyClient.Page.JOIN));
        shot(20, "lobby_join_empty");
        step(5, LobbyClient::back);
        lobby(LobbyActionC2S.Action.CREATE_ROOM, 0);
        until("room dibuat", () -> LobbyClient.room() != null, 200);
        shot(40, "room_waiting");
        lobby(LobbyActionC2S.Action.SET_TEAM_SIZE, 2);
        until("mode duo", () -> LobbyClient.room() != null && LobbyClient.room().teamSize() == 2, 200);
        step(5, () -> LobbyClient.setRole(Minecraft.getInstance().player.getUUID(), RoomInfo.Role.HOST));
        until("owner jadi host", () -> LobbyClient.room() != null && !LobbyClient.room().hosts().isEmpty(), 200);
        shot(20, "room_owner_host");
        step(5, () -> LobbyClient.setRole(Minecraft.getInstance().player.getUUID(), RoomInfo.Role.PLAYER));
        until("owner jadi pemain", () -> LobbyClient.room() != null && LobbyClient.room().hosts().isEmpty(), 200);
        shot(20, "room_duo");
        lobby(LobbyActionC2S.Action.START, 0);
        until("room membangun", () -> LobbyClient.room() != null && LobbyClient.room().state() != RoomState.WAITING, 200);
        shot(10, "room_building");
        until("noclip", () -> LobbyClient.noclipTime() >= 0L, 12000);
        shot(30, "noclip_sink");
        until("intro jatuh", IntroCutscene::isPlaying, 400);
        shot(70, "intro_fall");
        until("judul intro", IntroCutscene::showTitle, 400);
        shot(20, "intro_title");
        until("intro selesai", () -> !LobbyClient.cutsceneActive(), 600);
        shot(40, "level0_room");
        step(1, ClientSelfTest::diagnose);
        step(5, () -> ClientConfig.set(ClientConfig.ENABLE_SHADOWS, false));
        shot(60, "level0_noshadows");
        step(5, () -> ClientConfig.set(ClientConfig.ENABLE_SHADOWS, true));
        step(5, () -> Flashlight.press(Minecraft.getInstance().player, true));
        shot(60, "level0_flashlight");
        command(5, "backroom camera off");
        shot(60, "level0_noeffect");
        command(5, "backroom camera on");
        command(5, "backroom gotolevel1");
        shot(400, "level1_effect");
        command(5, "backroom gotopoolrooms");
        shot(400, "poolrooms_effect");
        step(5, () -> Flashlight.press(Minecraft.getInstance().player, true));
        shot(40, "poolrooms_flashlight_refused");
        lobby(LobbyActionC2S.Action.GO_LOBBY, 0);
        until("kembali ke lobby", () -> LobbyClient.isActive() && LobbyClient.isReady(), 400);
        shot(60, "lobby_back");
        step(20, () -> {
            LOGGER.info("[clienttest] selesai");
            Minecraft.getInstance().stop();
        });
        MinecraftForge.EVENT_BUS.addListener(ClientSelfTest::onTick);
    }

    /** Uji Skinwalker: wujud menyamar, wujud asli (kaki IK), transformasi + lampu, lalu {@code /skinwalker} ke diri sendiri. */
    private static void skinwalker() {
        until("lobby siap", LobbyClient::isReady, 6000);
        command(20, "backroom gotolevel0");
        until("di level 0", () -> CameraEffect.isInLevel(com.backrooms.world.BackroomsLevel.LEVEL0) && !LobbyClient.isActive(), 2400);
        // World uji dibuat Peaceful; Skinwalker (monster) langsung hilang di Peaceful, sama seperti aslinya.
        command(5, "difficulty normal");
        command(5, "gamemode creative");
        command(5, "backroom flashlight on");
        command(20, "summon spb-revamped:skin_walker ^ ^ ^4");
        shot(60, "skinwalker_disguised");
        step(1, ClientSelfTest::diagnose);
        command(5, "kill @e[type=spb-revamped:skin_walker]");
        command(20, "summon spb-revamped:skin_walker ^ ^ ^5 {\"spb-revamped:skw\":{trueForm:1b}}");
        shot(40, "skinwalker_trueform");
        shot(80, "skinwalker_trueform_walk");
        command(5, "kill @e[type=spb-revamped:skin_walker]");
        command(20, "summon spb-revamped:skin_walker ^ ^ ^4 {\"spb-revamped:skw\":{beginReveal:1b}}");
        shot(60, "skinwalker_reveal_start");
        shot(65, "skinwalker_reveal_flicker");
        shot(85, "skinwalker_reveal_lightsoff");
        shot(40, "skinwalker_reveal_done");
        command(5, "kill @e[type=spb-revamped:skin_walker]");
        command(5, "gamemode survival");
        command(40, "backroom skinwalker @s");
        shot(30, "skinwalker_jumpscare");
        shot(60, "skinwalker_jumpscare_face1");
        shot(200, "skinwalker_jumpscare_face2");
        shot(80, "skinwalker_captured_view");
        step(1, ClientSelfTest::diagnose);
        command(20, "backroom release @s");
        shot(40, "skinwalker_released");
        step(20, () -> {
            LOGGER.info("[clienttest] selesai");
            Minecraft.getInstance().stop();
        });
    }

    /**
     * Pemicu alami Skinwalker: pemain ini + satu fake player (mod dev advfakeplayer) di Level 0,
     * intercom sudah dua kali, pemain yang paling jarang bicara (lewat voice chat) ditangkap
     * selama pemain lain tidak melihatnya.
     */
    private static void skinwalkerNatural() {
        until("lobby siap", LobbyClient::isReady, 6000);
        // Jendela uji sering tidak difokus; tanpa ini menu pause muncul dan server singleplayer berhenti.
        step(1, () -> Minecraft.getInstance().options.pauseOnLostFocus = false);
        command(20, "backroom gotolevel0");
        until("di level 0", () -> CameraEffect.isInLevel(com.backrooms.world.BackroomsLevel.LEVEL0) && !LobbyClient.isActive(), 2400);
        command(5, "difficulty normal");
        command(5, "gamemode survival");
        command(5, "kill @e[type=spb-revamped:skin_walker]");
        command(5, "backroom release @s");
        // Run sebelumnya yang ditutup di tengah jumpscare: isBeingCaptured ikut tersimpan (seperti aslinya), tunggu habis.
        until("sisa jumpscare habis", () -> !com.backrooms.component.PlayerComponent.get(Minecraft.getInstance().player).isBeingCaptured(), 400);
        command(10, "fakeplayer spawn bob");
        // Fake player juga masuk lobby saat login (LobbyManager); tunggu, lalu bawa ke Level 0 sebagai pemain biasa.
        command(80, "execute as bob run backroom gotolevel0");
        command(60, "tp bob @s");
        command(10, "gamemode survival bob");
        command(10, "execute as bob at @s run tp @s ^ ^ ^3");
        command(5, "execute as bob at @s run tp @s ~ 21 ~");
        // Pemicu alami hanya sekali per world (flag "skinwalkerDone"); world uji dipakai berulang.
        step(5, () -> onServer(server -> {
            var level = server.getLevel(com.backrooms.world.BackroomsLevel.LEVEL0.dimension());
            var player = server.getPlayerList().getPlayer(Minecraft.getInstance().player.getUUID());
            com.backrooms.world.events.WorldEvents.get(level, player.getX()).done = false;
        }));
        shot(40, "natural_fakeplayer");
        // Intercom ke-2 sudah lewat (aslinya menunggu event Level 0 beberapa menit).
        step(5, () -> onServer(server -> {
            var level = server.getLevel(com.backrooms.world.BackroomsLevel.LEVEL0.dimension());
            var player = server.getPlayerList().getPlayer(Minecraft.getInstance().player.getUUID());
            com.backrooms.world.events.WorldEvents.get(level, player.getX()).setIntercomCount(2);
            // World dev dimulai creative; plugin hanya mencatat pemain survival saat voice chat tersambung.
            com.backrooms.compat.voicechat.BackroomsVoicechatPlugin.speakingTime.putIfAbsent(player.getUUID(), 0.0F);
            LOGGER.info("[clienttest] intercom=2, pemain di salinan ini: {}, speakingTime: {}",
                    com.backrooms.world.events.WorldEvents.get(level, player.getX()).players().stream()
                            .map(p -> p.getGameProfile().getName() + "@" + p.blockPosition().toShortString()).toList(),
                    com.backrooms.compat.voicechat.BackroomsVoicechatPlugin.speakingTime);
        }));
        step(20, ClientSelfTest::serverDiagnose);
        step(40, ClientSelfTest::serverDiagnose);
        until("ditangkap skinwalker", () -> com.backrooms.component.PlayerComponent.get(Minecraft.getInstance().player).hasBeenCaptured(), 1200);
        step(1, ClientSelfTest::diagnose);
        step(1, ClientSelfTest::serverDiagnose);
        shot(30, "natural_jumpscare");
        until("jumpscare selesai", () -> !com.backrooms.component.PlayerComponent.get(Minecraft.getInstance().player).isBeingCaptured(), 600);
        step(1, ClientSelfTest::diagnose);
        shot(40, "natural_captured_view");
        shot(200, "natural_following");
        step(1, ClientSelfTest::diagnose);
        command(20, "backroom release @s");
        command(20, "fakeplayer remove bob");
        shot(40, "natural_released");
        step(20, () -> {
            LOGGER.info("[clienttest] selesai");
            Minecraft.getInstance().stop();
        });
    }

    /**
     * Dua kali berturut-turut dalam satu sesi: ditangkap, Skinwalker berubah wujud dan membunuh fake player,
     * korban harus dilepas (gamemode kembali, Skinwalker hilang).
     */
    private static void skinwalkerRelease() {
        until("lobby siap", LobbyClient::isReady, 6000);
        step(1, () -> Minecraft.getInstance().options.pauseOnLostFocus = false);
        command(20, "backroom gotolevel0");
        until("di level 0", () -> CameraEffect.isInLevel(com.backrooms.world.BackroomsLevel.LEVEL0) && !LobbyClient.isActive(), 2400);
        command(100, "difficulty normal");
        command(5, "gamemode survival");
        command(5, "kill @e[type=spb-revamped:skin_walker]");
        until("sisa jumpscare habis", () -> !com.backrooms.component.PlayerComponent.get(Minecraft.getInstance().player).isBeingCaptured(), 400);
        for (int round = 1; round <= 2; round++) {
            String r = "round" + round;
            command(10, "fakeplayer remove bob");
            command(10, "fakeplayer spawn bob");
            command(80, "execute as bob run backroom gotolevel0");
            command(60, "tp bob @s");
            command(10, "gamemode survival bob");
            command(10, "execute as bob at @s run tp @s ^ ^ ^2");
            command(10, "backroom skinwalker @s");
            until(r + " ditangkap", () -> com.backrooms.component.PlayerComponent.get(Minecraft.getInstance().player).hasBeenCaptured(), 200);
            until(r + " jumpscare selesai", () -> !com.backrooms.component.PlayerComponent.get(Minecraft.getInstance().player).isBeingCaptured(), 600);
            if (round == 1) {
                // Celah kabur: teleport spectator (tombol 1) ke pemain di dimensi lain (carl ada di lobby).
                command(5, "fakeplayer spawn carl");
                step(60, () -> {
                    Minecraft client = Minecraft.getInstance();
                    var carl = client.getConnection().getOnlinePlayers().stream()
                            .filter(info -> info.getProfile().getName().equals("carl")).findFirst().orElse(null);
                    LOGGER.info("[clienttest] teleport spectator ke carl, sebelum: {} {}", client.level.dimension().location(), client.player.blockPosition().toShortString());
                    if (carl != null) {
                        client.getConnection().send(new net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket(carl.getProfile().getId()));
                    }
                });
                step(40, () -> {
                    Minecraft client = Minecraft.getInstance();
                    LOGGER.info("[clienttest] teleport spectator ke carl, sesudah: {} {} (harus tetap level0)", client.level.dimension().location(), client.player.blockPosition().toShortString());
                    var self = client.getConnection().getPlayerInfo(client.player.getUUID());
                    SkinWalkerTabCheck.log(self);
                });
                step(1, () -> Minecraft.getInstance().options.keyPlayerList.setDown(true));
                shot(5, "release_tab_captured");
                step(1, () -> Minecraft.getInstance().options.keyPlayerList.setDown(false));
                command(5, "fakeplayer remove carl");
            }
            // Langsung transformasi (aslinya setelah 2 menit atau dicurigai).
            step(5, () -> onServer(server -> {
                var level = server.getLevel(com.backrooms.world.BackroomsLevel.LEVEL0.dimension());
                level.getEntities(com.backrooms.entity.ModEntities.SKIN_WALKER.get(), e -> true)
                        .forEach(e -> e.getSkinWalkerComponent().setBeginReveal(true));
            }));
            step(1, ClientSelfTest::serverDiagnose);
            until(r + " dilepas", () -> !com.backrooms.component.PlayerComponent.get(Minecraft.getInstance().player).hasBeenCaptured()
                    && !Minecraft.getInstance().player.isSpectator(), 2400);
            step(1, ClientSelfTest::serverDiagnose);
            shot(20, "release_" + r);
        }
        step(20, () -> {
            LOGGER.info("[clienttest] selesai");
            Minecraft.getInstance().stop();
        });
    }

    /** Mode yang dilihat daftar pemain (tab) untuk korban, dibanding mode sebenarnya. */
    private static final class SkinWalkerTabCheck {
        static void log(net.minecraft.client.multiplayer.PlayerInfo info) {
            if (info == null) return;
            GameTypePair pair = new GameTypePair(info.getGameMode(), tab(info));
            LOGGER.info("[clienttest] tab: mode sebenarnya={} di tab={}", pair.real(), pair.tab());
        }

        private static net.minecraft.world.level.GameType tab(net.minecraft.client.multiplayer.PlayerInfo info) {
            com.backrooms.client.skinwalker.SkinWalkerClient.renderingTab = true;
            try {
                return info.getGameMode();
            } finally {
                com.backrooms.client.skinwalker.SkinWalkerClient.renderingTab = false;
            }
        }

        private record GameTypePair(net.minecraft.world.level.GameType real, net.minecraft.world.level.GameType tab) {}
    }

    private static void serverDiagnose() {
        onServer(server -> {
            var level = server.getLevel(com.backrooms.world.BackroomsLevel.LEVEL0.dimension());
            for (var p : server.getPlayerList().getPlayers()) {
                var c = com.backrooms.component.PlayerComponent.get(p);
                LOGGER.info("[clienttest] server {} dim={} pos={} mode={} camera={} captured={}/{} muted={} speaking={} vel={}",
                        p.getGameProfile().getName(), p.level().dimension().location(), p.blockPosition().toShortString(),
                        p.gameMode.getGameModeForPlayer(), p.getCamera().getType(), c.isBeingCaptured(), c.hasBeenCaptured(),
                        c.shouldBeMuted(), c.isSpeaking(), p.getDeltaMovement());
            }
            var dev = server.getPlayerList().getPlayer(Minecraft.getInstance().player.getUUID());
            var events = com.backrooms.world.events.WorldEvents.get(level, dev.getX());
            LOGGER.info("[clienttest] server events intercom={} done={} active={} target={} skinwalkers={}",
                    events.getIntercomCount(), events.done, events.activeSkinWalkerEntity, events.getActiveSkinwalkerTarget(),
                    level.getEntities(com.backrooms.entity.ModEntities.SKIN_WALKER.get(), e -> true).size());
        });
    }

    private static void onServer(java.util.function.Consumer<net.minecraft.server.MinecraftServer> action) {
        net.minecraft.server.MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) server.execute(() -> action.accept(server));
    }

    private static void diagnose() {
        Minecraft client = Minecraft.getInstance();
        var lights = foundry.veil.api.client.render.VeilRenderSystem.renderer().getDeferredRenderer();
        LOGGER.info("[clienttest] dim={} pos={} inWall={} black={} lobby={} room={} | deferred enabled={} active={} points={} areas={}",
                client.level == null ? "-" : client.level.dimension().location(),
                client.player == null ? "-" : client.player.position(),
                client.player != null && client.player.isInWall(), CameraEffect.blackScreen,
                LobbyClient.isActive(), LobbyClient.room(),
                lights.isEnabled(), lights.isActive(),
                lights.getLightRenderer().getLights(foundry.veil.api.client.registry.LightTypeRegistry.POINT.get()).size(),
                lights.getLightRenderer().getLights(foundry.veil.api.client.registry.LightTypeRegistry.AREA.get()).size());
    }

    private static void step(int delay, Runnable action) {
        STEPS.add(new Step(delay, () -> true, 0, "", action));
    }

    private static void until(String name, BooleanSupplier ready, int timeoutTicks) {
        STEPS.add(new Step(1, ready, timeoutTicks, name, () -> LOGGER.info("[clienttest] {}", name)));
    }

    private static void lobby(LobbyActionC2S.Action action, int value) {
        step(5, () -> {
            LOGGER.info("[clienttest] lobby {} {}", action, value);
            LobbyClient.send(action, value);
        });
    }

    private static void command(int delay, String command) {
        step(delay, () -> {
            LOGGER.info("[clienttest] /{}", command);
            Minecraft.getInstance().player.connection.sendCommand(command);
        });
    }

    private static void shot(int delay, String name) {
        step(delay, () -> {
            Minecraft client = Minecraft.getInstance();
            Screenshot.grab(client.gameDirectory, "clienttest_" + name + ".png", client.getMainRenderTarget(),
                    message -> LOGGER.info("[clienttest] screenshot {}: {}", name, message.getString()));
        });
    }

    private static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getInstance();
        if (!client.options.pauseOnLostFocus && client.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
            client.setScreen(null);
        }
        if (client.player == null || client.level == null || index >= STEPS.size()) return;
        Step step = STEPS.get(index);
        if (++wait < step.delayTicks()) return;
        if (!step.ready().getAsBoolean()) {
            if (wait < step.delayTicks() + step.timeoutTicks()) return;
            LOGGER.warn("[clienttest] menunggu \"{}\" kelamaan, lanjut", step.name());
        }
        wait = 0;
        index++;
        try {
            step.action().run();
        } catch (RuntimeException e) {
            LOGGER.error("[clienttest] langkah {} gagal", index, e);
        }
    }
}
