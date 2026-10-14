package com.backrooms.client.lobby;

import com.backrooms.network.LobbyActionC2S;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Pratinjau animasi lobby khusus development ({@code gradlew runClient -Panimtest=true}).
 * Game tetap terbuka; perintah dibaca dari {@code run/animtest/command.txt}
 * setiap kali file itu berubah, satu perintah per baris:
 * <pre>
 *   pose N            ganti pose (indeks LobbyPose)
 *   weapon N          ganti senjata (indeks LobbyWeapon)
 *   bg N              ganti latar lobby (indeks BackroomsLevel)
 *   reload            muat ulang sumber daya (animasi JSON) lalu mulai ulang animasi
 *   turn DEG          putar badan karakter (lihat dari samping)
 *   shots NAME t1,t2  mulai ulang animasi, screenshot di tick t1, t2, ... sejak mulai
 *   wait T            tunggu T tick
 *   markers           tampilkan/sembunyikan sumbu kalibrasi moncong
 *   muzzle X Y Z      geser letak moncong senjata yang dipakai (ruang tangan, blok)
 *   fire              satu tembakan sekarang
 *   holdflash         tahan/lepas api moncong (untuk dilihat)
 *   quit
 * </pre>
 * Selesai menjalankan semua perintah, {@code run/animtest/done.txt} ditulis.
 */
public final class AnimDev {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Path DIR = Path.of("animtest");
    private static final Path COMMAND = DIR.resolve("command.txt");
    private static final Path DONE = DIR.resolve("done.txt");

    private static long lastModified;
    private static final Deque<String> QUEUE = new ArrayDeque<>();
    private static int waitTicks;
    private static String shotName;
    private static int[] shotTicks;
    private static int shotIndex;
    private static int shotClock;
    private static int batch;

    private AnimDev() {}

    public static void init() {
        if (!Boolean.getBoolean("backrooms.animtest")) return;
        try {
            Files.createDirectories(DIR);
        } catch (IOException e) {
            LOGGER.error("[animtest] tidak bisa membuat folder", e);
        }
        MinecraftForge.EVENT_BUS.addListener(AnimDev::onTick);
        LOGGER.info("[animtest] siap, tulis perintah ke {}", COMMAND.toAbsolutePath());
    }

    private static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !LobbyClient.isReady()) return;
        poll();
        if (shotTicks != null) {
            shotClock++;
            while (shotIndex < shotTicks.length && shotClock >= shotTicks[shotIndex]) {
                String name = shotName + "_" + String.format("%03d", shotTicks[shotIndex]);
                Screenshot.grab(client.gameDirectory, "anim_" + name + ".png", client.getMainRenderTarget(), m -> {});
                shotIndex++;
            }
            if (shotIndex >= shotTicks.length) {
                shotTicks = null;
                if (QUEUE.isEmpty() && waitTicks == 0) markDone();
            }
            return;
        }
        if (client.getOverlay() != null) {
            // Layar muat ulang sumber daya masih tampil.
            waitTicks = Math.max(waitTicks, 20);
            return;
        }
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        if (QUEUE.isEmpty()) return;
        run(client, QUEUE.poll());
        if (QUEUE.isEmpty() && shotTicks == null && waitTicks == 0) markDone();
    }

    private static void poll() {
        try {
            if (!Files.exists(COMMAND)) return;
            long modified = Files.getLastModifiedTime(COMMAND).toMillis();
            if (modified == lastModified) return;
            lastModified = modified;
            List<String> lines = Files.readAllLines(COMMAND, StandardCharsets.UTF_8);
            Files.deleteIfExists(DONE);
            batch++;
            for (String line : lines) {
                if (!line.isBlank() && !line.startsWith("#")) QUEUE.add(line.trim());
            }
            LOGGER.info("[animtest] batch {}: {} perintah", batch, QUEUE.size());
        } catch (IOException e) {
            LOGGER.error("[animtest] gagal membaca perintah", e);
        }
    }

    private static void run(Minecraft client, String line) {
        String[] parts = line.split("\\s+");
        LOGGER.info("[animtest] {}", line);
        switch (parts[0]) {
            case "pose" -> LobbyClient.send(LobbyActionC2S.Action.SET_POSE, Integer.parseInt(parts[1]));
            case "weapon" -> LobbyClient.send(LobbyActionC2S.Action.SET_WEAPON, Integer.parseInt(parts[1]));
            case "bg" -> LobbyClient.send(LobbyActionC2S.Action.SET_BACKGROUND, Integer.parseInt(parts[1]));
            case "reload" -> {
                client.reloadResourcePacks().thenRun(LobbyAnimator::restartAll);
                waitTicks = 40;
            }
            case "turn" -> LobbyClient.setDragYaw(Float.parseFloat(parts[1]));
            case "wait" -> waitTicks = Integer.parseInt(parts[1]);
            case "shots" -> {
                LobbyAnimator.restartAll();
                shotName = parts[1];
                String[] ticks = parts[2].split(",");
                shotTicks = new int[ticks.length];
                for (int i = 0; i < ticks.length; i++) shotTicks[i] = Integer.parseInt(ticks[i]) + 1;
                shotIndex = 0;
                shotClock = 0;
            }
            case "markers" -> LobbyFx.toggleMarkers();
            case "holdflash" -> LobbyFx.toggleHoldFlash();
            case "muzzle" -> LobbyFx.setMuzzle(LobbyClient.weapon(), Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
            case "fire" -> {
                if (client.player != null) LobbyFx.fire(client.player);
            }
            case "quit" -> client.stop();
            default -> LOGGER.warn("[animtest] perintah tidak dikenal: {}", line);
        }
    }

    private static void markDone() {
        try {
            Files.writeString(DONE, "batch " + batch + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("[animtest] gagal menulis done.txt", e);
        }
    }
}
