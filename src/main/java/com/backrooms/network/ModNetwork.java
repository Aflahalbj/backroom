package com.backrooms.network;

import com.backrooms.BackroomsMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Paket mod ini. Lobby dan room: lihat {@link com.backrooms.lobby.LobbyManager}.
 * Senter disinkronkan seperti komponen pemain "flashlight" di
 * mod aslinya: klien mengirim statusnya, server menyimpan dan meneruskannya ke
 * semua pemain yang melihat pemain itu.
 */
public final class ModNetwork {

    private static final String VERSION = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(BackroomsMod.MOD_ID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    /** Status senter per pemain di server. */
    private static final Map<UUID, Boolean> FLASHLIGHTS = new HashMap<>();

    private ModNetwork() {}

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, FlashlightC2S.class, FlashlightC2S::encode, FlashlightC2S::decode,
                FlashlightC2S::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, FlashlightS2C.class, FlashlightS2C::encode, FlashlightS2C::decode,
                FlashlightS2C::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, ClientActionS2C.class, ClientActionS2C::encode, ClientActionS2C::decode,
                ClientActionS2C::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, LobbyActionC2S.class, LobbyActionC2S::encode, LobbyActionC2S::decode,
                LobbyActionC2S::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, LobbyStateS2C.class, LobbyStateS2C::encode, LobbyStateS2C::decode,
                LobbyStateS2C::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, RoomS2C.class, RoomS2C::encode, RoomS2C::decode,
                RoomS2C::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, RoomListS2C.class, RoomListS2C::encode, RoomListS2C::decode,
                RoomListS2C::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, CutsceneS2C.class, CutsceneS2C::encode, CutsceneS2C::decode,
                CutsceneS2C::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        MinecraftForge.EVENT_BUS.register(ModNetwork.class);
    }

    public static void send(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    static void setFlashlight(ServerPlayer player, boolean on) {
        FLASHLIGHTS.put(player.getUUID(), on);
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), new FlashlightS2C(player.getUUID(), on));
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer tracker) {
            boolean on = FLASHLIGHTS.getOrDefault(target.getUUID(), false);
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> tracker), new FlashlightS2C(target.getUUID(), on));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        FLASHLIGHTS.remove(event.getEntity().getUUID());
    }
}
