package com.backrooms.world.events;

import com.backrooms.network.ModNetwork;
import com.backrooms.network.WorldEventsS2C;
import com.backrooms.world.BackroomsLevel;
import com.backrooms.world.BuildArea;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Pemegang {@link WorldEvents} per salinan level, disimpan bersama dunianya
 * (seperti komponen dunia CCA) dan di-tick tiap tick server. Klien menerima
 * status salinan yang sedang ditempatinya setiap kali statusnya berubah atau
 * pemain pindah salinan (CCA juga menyinkronkan saat pemain masuk dunia).
 */
public final class WorldEventsManager {

    private static final String DATA_NAME = "spb_revamped_world_events";
    /** Salinan terakhir yang statusnya sudah dikirim ke tiap pemain. */
    private static final Map<UUID, String> SENT = new HashMap<>();

    private WorldEventsManager() {}

    /** Slot salinan level di koordinat X ini; di dimensi selain ketiga level hanya ada satu (0). */
    public static int slotOf(Level level, double x) {
        if (BackroomsLevel.byDimension(level.dimension()) == null) return 0;
        return (int) Math.round(x / BuildArea.SPACING);
    }

    public static WorldEvents get(ServerLevel level, double x) {
        return data(level).get(level, slotOf(level, x));
    }

    /**
     * Kondisi lampu di tempat ini untuk lampu neon di server, atau {@code null}
     * kalau levelnya bukan {@code BackroomsLevelWithLights} (Poolrooms, lobby, dunia biasa).
     */
    public static LightState lightStateAt(ServerLevel level, double x) {
        BackroomsLevel which = BackroomsLevel.byDimension(level.dimension());
        if (which != BackroomsLevel.LEVEL0 && which != BackroomsLevel.LEVEL1) return null;
        return get(level, x).getLightState();
    }

    static void setDirty(ServerLevel level) {
        data(level).setDirty();
    }

    private static Data data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(Data::load, Data::new, DATA_NAME);
    }

    public static void tick(MinecraftServer server) {
        for (BackroomsLevel which : BackroomsLevel.values()) {
            ServerLevel level = server.getLevel(which.dimension());
            if (level == null) continue;
            Set<Integer> slots = new HashSet<>();
            for (ServerPlayer player : level.players()) {
                slots.add(slotOf(level, player.getX()));
            }
            for (int slot : slots) {
                data(level).get(level, slot).serverTick();
            }
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel level = player.serverLevel();
            int slot = slotOf(level, player.getX());
            String key = level.dimension().location() + "#" + slot;
            if (!key.equals(SENT.get(player.getUUID()))) {
                SENT.put(player.getUUID(), key);
                CompoundTag tag = new CompoundTag();
                data(level).get(level, slot).writeToNbt(tag);
                ModNetwork.send(player, new WorldEventsS2C(tag));
            }
        }
    }

    public static void forget(ServerPlayer player) {
        SENT.remove(player.getUUID());
    }

    public static void clear() {
        SENT.clear();
    }

    /** Semua salinan di satu dimensi. */
    private static final class Data extends SavedData {
        private final Map<Integer, WorldEvents> events = new HashMap<>();
        private final Map<Integer, CompoundTag> pending = new HashMap<>();

        static Data load(CompoundTag tag) {
            Data data = new Data();
            CompoundTag slots = tag.getCompound("slots");
            for (String key : slots.getAllKeys()) {
                data.pending.put(Integer.parseInt(key), slots.getCompound(key));
            }
            return data;
        }

        WorldEvents get(ServerLevel level, int slot) {
            return this.events.computeIfAbsent(slot, s -> {
                WorldEvents events = new WorldEvents(level, s);
                CompoundTag saved = this.pending.remove(s);
                if (saved != null) events.readFromNbt(saved);
                return events;
            });
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            CompoundTag slots = new CompoundTag();
            this.pending.forEach((slot, saved) -> slots.put(Integer.toString(slot), saved));
            this.events.forEach((slot, events) -> {
                CompoundTag saved = new CompoundTag();
                events.writeToNbt(saved);
                slots.put(Integer.toString(slot), saved);
            });
            tag.put("slots", slots);
            return tag;
        }
    }
}
