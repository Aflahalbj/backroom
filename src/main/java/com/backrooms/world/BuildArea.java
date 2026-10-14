package com.backrooms.world;

import com.backrooms.config.BackroomsConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Satu salinan bangunan sebuah level di dimensinya (atau di dimensi lobby).
 *
 * <p>Setiap dimensi bisa berisi beberapa salinan, berjajar di sumbu X dengan
 * jarak {@link #SPACING} blok:
 * <ul>
 *   <li>slot {@code 0}: bangunan utama (dipakai command biasa, tengahnya di 0,0);</li>
 *   <li>slot {@link #LOBBY_SLOT}: set kecil 80x80 untuk latar lobby -- bukan di
 *       dimensi level-nya, tapi di dimensi {@link BackroomsLevel#LOBBY}, ketiganya
 *       berjajar sejauh {@link #LOBBY_SPACING};</li>
 *   <li>slot {@code 1, 2, ...}: Backrooms milik masing-masing room.</li>
 * </ul>
 *
 * <p>Generator tetap bekerja di koordinat asli mod; {@link #offsetX()} dan
 * {@link #offsetZ()} menggeser hasilnya ke posisi slot ini di dunia. Karena
 * generatornya tidak memakai seed, tiap salinan punya susunan sendiri.
 */
public record BuildArea(BackroomsLevel which, int slot, int radius) {

    /** Jarak antar slot; muat area terbesar ({@code regionRadius} 6 = 1040 blok) plus jeda. */
    public static final int SPACING = 2048;
    public static final int LOBBY_SLOT = -1;
    /** Jarak antar set latar di dimensi lobby. */
    public static final int LOBBY_SPACING = 512;

    public static BuildArea main(BackroomsLevel which) {
        return new BuildArea(which, 0, BackroomsConfig.REGION_RADIUS.get());
    }

    public static BuildArea lobby(BackroomsLevel which) {
        return new BuildArea(which, LOBBY_SLOT, 0);
    }

    public static BuildArea room(BackroomsLevel which, int slot) {
        return new BuildArea(which, slot, BackroomsConfig.REGION_RADIUS.get());
    }

    public boolean isLobby() {
        return slot == LOBBY_SLOT;
    }

    /** Dimensi tempat area ini dibangun. */
    public ResourceKey<Level> dimension() {
        return isLobby() ? BackroomsLevel.LOBBY : which.dimension();
    }

    /** Kunci di {@link BuildState} dimensinya (set lobby berbagi satu dimensi: -1, -2, -3). */
    public int buildKey() {
        return isLobby() ? LOBBY_SLOT - which.ordinal() : slot;
    }

    /** Set latar mana yang ada di koordinat X ini (di dimensi lobby). */
    public static BackroomsLevel lobbyAt(double x) {
        BackroomsLevel[] levels = BackroomsLevel.values();
        int index = (int) Math.round(x / LOBBY_SPACING);
        return levels[Math.max(0, Math.min(levels.length - 1, index))];
    }

    public String describe() {
        if (slot == 0) return which.displayName();
        if (slot == LOBBY_SLOT) return which.displayName() + " (lobby)";
        return which.displayName() + " (room slot " + slot + ")";
    }

    // ------------------------------------------- area, koordinat generator

    /**
     * Region maze ada di setiap chunk kelipatan 5 dan masing-masing mengisi 2
     * chunk ke kiri sampai 2 chunk ke kanan, jadi area {@code [-5R-2, 5R+2]}
     * chunk berisi tepat {@code (2R+1)^2} region utuh.
     */
    private int halfSize() {
        return 5 * radius + 2;
    }

    public int minChunkX() { return -halfSize(); }

    public int maxChunkX() { return halfSize(); }

    public int minChunkZ() { return -halfSize(); }

    public int maxChunkZ() { return halfSize(); }

    // ----------------------------------------------- pergeseran ke dunia

    /** Geser X supaya tengah area tepat di 0 (lebar genap: -w/2 .. w/2-1), lalu ke slot-nya. */
    public int offsetX() {
        int center = -((minChunkX() * 16 + maxChunkX() * 16 + 16) / 2);
        return center + (isLobby() ? which.ordinal() * LOBBY_SPACING : slot * SPACING);
    }

    public int offsetZ() {
        return -((minChunkZ() * 16 + maxChunkZ() * 16 + 16) / 2);
    }

    public int worldMinX() { return minChunkX() * 16 + offsetX(); }

    public int worldMaxX() { return maxChunkX() * 16 + 15 + offsetX(); }

    public int worldMinZ() { return minChunkZ() * 16 + offsetZ(); }

    public int worldMaxZ() { return maxChunkZ() * 16 + 15 + offsetZ(); }

    /** Titik di koordinat asli mod, digeser ke slot ini. */
    public Vec3 toWorld(Vec3 original) {
        return original.add(offsetX(), 0.0D, offsetZ());
    }

    /** Titik spawn aslinya, digeser bersama bangunannya. */
    public Vec3 worldSpawn() {
        return toWorld(which.spawn());
    }
}
