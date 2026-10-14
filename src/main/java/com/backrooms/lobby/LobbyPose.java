package com.backrooms.lobby;

/**
 * Pose karakter di lobby (dipilih di Penampilan, terlihat juga oleh anggota
 * room). Tiap pose adalah animasi Player Animator yang berulang, di
 * {@code assets/backrooms/player_animation/lobby_<nama>.json}; tembakan di
 * dalamnya memicu api moncong dan suara (lihat {@code LobbyFx}).
 */
public enum LobbyPose {
    SIAGA("Siaga", "lobby_siaga"),
    BIDIK("Bidik", "lobby_bidik"),
    RELOAD("Reload", "lobby_reload"),
    BERLUTUT("Berlutut", "lobby_berlutut"),
    GULING("Guling", "lobby_guling"),
    ANCAM("Ancam", "lobby_ancam"),
    /** Bergantian memutar semua pose lain, satu putaran penuh masing-masing. */
    AUTO("Auto", null);

    /** Pose pemain yang belum pernah memilih. */
    public static final LobbyPose DEFAULT = AUTO;
    /** Urutan pose dalam mode {@link #AUTO}. */
    public static final LobbyPose[] AUTO_SEQUENCE = {SIAGA, BIDIK, RELOAD, BERLUTUT, GULING, ANCAM};

    private final String displayName;
    private final String animation;

    LobbyPose(String displayName, String animation) {
        this.displayName = displayName;
        this.animation = animation;
    }

    public String displayName() {
        return displayName;
    }

    /** Nama animasi Player Animator (namespace {@code backrooms}); {@code null} untuk {@link #AUTO}. */
    public String animation() {
        return animation;
    }

    public static LobbyPose byId(int id) {
        LobbyPose[] values = values();
        return id >= 0 && id < values.length ? values[id] : DEFAULT;
    }
}
