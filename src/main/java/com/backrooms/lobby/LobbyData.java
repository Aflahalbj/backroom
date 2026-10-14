package com.backrooms.lobby;

import com.backrooms.world.BackroomsLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Panggung satu set latar lobby (hasil {@link StageFinder}), disimpan supaya tidak dicari ulang. */
public class LobbyData extends SavedData {

    private static final String NAME = "backrooms_lobby";
    /** Naikkan kalau cara pencariannya berubah, supaya panggung lama dicari ulang. */
    private static final int VERSION = 10;

    private LobbyStage stage;

    /** Data set latar {@code which} di dimensi lobby {@code level}. */
    public static LobbyData get(ServerLevel level, BackroomsLevel which) {
        return level.getDataStorage().computeIfAbsent(LobbyData::load, LobbyData::new,
                NAME + "_" + which.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static LobbyData load(CompoundTag tag) {
        LobbyData data = new LobbyData();
        if (tag.getInt("version") == VERSION && tag.contains("x")) {
            data.stage = new LobbyStage(new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")),
                    Direction.from2DDataValue(tag.getInt("facing")), tag.getInt("reach"));
        }
        return data;
    }

    public LobbyStage stage() {
        return stage;
    }

    public void setStage(LobbyStage stage) {
        this.stage = stage;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("version", VERSION);
        if (stage != null) {
            tag.putInt("x", stage.pos().getX());
            tag.putInt("y", stage.pos().getY());
            tag.putInt("z", stage.pos().getZ());
            tag.putInt("facing", stage.facing().get2DDataValue());
            tag.putInt("reach", stage.reach());
        }
        return tag;
    }
}
