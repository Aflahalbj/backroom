package com.backrooms.client;

import com.backrooms.network.FlashlightC2S;
import com.backrooms.network.ModNetwork;
import com.backrooms.sound.ModSounds;
import com.backrooms.world.BackroomsLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Status senter tiap pemain di sisi klien, dan logika tombol senter dari
 * {@code ClientWrapper.tickClientPlayerComponent} aslinya:
 *
 * <ul>
 *   <li>tekan tombol (bawaan B) &rarr; bunyi klik (volume 0.5);</li>
 *   <li>di level yang mengizinkan senter &rarr; nyala/mati dan dikirim ke server
 *       supaya pemain lain ikut melihat cahayanya;</li>
 *   <li>di Poolrooms (senter "basah") &rarr; tetap mati dan pesan
 *       {@code spb-revamped.flashlight.wet1/wet2} muncul di action bar.</li>
 * </ul>
 *
 * Seperti aslinya, tombol hanya bekerja di dimensi yang dikenal mod
 * (overworld, nether, end, dan level backrooms).
 */
public final class Flashlight {

    private static final Map<UUID, Boolean> ON = new HashMap<>();

    private Flashlight() {}

    public static boolean isOn(Player player) {
        return ON.getOrDefault(player.getUUID(), false);
    }

    public static void setOn(UUID player, boolean on) {
        ON.put(player, on);
    }

    public static void clear() {
        ON.clear();
    }

    /** Level asli punya {@code allowsTorch()}: hanya Poolrooms yang melarang. */
    static boolean allowsTorch(ResourceKey<Level> dimension) {
        return dimension != BackroomsLevel.POOLROOMS.dimension();
    }

    static Component notAllowedMessage() {
        return Component.translatable("spb-revamped.flashlight.wet1")
                .append(Component.translatable("spb-revamped.flashlight.wet2").withStyle(ChatFormatting.RED));
    }

    private static boolean knownLevel(ResourceKey<Level> dimension) {
        return dimension == Level.OVERWORLD || dimension == Level.NETHER || dimension == Level.END
                || BackroomsLevel.byDimension(dimension) != null;
    }

    /** Dipanggil tiap akhir tick klien. */
    public static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) return;
        ResourceKey<Level> dimension = player.level().dimension();
        if (!knownLevel(dimension)) return;

        if (ModKeyBinds.TOGGLE_FLASHLIGHT.consumeClick()) {
            press(player, !isOn(player));
        }
        if (!allowsTorch(dimension)) {
            if (isOn(player)) {
                ModNetwork.CHANNEL.sendToServer(new FlashlightC2S(false));
            }
            setOn(player.getUUID(), false);
        }
    }

    /** Sama dengan menekan tombol senter; {@code target} = status yang diminta. */
    public static void press(LocalPlayer player, boolean target) {
        player.playSound(ModSounds.event("flashlight_click"), 0.5F, 1.0F);
        if (allowsTorch(player.level().dimension())) {
            setOn(player.getUUID(), target);
            CameraEffect.disableFlashlightHint();
            if (!player.isSpectator()) {
                ModNetwork.CHANNEL.sendToServer(new FlashlightC2S(target));
            }
        } else {
            setOn(player.getUUID(), false);
            player.displayClientMessage(notAllowedMessage(), true);
        }
    }
}
