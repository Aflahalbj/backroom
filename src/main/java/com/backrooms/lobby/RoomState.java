package com.backrooms.lobby;

public enum RoomState {
    /** Menunggu host menekan START. */
    WAITING("MENUNGGU"),
    /** Backrooms room ini sedang dibangun. */
    BUILDING("MEMBANGUN"),
    /** Anggotanya sudah di dalam; pemain baru yang bergabung langsung masuk. */
    PLAYING("SEDANG MAIN");

    private final String label;

    RoomState(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
