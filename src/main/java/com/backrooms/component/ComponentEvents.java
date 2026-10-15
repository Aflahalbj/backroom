package com.backrooms.component;

import com.backrooms.world.events.WorldEventsManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Pengganti mesin Cardinal Components untuk komponen {@code spb-revamped:player}:
 * dipasang ke setiap pemain, disalin saat respawn ({@code RespawnCopyStrategy.ALWAYS_COPY}),
 * disinkronkan ke pemainnya saat masuk/pindah dimensi, dan di-tick di server.
 */
public final class ComponentEvents {

    private ComponentEvents() {}

    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.register(PlayerComponent.class);
    }

    @SubscribeEvent
    public static void onAttach(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player player) {
            event.addCapability(PlayerComponent.ID, new PlayerComponent.Provider(player));
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        original.reviveCaps();
        CompoundTag tag = new CompoundTag();
        PlayerComponent.get(original).writeToNbt(tag);
        PlayerComponent.get(event.getEntity()).readFromNbt(tag);
        original.invalidateCaps();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        PlayerComponent.get(event.getEntity()).sync();
        if (event.getEntity() instanceof ServerPlayer joined) {
            for (ServerPlayer other : joined.server.getPlayerList().getPlayers()) {
                com.backrooms.network.ModNetwork.send(joined, PlayerComponent.get(other).capturedPacket());
            }
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        PlayerComponent.get(event.getEntity()).sync();
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        PlayerComponent.get(event.getEntity()).sync();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            WorldEventsManager.forget(player);
        }
    }

    /**
     * {@code SkinWalkerEntity.onKilledOther} di aslinya: begitu Skinwalker membunuh, korbannya dilepas.
     * Minecraft 1.20.1 tidak memanggil hook itu kalau yang mati pemain ({@code ServerPlayer.die} tidak
     * memanggil {@code killedEntity}), jadi di aslinya pelepasan ini tidak pernah terjadi untuk pemain.
     * Di sini dipanggil juga untuk pemain, sesuai maksud kode aslinya.
     */
    @SubscribeEvent
    public static void onDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getSource().getEntity() instanceof com.backrooms.entity.custom.SkinWalkerEntity skinWalker) {
            skinWalker.killedEntity(player.serverLevel(), player);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player && !player.isRemoved()) {
            PlayerComponent.get(player).serverTick();
        }
    }
}
