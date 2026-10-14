package com.backrooms.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Uji klien otomatis khusus development ({@code gradlew runClient -Pclienttest=true}):
 * masuk ke world uji, berkunjung ke tiap level, menyalakan/mematikan senter
 * dan efek kamera, mengambil screenshot, lalu menutup game. Tidak aktif di
 * permainan biasa.
 */
public final class ClientSelfTest {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<Step> STEPS = new ArrayList<>();
    private static int index;
    private static int wait;

    private record Step(int delayTicks, Runnable action) {}

    private ClientSelfTest() {}

    public static void init() {
        if (!Boolean.getBoolean("backrooms.clienttest")) return;
        command(200, "backroom gotolevel0");
        step(300, ClientSelfTest::diagnose);
        shot(5, "level0_effect");
        step(5, () -> ClientConfig.set(ClientConfig.ENABLE_SHADOWS, false));
        shot(60, "level0_noshadows");
        step(5, () -> ClientConfig.set(ClientConfig.ENABLE_SHADOWS, true));
        step(5, () -> Flashlight.press(Minecraft.getInstance().player, true));
        shot(60, "level0_flashlight");
        step(1, ClientSelfTest::diagnose);
        command(5, "backroom camera off");
        shot(60, "level0_noeffect");
        command(5, "backroom camera on");
        command(5, "backroom gotolevel1");
        shot(200, "level1_effect");
        command(5, "backroom gotopoolrooms");
        shot(200, "poolrooms_effect");
        step(5, () -> Flashlight.press(Minecraft.getInstance().player, true));
        shot(40, "poolrooms_flashlight_refused");
        command(5, "backroom gotoworld");
        shot(150, "overworld_effect");
        step(20, () -> {
            LOGGER.info("[clienttest] selesai");
            Minecraft.getInstance().stop();
        });
        MinecraftForge.EVENT_BUS.addListener(ClientSelfTest::onTick);
    }

    private static void diagnose() {
        var lights = foundry.veil.api.client.render.VeilRenderSystem.renderer().getDeferredRenderer();
        LOGGER.info("[clienttest] deferred enabled={} active={} state={} points={} areas={} vanillaLight={} ao={}",
                lights.isEnabled(), lights.isActive(), lights.getRendererState(),
                lights.getLightRenderer().getLights(foundry.veil.api.client.registry.LightTypeRegistry.POINT.get()).size(),
                lights.getLightRenderer().getLights(foundry.veil.api.client.registry.LightTypeRegistry.AREA.get()).size(),
                lights.getLightRenderer().isVanillaLightEnabled(), lights.getLightRenderer().isAmbientOcclusionEnabled());
    }

    private static void step(int delay, Runnable action) {
        STEPS.add(new Step(delay, action));
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
        wait = 0;
        index++;
        try {
            step.action().run();
        } catch (RuntimeException e) {
            LOGGER.error("[clienttest] langkah {} gagal", index, e);
        }
    }
}
