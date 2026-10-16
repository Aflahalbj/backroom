package com.backrooms.client.skinwalker;

import com.backrooms.world.events.LightState;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Salinan klien dari {@code WorldEvents} salinan level tempat pemain berada
 * (di aslinya komponen dunia CCA yang disinkronkan): kondisi lampu, waktu
 * Poolrooms, dan korban Skinwalker yang sedang aktif.
 */
public final class ClientWorldEvents {

    private static LightState lightState = LightState.ON;
    private static UUID activeSkinwalkerTarget;
    private static float timeOfDay = 0.0F;
    private static boolean sunsetTransitioning = false;
    private static boolean skinwalkerNight = false;

    private ClientWorldEvents() {}

    public static void read(CompoundTag tag) {
        lightState = tag.contains("lightState") ? LightState.valueOf(tag.getString("lightState")) : LightState.ON;
        activeSkinwalkerTarget = tag.hasUUID("activeSkinwalkerTarget") ? tag.getUUID("activeSkinwalkerTarget") : null;
        timeOfDay = tag.getFloat("timeOfDay");
        sunsetTransitioning = tag.getBoolean("sunsetTransitioning");
        skinwalkerNight = tag.getBoolean("skinwalkerNight");
    }

    public static void clear() {
        lightState = LightState.ON;
        activeSkinwalkerTarget = null;
        timeOfDay = 0.0F;
        sunsetTransitioning = false;
        skinwalkerNight = false;
    }

    public static LightState lightState() {
        return lightState;
    }

    /** {@code PoolroomsBackroomsLevel.getTimeOfDay}. */
    public static float timeOfDay() {
        return timeOfDay;
    }

    public static boolean isSunsetTransitioning() {
        return sunsetTransitioning;
    }

    /** Tambahan port ini: Poolrooms malam selama lampu Skinwalker mati. */
    public static boolean isSkinwalkerNight() {
        return skinwalkerNight;
    }

    /** {@code PoolroomsBackroomsLevel.isNoon}. */
    public static boolean isNoon() {
        return (double) timeOfDay != 0.25D && (double) timeOfDay != 0.75D;
    }

    public static UUID activeSkinwalkerTarget() {
        return activeSkinwalkerTarget;
    }

    /** {@code WorldEvents.getActiveSkinwalkerTarget()} di klien. */
    public static Player getActiveSkinwalkerTarget(Level level) {
        return activeSkinwalkerTarget != null ? level.getPlayerByUUID(activeSkinwalkerTarget) : null;
    }
}
