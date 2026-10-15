package com.backrooms.client.skinwalker;

import com.backrooms.component.PlayerComponent;
import com.backrooms.mixin.SoundEngineAccessor;
import com.backrooms.mixin.SoundManagerAccessor;
import com.backrooms.network.ModNetwork;
import com.backrooms.network.SkinwalkerSyncC2S;
import com.backrooms.sound.ModSounds;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Port dari {@code com.sp.render.SkinwalkerJumpscare}: 14 detik layar
 * jumpscare di shader VHS untuk korban yang baru ditangkap (wajah seram di
 * detik 2 dan 12), semua suara lain dihentikan, HUD disembunyikan.
 */
public class SkinwalkerJumpscare {
    private static long startTime;
    private static boolean started = false;
    private static final RandomSource random = RandomSource.create(13L);
    private static final RandomSource random2 = RandomSource.create(8767L);
    private static SimpleSoundInstance jumpScareSound;

    public static void doJumpscare(ShaderProgram program, Minecraft client, PlayerComponent component) {
        if (!started) {
            startTime = System.currentTimeMillis();
            started = true;
            if (client.player != null) {
                jumpScareSound = new SimpleSoundInstance(ModSounds.event("jumpscare"), SoundSource.HOSTILE, 1.0F, 1.0F, client.player.getRandom(), client.player.blockPosition());
                client.getSoundManager().play(jumpScareSound);
            }
        }

        stopAllOtherSounds(jumpScareSound.getLocation(), ((SoundManagerAccessor) client.getSoundManager()).getSoundEngine());
        client.options.hideGui = true;
        program.setInt("Jumpscare", 1);
        long currentTime = System.currentTimeMillis() - startTime;
        if (currentTime >= 2000L && currentTime < 12000L) {
            program.setInt("CreepyFace1", 1);
        }

        if (currentTime >= 12000L) {
            program.setInt("CreepyFace2", 1);
            program.setVector("Rand", random.nextFloat(), random2.nextFloat());
        }

        if (currentTime >= 14000L) {
            component.setBeingCaptured(false);
            client.options.hideGui = false;
            started = false;
            startTime = 0L;
            ModNetwork.CHANNEL.sendToServer(new SkinwalkerSyncC2S(SkinwalkerSyncC2S.Kind.BEING_CAPTURED, component.isBeingCaptured() ? 1 : 0));
            program.setInt("Jumpscare", 0);
            program.setInt("CreepyFace1", 0);
            program.setInt("CreepyFace2", 0);
            program.setVector("Rand", 0.0F, 0.0F);
        }
    }

    /** {@code ExtraUtils.stopAllOtherSounds}. */
    private static void stopAllOtherSounds(ResourceLocation id, SoundEngine soundSystem) {
        for (SoundInstance soundInstance : ((SoundEngineAccessor) soundSystem).getInstanceBySource().values()) {
            if (!soundInstance.getLocation().equals(id)) {
                soundSystem.stop(soundInstance);
            }
        }
    }
}
