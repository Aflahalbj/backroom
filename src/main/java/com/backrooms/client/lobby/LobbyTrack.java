package com.backrooms.client.lobby;

/**
 * Pilihan suara lobby (Penampilan > Musik). Disimpan sebagai indeks di
 * {@code ClientConfig.lobbySound}.
 */
public enum LobbyTrack {
    /** Suasana khas level yang jadi latar lobby. */
    SUASANA("Suasana level"),
    CREEPY1("Creepy 1", "creepy_music1"),
    CREEPY2("Creepy 2", "creepy_music2"),
    NO_ESCAPE("No Escape", "noescape"),
    SENJA("Senja Poolrooms", "poolrooms_ambience_sunset"),
    /** Lagu-lagu {@link #PLAYLIST} bergantian. */
    ACAK("Acak"),
    /** Tanpa musik, dan suara lain di lobby juga dibisukan. */
    HENING("Hening");

    /** Urutan lagu untuk {@link #ACAK}. */
    static final String[] PLAYLIST = {"creepy_music1", "noescape", "creepy_music2"};
    /** Suara suasana per latar lobby (urutan {@code BackroomsLevel}). */
    private static final String[] AMBIENCE = {"ambience", "level1_ambience1", "poolrooms_ambience_noon"};

    private final String displayName;
    private final String sound;

    LobbyTrack(String displayName) {
        this(displayName, null);
    }

    LobbyTrack(String displayName, String sound) {
        this.displayName = displayName;
        this.sound = sound;
    }

    public String displayName() {
        return displayName;
    }

    /** Sound event (namespace mod) yang diputar berulang untuk latar ini, atau {@code null}. */
    String sound(int background) {
        if (this == SUASANA) {
            return AMBIENCE[Math.floorMod(background, AMBIENCE.length)];
        }
        return sound;
    }

    public static LobbyTrack byId(int id) {
        LobbyTrack[] values = values();
        return id >= 0 && id < values.length ? values[id] : SUASANA;
    }
}
