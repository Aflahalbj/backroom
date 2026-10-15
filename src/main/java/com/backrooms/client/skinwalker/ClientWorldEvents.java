package com.backrooms.client.skinwalker;

import com.backrooms.world.events.LightState;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Salinan klien dari {@code WorldEvents} salinan level tempat pemain berada
 * (di aslinya komponen dunia CCA yang disinkronkan): kondisi lampu dan korban
 * Skinwalker yang sedang aktif.
 */
public final class ClientWorldEvents {

    private static LightState lightState = LightState.ON;
    private static UUID activeSkinwalkerTarget;

    private ClientWorldEvents() {}

    public static void read(CompoundTag tag) {
        lightState = tag.contains("lightState") ? LightState.valueOf(tag.getString("lightState")) : LightState.ON;
        activeSkinwalkerTarget = tag.hasUUID("activeSkinwalkerTarget") ? tag.getUUID("activeSkinwalkerTarget") : null;
    }

    public static void clear() {
        lightState = LightState.ON;
        activeSkinwalkerTarget = null;
    }

    public static LightState lightState() {
        return lightState;
    }

    public static UUID activeSkinwalkerTarget() {
        return activeSkinwalkerTarget;
    }

    /** {@code WorldEvents.getActiveSkinwalkerTarget()} di klien. */
    public static Player getActiveSkinwalkerTarget(Level level) {
        return activeSkinwalkerTarget != null ? level.getPlayerByUUID(activeSkinwalkerTarget) : null;
    }
}
