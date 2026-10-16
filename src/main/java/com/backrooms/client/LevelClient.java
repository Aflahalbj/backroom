package com.backrooms.client;

import com.backrooms.client.ambience.LoopingAmbienceSoundInstance;
import com.backrooms.client.render.PoolroomsDayCycle;
import com.backrooms.client.skinwalker.ClientWorldEvents;
import com.backrooms.client.smiler.SmilerGlitchSoundInstance;
import com.backrooms.component.PlayerComponent;
import com.backrooms.entity.custom.SmilerEntity;
import com.backrooms.network.ModNetwork;
import com.backrooms.network.SkinwalkerSyncC2S;
import com.backrooms.world.BackroomsLevel;
import com.backrooms.world.events.LightState;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

/**
 * Bagian Smiler dan suara level dari {@code ClientWrapper.tickClientPlayerComponent}
 * (pemain ini saja): glitch saat dilihat Smiler, suara latar Level 1, tawa Smiler
 * saat blackout, dan suara latar Poolrooms siang/senja.
 *
 * <p>Beda kecil dari aslinya: suara latar Poolrooms dan tawa Smiler di aslinya tidak
 * berhenti kalau pemain pindah level (hanya dicek saat masih di level itu); di sini berhenti.
 */
public final class LevelClient {

    private static SoundInstance deepAmbience;
    private static SoundInstance poolroomsNoonAmbience;
    private static SoundInstance poolroomsSunsetAmbience;
    private static SoundInstance smilerAmbience;

    private LevelClient() {}

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(LevelClient::onClientTick);
    }

    private static boolean isPlaying(SoundManager soundManager, SoundInstance sound) {
        return sound != null && soundManager.isActive(sound);
    }

    /** Level dimensi pemain ({@code playerComponent.player.getWorld().getRegistryKey()} di aslinya). */
    private static BackroomsLevel levelOf(Player player) {
        return BackroomsLevel.byDimension(player.level().dimension());
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        Player player = client.player;
        PlayerComponent playerComponent = PlayerComponent.get(player);
        SoundManager soundManager = client.getSoundManager();

        List<SmilerEntity> smilerEntityList = player.level().getEntitiesOfClass(SmilerEntity.class, player.getBoundingBox().inflate(15.0D, 1.0D, 15.0D), livingEntity -> true);
        boolean isSeen = false;
        if (!smilerEntityList.isEmpty()) {
            for (SmilerEntity smiler : smilerEntityList) {
                if (smiler.hasLineOfSight(player)) {
                    playerComponent.setShouldGlitch(true);
                    isSeen = true;
                    break;
                }
            }
        }

        if (!isSeen) {
            playerComponent.setShouldGlitch(false);
        }

        if (playerComponent.shouldGlitch()) {
            playerComponent.glitchTick = Math.min(playerComponent.glitchTick + 1, 80);
            playerComponent.glitchTimer = Math.min((float) playerComponent.glitchTick / 80.0F, 1.0F);
            if (!(playerComponent.glitchAmbience instanceof SoundInstance glitch) || !soundManager.isActive(glitch)) {
                SmilerGlitchSoundInstance sound = new SmilerGlitchSoundInstance(player);
                playerComponent.glitchAmbience = sound;
                soundManager.play(sound);
            }

            if (playerComponent.glitchTimer >= 0.25F && !playerComponent.shouldInflictGlitchDamage) {
                playerComponent.shouldInflictGlitchDamage = true;
                ModNetwork.CHANNEL.sendToServer(new SkinwalkerSyncC2S(SkinwalkerSyncC2S.Kind.GLITCH, 1));
            }
        } else {
            playerComponent.glitchTick = Math.max(playerComponent.glitchTick - 1, 0);
            playerComponent.glitchTimer = Math.max((float) playerComponent.glitchTick / 80.0F, 0.0F);
            if (playerComponent.glitchTimer <= 0.0F && playerComponent.glitchAmbience instanceof SoundInstance glitch && soundManager.isActive(glitch)) {
                soundManager.stop(glitch);
            }

            if (playerComponent.glitchTimer <= 0.75F && playerComponent.shouldInflictGlitchDamage) {
                playerComponent.shouldInflictGlitchDamage = false;
                ModNetwork.CHANNEL.sendToServer(new SkinwalkerSyncC2S(SkinwalkerSyncC2S.Kind.GLITCH, 0));
            }
        }

        BackroomsLevel level = levelOf(player);
        if (level == BackroomsLevel.LEVEL1 && !isPlaying(soundManager, deepAmbience)) {
            deepAmbience = new LoopingAmbienceSoundInstance("ambience", player, () -> levelOf(player) == BackroomsLevel.LEVEL1);
            soundManager.play(deepAmbience);
        }

        if (level == BackroomsLevel.POOLROOMS) {
            PoolroomsDayCycle.advanceDayTime(client.level);
            if (ClientWorldEvents.isNoon() && !isPlaying(soundManager, poolroomsNoonAmbience)) {
                poolroomsNoonAmbience = new LoopingAmbienceSoundInstance("poolrooms_ambience_noon", player,
                        () -> levelOf(player) == BackroomsLevel.POOLROOMS && ClientWorldEvents.isNoon());
                soundManager.play(poolroomsNoonAmbience);
            }

            if (!ClientWorldEvents.isNoon() && !isPlaying(soundManager, poolroomsSunsetAmbience)) {
                poolroomsSunsetAmbience = new LoopingAmbienceSoundInstance("poolrooms_ambience_sunset", player,
                        () -> levelOf(player) == BackroomsLevel.POOLROOMS && !ClientWorldEvents.isNoon());
                soundManager.play(poolroomsSunsetAmbience);
            }
        }

        if (level == BackroomsLevel.LEVEL1) {
            if (ClientWorldEvents.lightState() == LightState.BLACKOUT && !isPlaying(soundManager, smilerAmbience)) {
                smilerAmbience = new LoopingAmbienceSoundInstance("smiler_laugh", player,
                        () -> levelOf(player) == BackroomsLevel.LEVEL1 && ClientWorldEvents.lightState() == LightState.BLACKOUT);
                soundManager.play(smilerAmbience);
            }
        }
    }
}
