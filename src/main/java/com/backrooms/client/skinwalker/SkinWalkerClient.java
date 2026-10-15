package com.backrooms.client.skinwalker;

import com.backrooms.component.PlayerComponent;
import com.backrooms.entity.custom.SkinWalkerEntity;
import com.backrooms.entity.ik.parts.sever_limbs.ServerLimb;
import com.backrooms.network.ModNetwork;
import com.backrooms.network.SkinwalkerSyncC2S;
import com.backrooms.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

/**
 * Bagian Skinwalker dari {@code com.sp.clientWrapper.ClientWrapper} dan
 * {@code SPBRevampedClient}: suara langkah kaki laba-laba, musik kejar,
 * sinkronisasi entity yang dilihat pemain, cek "korban terlihat di layar",
 * dan teks untuk korban.
 */
public final class SkinWalkerClient {

    /** Pemain yang sedang jadi korban Skinwalker (dari {@code CapturedPlayerS2C}). */
    private static final java.util.Set<java.util.UUID> CAPTURED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** True selama daftar pemain (tab) digambar; lihat {@code PlayerInfoMixin}. */
    public static boolean renderingTab = false;

    private SkinWalkerClient() {}

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(SkinWalkerClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            ClientWorldEvents.clear();
            CAPTURED.clear();
        });
    }

    public static void setCaptured(java.util.UUID player, boolean captured) {
        if (captured) {
            CAPTURED.add(player);
        } else {
            CAPTURED.remove(player);
        }
    }

    public static boolean isCaptured(java.util.UUID player) {
        return CAPTURED.contains(player);
    }

    public static void skinWalkerPlayStepSound(ServerLimb limb) {
        Minecraft client = Minecraft.getInstance();
        client.getSoundManager().play(new SimpleSoundInstance(ModSounds.event("skinwalker_footstep"), SoundSource.HOSTILE, 10.0F, 1.0F, limb.random, limb.pos.x, limb.pos.y, limb.pos.z));
    }

    public static void onRemoveSkinWalkerClientSide(SkinWalkerEntity entity) {
        if (entity.chaseSoundInstance instanceof SkinWalkerChaseSoundInstance sound && entity.level().isClientSide) {
            Minecraft.getInstance().getSoundManager().stop(sound);
        }
    }

    public static void handleSkinWalkerEntityClientSide(SkinWalkerEntity entity) {
        Minecraft client = Minecraft.getInstance();
        if (!(entity.chaseSoundInstance instanceof SkinWalkerChaseSoundInstance sound) || !client.getSoundManager().isActive(sound)) {
            SkinWalkerChaseSoundInstance chase = new SkinWalkerChaseSoundInstance(entity);
            entity.chaseSoundInstance = chase;
            client.getSoundManager().play(chase);
        }
    }

    public static void onPlayerComponent(int entityId, CompoundTag tag) {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null && client.level.getEntity(entityId) instanceof Player player) {
            PlayerComponent.get(player).readFromNbt(tag);
        }
    }

    public static void onSkinWalkerComponent(int entityId, CompoundTag tag) {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null && client.level.getEntity(entityId) instanceof SkinWalkerEntity skinWalker) {
            skinWalker.component.readFromNbt(tag);
        }
    }

    public static void playPersonalSound(String sound, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.event(sound), pitch, volume));
    }

    /** Bagian Skinwalker dari {@code ClientWrapper.tickClientPlayerComponent} (pemain ini saja). */
    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        PlayerComponent playerComponent = PlayerComponent.get(client.player);

        if (playerComponent.getTargetEntity() != client.crosshairPickEntity) {
            playerComponent.setTargetEntity(client.crosshairPickEntity);
            int id = playerComponent.getTargetEntity() != null ? playerComponent.getTargetEntity().getId() : -1;
            ModNetwork.CHANNEL.sendToServer(new SkinwalkerSyncC2S(SkinwalkerSyncC2S.Kind.TARGET_ENTITY, id));
        }

        if (playerComponent.hasBeenCaptured() && !playerComponent.isBeingCaptured()) {
            SkinWalkerCapturedFlavorText.tickFlavorText(playerComponent.player);
        }
    }

    /** Dari stage {@code AFTER_LEVEL} di {@code SPBRevampedClient}: apakah korban Skinwalker sedang terlihat di layar. */
    public static void afterLevel(Frustum frustum) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || frustum == null) return;
        PlayerComponent playerComponent = PlayerComponent.get(client.player);
        Entity activeSkinwalker = ClientWorldEvents.getActiveSkinwalkerTarget(client.level);
        if (activeSkinwalker != null) {
            AABB box = activeSkinwalker.getBoundingBoxForCulling().inflate(0.1D);
            boolean inFrustum = frustum.isVisible(box);
            if (inFrustum && client.player.hasLineOfSight(activeSkinwalker)) {
                if (!playerComponent.canSeeActiveSkinWalkerTarget()) {
                    playerComponent.setCanSeeActiveSkinWalkerTarget(true);
                    ModNetwork.CHANNEL.sendToServer(new SkinwalkerSyncC2S(SkinwalkerSyncC2S.Kind.SEE_SKINWALKER, 1));
                }
            } else if (playerComponent.canSeeActiveSkinWalkerTarget()) {
                playerComponent.setCanSeeActiveSkinWalkerTarget(false);
                ModNetwork.CHANNEL.sendToServer(new SkinwalkerSyncC2S(SkinwalkerSyncC2S.Kind.SEE_SKINWALKER, 0));
            }
        }
    }
}
