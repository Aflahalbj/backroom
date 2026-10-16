package com.backrooms.client.lobby;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sepuluh gerakan hero cutscene perkenalan (animasinya dari
 * {@code tools/drop_moves_*.py}), cara kamera membingkai masing-masing, dan
 * pembagiannya ke anggota room: tidak ada yang sama dalam satu room selama
 * gerakannya cukup.
 *
 * <p>Pose kuncinya dari riset referensi: kartu operator (Valorant, R6, CoD,
 * Apex), poster film action, posisi senapan militer (port arms, low ready,
 * press check, compressed ready) dan posisi terjun bebas (sit-fly, mantis,
 * back-fly).
 */
final class DropMoves {

    /**
     * Satu gerakan. Kamera: jarak, sudut dari depan badan (+ = sisi kanan
     * karakter), elevasi (- = dari bawah), tinggi titik tatap dari kaki, roll;
     * masing-masing dari awal ke akhir gerakan.
     */
    record Move(String animation, String title, boolean akimbo,
                double dist, double distEnd, double az, double azEnd, double el, double elEnd,
                double height, float roll, float rollEnd) {}

    static final Map<String, Move> MOVES = new LinkedHashMap<>();

    private static void add(Move move) {
        MOVES.put(move.animation(), move);
    }

    static {
        // Sepuluh siluet berbeda; sudut (karakter diputar, kamera diam) dipilih di lab pose.
        add(new Move("drop_port", "PORT ARMS", false, 3.3, 2.9, 28, 22, -12, -8, 0.95, 0, 0));
        add(new Move("drop_duduk", "SIT-FLY BIDIK", false, 3.3, 2.9, -30, -24, -12, -8, 0.8, 0, 0));
        add(new Move("drop_kepala", "HEAD-DOWN", false, 3.6, 3.2, 12, 6, -6, -4, 0.45, 0, 0));
        add(new Move("drop_mantis", "MANTIS", false, 3.5, 3.1, 22, 15, -30, -26, 0.75, 0, 0));
        add(new Move("drop_telentang", "BACK-FLY", false, 3.6, 3.2, -30, -24, 60, 56, 0.75, 0, 0));
        add(new Move("drop_miring", "SIDE-FLY", false, 3.4, 3.0, -18, -10, -10, -8, 0.85, 0, 0));
        add(new Move("drop_tukik", "MENUKIK", false, 3.8, 3.4, 22, 16, -30, -26, 0.5, 0, 0));
        add(new Move("drop_angkat", "ANGKAT SENJATA", false, 3.8, 3.4, -25, -19, -8, -4, 1.2, 0, 0));
        add(new Move("drop_bintang", "BINTANG", false, 3.6, 3.2, 0, 10, -10, -8, 0.95, 0, 0));
        add(new Move("drop_ringkuk", "MERINGKUK", false, 3.0, 2.7, -20, -12, -10, -8, 0.8, 0, 0));
    }

    /** Semua gerakan (diacak per room oleh {@link #assign}). */
    private static final String[] ORDER = {
            "drop_port", "drop_kepala", "drop_duduk", "drop_bintang", "drop_mantis",
            "drop_angkat", "drop_miring", "drop_telentang", "drop_ringkuk", "drop_tukik"};

    private DropMoves() {}

    static Move get(String animation) {
        return MOVES.getOrDefault(animation, MOVES.get(ORDER[0]));
    }

    /**
     * Bagikan gerakan ke anggota secara acak: tidak ada yang kembar sampai
     * semua gerakan terpakai (lebih dari sepuluh anggota, mis. 10 pemain + 2
     * host, baru ada yang sama). Acakannya hanya bergantung pada room dan
     * urutan anggota, jadi semua klien melihat pembagian yang sama.
     */
    static List<String> assign(List<java.util.UUID> lineup, long seed) {
        long mixed = seed * 0x9E3779B97F4A7C15L;
        for (java.util.UUID id : lineup) {
            mixed = mixed * 31L + id.getMostSignificantBits();
            mixed = mixed * 31L + id.getLeastSignificantBits();
        }
        java.util.Random random = new java.util.Random(mixed);
        List<String> out = new ArrayList<>();
        List<String> bag = new ArrayList<>();
        for (int i = 0; i < lineup.size(); i++) {
            if (bag.isEmpty()) {
                bag.addAll(java.util.Arrays.asList(ORDER));
                java.util.Collections.shuffle(bag, random);
            }
            out.add(bag.remove(bag.size() - 1));
        }
        return out;
    }
}
