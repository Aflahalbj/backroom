package com.backrooms.client;

import com.backrooms.client.lobby.IntroCutscene;
import com.backrooms.client.lobby.LobbyClient;
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
