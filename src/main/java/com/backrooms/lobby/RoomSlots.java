package com.backrooms.lobby;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Nomor slot (lihat {@link com.backrooms.world.BuildArea}) berikutnya untuk
 * Backrooms milik room baru. Tidak pernah dipakai ulang: bangunan room lama
 * tetap di dunia, jadi slot barunya selalu tanah kosong.
 */
public class RoomSlots extends SavedData {

    private static final String NAME = "backrooms_room_slots";

    private int next = 1;

    static int allocate(MinecraftServer server) {
        RoomSlots slots = server.overworld().getDataStorage().computeIfAbsent(RoomSlots::load, RoomSlots::new, NAME);
        int slot = slots.next++;
        slots.setDirty();
        return slot;
    }

    private static RoomSlots load(CompoundTag tag) {
        RoomSlots slots = new RoomSlots();
        slots.next = Math.max(1, tag.getInt("next"));
        return slots;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("next", next);
        return tag;
    }
}
