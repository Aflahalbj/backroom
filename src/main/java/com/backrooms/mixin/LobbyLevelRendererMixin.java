package com.backrooms.mixin;

import com.backrooms.client.lobby.LobbyClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Barisan room di lobby dilihat dari sisi tiap pemain: karakter sendiri
 * digambar di depan tengah (bertukar tempat dengan yang ada di sana, lihat
 * {@link LobbyClient#renderOffset}). Posisi aslinya tidak berubah; hanya
 * gambarnya (termasuk bayangan dan nama) yang digeser.
 */
@Mixin(LevelRenderer.class)
public abstract class LobbyLevelRendererMixin {

    @Unique
    private static boolean backrooms$shifting;

    @Shadow
    private void renderEntity(Entity entity, double camX, double camY, double camZ, float partialTick, PoseStack poseStack,
                              MultiBufferSource buffers) {
    }

    /** Di jurang cutscene perkenalan tidak ada langit (latar Poolrooms punya langit siang yang membuatnya abu-abu). */
    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    private void backrooms$noSkyInAbyss(CallbackInfo ci) {
        if (com.backrooms.client.lobby.DropCutscene.darkVoid()) ci.cancel();
    }

    /** Jauh di jurang cutscene perkenalan blok dunia tidak digambar (struktur set di bawah lantai tampak seperti tiang). */
    @Inject(method = "renderChunkLayer", at = @At("HEAD"), cancellable = true)
    private void backrooms$noTerrainInAbyss(CallbackInfo ci) {
        if (com.backrooms.client.lobby.DropCutscene.hideTerrain()) ci.cancel();
    }

    @Inject(method = "renderEntity", at = @At("HEAD"), cancellable = true)
    private void backrooms$lobbySlot(Entity entity, double camX, double camY, double camZ, float partialTick, PoseStack poseStack,
                                     MultiBufferSource buffers, CallbackInfo ci) {
        if (backrooms$shifting) return;
        Vec3 offset = LobbyClient.renderOffset(entity, partialTick);
        if (offset == null) return;
        backrooms$shifting = true;
        try {
            renderEntity(entity, camX - offset.x, camY - offset.y, camZ - offset.z, partialTick, poseStack, buffers);
        } finally {
            backrooms$shifting = false;
        }
        ci.cancel();
    }
}
