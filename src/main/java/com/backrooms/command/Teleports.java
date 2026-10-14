package com.backrooms.command;

import com.backrooms.lobby.LobbyManager;
import com.backrooms.lobby.RoomManager;
import com.backrooms.world.BackroomsLevel;
import com.backrooms.world.BuildArea;
import com.backrooms.world.LevelBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Pindah antar dimensi backrooms dan kembali ke dunia asal.
 *
 * <p>Titik kembali disimpan di {@link Player#PERSISTED_NBT_TAG}, yang ikut
 * terbawa Forge waktu pemain mati dan respawn. Titik itu hanya dicatat waktu
 * pemain berangkat dari luar backrooms, jadi pindah dari level 0 ke level 1
 * tetap mengembalikan ke tempat semula di dunia biasa.
 *
 * <p>Pemain yang ikut room masuk ke salinan level milik room-nya.
 */
public final class Teleports {

    private static final String RETURN_KEY = "backrooms:return";

    private Teleports() {}

    public static void goToLevel(ServerPlayer player, BackroomsLevel which) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        if (BackroomsLevel.byDimension(player.level().dimension()) == null) {
            saveReturnPoint(player);
        }
        BuildArea area = RoomManager.areaFor(player, which);
        LevelBuilder.ensureBuilt(server, area, player, ready -> {
            ServerLevel target = server.getLevel(which.dimension());
            if (target == null) return;
            LobbyManager.exit(ready);
            Vec3 spawn = area.worldSpawn();
            ready.fallDistance = 0;
            ready.teleportTo(target, spawn.x, spawn.y, spawn.z, ready.getYRot(), ready.getXRot());
            ready.sendSystemMessage(Component.literal("Kamu masuk ke " + which.displayName() + ".")
                    .withStyle(ChatFormatting.GRAY));
        });
    }

    public static void goToWorld(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        CompoundTag persisted = persisted(player);
        ServerLevel target = null;
        Vec3 pos = null;
        float yRot = player.getYRot();
        float xRot = player.getXRot();

        if (persisted.contains(RETURN_KEY)) {
            CompoundTag tag = persisted.getCompound(RETURN_KEY);
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString("dim")));
            target = server.getLevel(dimension);
            pos = new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
            yRot = tag.getFloat("yRot");
            xRot = tag.getFloat("xRot");
        }
        if (target == null) {
            target = server.overworld();
            BlockPos spawn = target.getSharedSpawnPos();
            pos = Vec3.atBottomCenterOf(spawn);
        }
        persisted.remove(RETURN_KEY);
        LobbyManager.exit(player);
        player.fallDistance = 0;
        player.teleportTo(target, pos.x, pos.y, pos.z, yRot, xRot);
        player.sendSystemMessage(Component.literal("Kamu kembali ke dunia.").withStyle(ChatFormatting.GRAY));
    }

    private static void saveReturnPoint(ServerPlayer player) {
        CompoundTag tag = new CompoundTag();
        tag.putString("dim", player.level().dimension().location().toString());
        tag.putDouble("x", player.getX());
        tag.putDouble("y", player.getY());
        tag.putDouble("z", player.getZ());
        tag.putFloat("yRot", player.getYRot());
        tag.putFloat("xRot", player.getXRot());
        persisted(player).put(RETURN_KEY, tag);
    }

    private static CompoundTag persisted(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(Player.PERSISTED_NBT_TAG)) {
            data.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return data.getCompound(Player.PERSISTED_NBT_TAG);
    }
}
