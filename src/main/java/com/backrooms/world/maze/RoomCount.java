package com.backrooms.world.maze;

/** Jumlah varian tiap jenis ruangan (aroom..eroom), seperti {@code BackroomsLevel.RoomCount} asli. */
public record RoomCount(int aRoomCount, int bRoomCount, int cRoomCount, int dRoomCount, int eRoomCount) {

    public RoomCount(int all) {
        this(all, all, all, all, all);
    }
}
