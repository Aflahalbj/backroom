package com.backrooms.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Penanda "dimensi ini sudah dibangun", disimpan di folder data dimensinya.
 * Hanya diset setelah bangunan selesai seluruhnya -- kalau server mati di
 * tengah jalan, bangunannya diulang dari awal di kunjungan berikutnya.
 */
public class BuildState extends SavedData {

    private static final String NAME = "backrooms_build";

    private boolean built;

    public static BuildState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(BuildState::load, BuildState::new, NAME);
    }

    private static BuildState load(CompoundTag tag) {
        BuildState state = new BuildState();
        state.built = tag.getBoolean("built");
        return state;
    }

    public boolean isBuilt() {
        return built;
    }

    public void markBuilt() {
        built = true;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("built", built);
        return tag;
    }
}
