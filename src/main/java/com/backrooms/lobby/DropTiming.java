package com.backrooms.lobby;

/**
 * Jadwal cutscene perkenalan room (lantai lobby bolong, semua anggota jatuh,
 * lalu diperkenalkan satu per satu). Dipakai klien untuk memutar cutscene dan
 * server untuk tahu kapan memindahkan pemain ke Backrooms, jadi lamanya sama
 * di kedua sisi. Semua dalam milidetik sejak cutscene mulai.
 *
 * <pre>
 *   0 .. TREMOR           tenang, semua masih berdiri di barisan
 *   TREMOR .. BREAK       gempa: lantai retak merambat, debu jatuh dari plafon,
 *                         lampu berkedip, semua goyah (antisipasi)
 *   BREAK .. DROP         lantai runtuh, semua "menggantung" sesaat
 *   DROP .. INTRO_START   jatuh menembus lubang, kamera menyusul ke bawah
 *   tiap anggota: slot    jatuh masuk bingkai dari atas, gerak lambat, kartu nama
 *   sesudahnya: FINALE    semua dalam satu bingkai, lalu menyelam ke gelap
 * </pre>
 */
public final class DropTiming {

    /** Getaran pertama (tick 12 animasi {@code drop_jatuh}). */
    public static final long TREMOR = 600L;
    /** Lantai runtuh (tick 52). */
    public static final long BREAK = 2600L;
    /** Selesai menggantung, mulai jatuh (tick 60). */
    public static final long DROP = 3000L;
    /** Kamera sudah di jurang; perkenalan pertama dimulai (anggota pertama jatuh masuk bingkai). */
    public static final long INTRO_START = 4850L;
    /** Bingkai bersama + menyelam ke gelap. */
    public static final long FINALE = 5000L;
    /** Bagian akhir yang hitam penuh (menunggu dipindahkan server). */
    public static final long BLACK_TAIL = 350L;

    private DropTiming() {}

    /**
     * Lama giliran satu anggota. Tetap (tidak tergantung jumlah anggota)
     * karena gerak lambat animasi aksinya sudah "dipanggang" dengan jadwal ini
     * (tools/lobby_anims.py: SLOT_MS, SLOW, SLOW_IN, SLOW_OUT harus sama).
     */
    public static final long SLOT = 3200L;
    /** Kecepatan waktu saat bullet time. */
    public static final float SLOW = 0.3F;
    /** Waktu melambat antara dua titik ini (ms sejak giliran mulai)... */
    public static final long SLOW_IN_START = 500L;
    public static final long SLOW_IN_END = 800L;
    /** ...dan kembali normal antara dua titik ini. */
    public static final long SLOW_OUT_START = 2500L;
    public static final long SLOW_OUT_END = 2900L;

    public static long slot(int members) {
        return SLOT;
    }

    /** Kecepatan waktu {@code u} ms sejak giliran seorang anggota dimulai (sama dengan speed_at di generator). */
    public static float slotSpeed(long u) {
        if (u < SLOW_IN_START) return 1.0F;
        if (u < SLOW_IN_END) return 1.0F + (SLOW - 1.0F) * smooth((u - SLOW_IN_START) / (float) (SLOW_IN_END - SLOW_IN_START));
        if (u < SLOW_OUT_START) return SLOW;
        if (u < SLOW_OUT_END) return SLOW + (1.0F - SLOW) * smooth((u - SLOW_OUT_START) / (float) (SLOW_OUT_END - SLOW_OUT_START));
        return 1.0F;
    }

    private static float smooth(float x) {
        x = Math.max(0.0F, Math.min(1.0F, x));
        return x * x * (3.0F - 2.0F * x);
    }

    public static long finaleStart(int members) {
        return INTRO_START + slot(members) * Math.max(1, members);
    }

    public static long total(int members) {
        return finaleStart(members) + FINALE;
    }

    /** Tick server sampai pemain dipindahkan; sedikit lebih lama supaya klien sudah hitam. */
    public static int serverTicks(int members) {
        return (int) ((total(members) + 49L) / 50L) + 4;
    }
}
