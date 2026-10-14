package com.backrooms.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Set;
import java.util.TreeSet;

/**
 * Slot mana saja (lihat {@link BuildArea}) yang sudah selesai dibangun di
 * dimensi ini, disimpan di folder data dimensinya. Hanya dicatat setelah
 * bangunan selesai seluruhnya -- kalau server mati di tengah jalan,
 * bangunannya diulang dari awal di kunjungan berikutnya.
 */
public class BuildState extends SavedData {

    private static final String NAME = "backrooms_build";

    private final Set<Integer> built = new TreeSet<>();

    public static BuildState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(BuildState::load, BuildState::new, NAME);
    }

    private static BuildState load(CompoundTag tag) {
        BuildState state = new BuildState();
        // Format lama: satu bangunan per dimensi = slot utama.
        if (tag.getBoolean("built")) state.built.add(0);
        for (int slot : tag.getIntArray("slots")) state.built.add(slot);
        return state;
    }

    public boolean isBuilt(int slot) {
        return built.contains(slot);
    }

    public void markBuilt(int slot) {
        built.add(slot);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putIntArray("slots", built.stream().mapToInt(Integer::intValue).toArray());
        return tag;
    }
}
