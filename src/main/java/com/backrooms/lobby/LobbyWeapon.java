package com.backrooms.lobby;

/**
 * Senjata yang dipegang karakter di lobby (dipilih di Penampilan). Senjatanya
 * dari TACZ (Timeless and Classics Zero); tanpa TACZ, tangan karakter kosong.
 */
public enum LobbyWeapon {
    NONE("Tanpa senjata", null, null),
    AK47("AK-47", "tacz:ak47", "tacz:ak47/ak47_shoot_3p",
            "tacz:ak47/ak47_reload_magout", "tacz:ak47/ak47_reload_magin", "tacz:ak47/ak47_reload_mvt2"),
    M4A1("M4A1", "tacz:m4a1", "tacz:m4a1/m4a1_shoot_3p",
            "tacz:m4a1/m4a1_reload_tactical_magout", "tacz:m4a1/m4a1_reload_tactical_magin", "tacz:m4a1/m4a1_reload_tactical_end"),
    SCAR_H("SCAR-H", "tacz:scar_h", "tacz:scar_h/scar_h_shoot_3p",
            "tacz:scar_h/p05_ar_schotel_reload_empty_magout", "tacz:scar_h/p05_ar_schotel_branched_reload_magin",
            "tacz:scar_h/p05_ar_schotel_reload_end"),
    HK416("HK416D", "tacz:hk416d", "tacz:hk416d/hk416d_shoot_3p",
            "tacz:hk416d/hk416d_reload_fast_magout", "tacz:hk416d/hk416d_reload_magin_01", "tacz:hk416d/hk416d_reload_end"),
    P90("P90", "tacz:p90", "tacz:p90/p90_shoot_3p",
            "tacz:p90/p90_reload_magout", "tacz:p90/p90_reload_magin", "tacz:p90/p90_reload_end"),
    MP5("MP5", "tacz:hk_mp5a5", "tacz:hk_mp5a5/hk_mp5a5_shoot_3p",
            "tacz:hk_mp5a5/hk_mp5a5_reload_magout", "tacz:hk_mp5a5/hk_mp5a5_reload_magin", "tacz:hk_mp5a5/hk_mp5a5_reload_end"),
    M870("M870", "tacz:m870", "tacz:m870/m870_shoot_3p",
            "tacz:m870/m870_reload_start_twist", "tacz:m870/m870_reload_loop_shellin_01", "tacz:m870/m870_reload_end_turnover"),
    AWP("AWP", "tacz:ai_awp", "tacz:ai_awp/awp_shoot_3p",
            "tacz:ai_awp/awp_reload_magout", "tacz:ai_awp/awp_reload_magin", "tacz:ai_awp/awp_reload_end");

    public static final LobbyWeapon DEFAULT = AK47;

    private final String displayName;
    private final String gunId;
    private final String shootSound;
    private final String[] reloadSounds;

    LobbyWeapon(String displayName, String gunId, String shootSound, String... reloadSounds) {
        this.displayName = displayName;
        this.gunId = gunId;
        this.shootSound = shootSound;
        this.reloadSounds = reloadSounds;
    }

    public String displayName() {
        return displayName;
    }

    /** ID senjata TACZ ({@code GunId} di NBT item), atau {@code null} untuk tangan kosong. */
    public String gunId() {
        return gunId;
    }

    /** Suara tembakan orang ketiga TACZ, atau {@code null}. */
    public String shootSound() {
        return shootSound;
    }

    /**
     * Suara tahap reload TACZ: {@code "magout"}, {@code "magin"}, atau
     * {@code "end"} (kokang); {@code null} kalau tidak ada.
     */
    public String reloadSound(String stage) {
        int i = switch (stage) {
            case "magout" -> 0;
            case "magin" -> 1;
            case "end" -> 2;
            default -> -1;
        };
        return i >= 0 && i < reloadSounds.length ? reloadSounds[i] : null;
    }

    public static LobbyWeapon byId(int id) {
        LobbyWeapon[] values = values();
        return id >= 0 && id < values.length ? values[id] : DEFAULT;
    }
}
