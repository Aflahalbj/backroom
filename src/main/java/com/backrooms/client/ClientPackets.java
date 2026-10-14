package com.backrooms.client;

import com.backrooms.network.ClientActionS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/** Penanganan paket di klien (dipisah supaya server tidak memuat kelas klien). */
public final class ClientPackets {

    private ClientPackets() {}

    public static void flashlight(UUID player, boolean on) {
        Minecraft client = Minecraft.getInstance();
        // Status pemain ini sendiri dipegang klien (seperti aslinya); hanya
        // pemain lain yang diambil dari server.
        if (client.player != null && client.player.getUUID().equals(player)) return;
        Flashlight.setOn(player, on);
    }

    public static void action(ClientActionS2C.Action action, boolean value) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        switch (action) {
            case CAMERA_ALL -> {
                ClientConfig.set(ClientConfig.ENABLE_VHS_EFFECT, value);
                ClientConfig.set(ClientConfig.ENABLE_VHS_EFFECT_IN_THE_BACKROOMS, value);
                client.player.displayClientMessage(Component.literal("Efek kamera: " + (value ? "ON" : "OFF")), true);
            }
            case CAMERA_BACKROOMS -> {
                ClientConfig.set(ClientConfig.ENABLE_VHS_EFFECT_IN_THE_BACKROOMS, value);
                client.player.displayClientMessage(Component.literal("Efek kamera di backrooms: " + (value ? "ON" : "OFF")), true);
            }
            case CAMERA_OUTSIDE -> {
                ClientConfig.set(ClientConfig.ENABLE_VHS_EFFECT, value);
                client.player.displayClientMessage(Component.literal("Efek kamera di luar backrooms: " + (value ? "ON" : "OFF")), true);
            }
            case FLASHLIGHT -> Flashlight.press(client.player, value);
            case FLASHLIGHT_TOGGLE -> Flashlight.press(client.player, !Flashlight.isOn(client.player));
        }
    }
}
