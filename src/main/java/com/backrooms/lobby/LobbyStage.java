package com.backrooms.lobby;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Tempat berdiri karakter di set lobby: {@code pos} adalah blok kaki pemain
 * paling depan di tengah, {@code facing} arah dari barisan pemain ke kamera,
 * {@code reach} seberapa jauh (blok) kamera bisa mundur tanpa terhalang.
 * Kamera sendiri dihitung klien dari sini (jaraknya ikut jumlah pemain).
 */
public record LobbyStage(BlockPos pos, Direction facing, int reach) {

    /** Jarak antar pemain dalam satu baris. */
    public static final double SPACING = 1.4D;
    /** Jarak antar pemain di baris yang penuh, supaya tetap di dalam lebar panggung (5 blok). */
    public static final double FULL_ROW_SPACING = 1.2D;
    /** Seberapa jauh baris belakang dari baris depan. */
    public static final double BACK_ROW = 1.5D;
    /** Satu baris berisi paling banyak sekian pemain (10 pemain = dua baris). */
    public static final int ROW_SIZE = 5;

    /**
     * Posisi kaki pemain ke-{@code index} (urutan room) dari
     * {@code count} pemain: 5 pertama di baris depan, sisanya di belakang,
     * masing-masing diisi dari tengah ke luar.
     */
    public Vec3 slot(int index, int count) {
        int row = index / ROW_SIZE;
        int inRow = Math.min(ROW_SIZE, count - row * ROW_SIZE);
        double lateral = centerOut(index % ROW_SIZE, inRow) * (inRow >= ROW_SIZE ? FULL_ROW_SPACING : SPACING);
        Direction right = facing.getClockWise();
        double back = row * BACK_ROW;
        return Vec3.atBottomCenterOf(pos)
                .add(right.getStepX() * lateral, 0.0D, right.getStepZ() * lateral)
                .add(-facing.getStepX() * back, 0.0D, -facing.getStepZ() * back);
    }

    /** Arah hadap pemain (menghadap kamera). */
    public float playerYaw() {
        return facing.toYRot();
    }

    /** Posisi ke-{@code k} dari {@code n}, dimulai dari tengah: 0, +1, -1, +2, ... (dalam satuan jarak). */
    static double centerOut(int k, int n) {
        Double[] spots = new Double[n];
        for (int i = 0; i < n; i++) spots[i] = i - (n - 1) / 2.0D;
        Arrays.sort(spots, Comparator.comparingDouble((Double d) -> Math.abs(d)).thenComparing(Comparator.reverseOrder()));
        return spots[Math.min(k, n - 1)];
    }
}
