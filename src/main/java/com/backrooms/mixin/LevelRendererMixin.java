package com.backrooms.mixin;

import com.backrooms.client.CameraEffect;
import com.backrooms.client.render.ModRenderTypes;
import com.backrooms.client.render.pbr.BlockIdMap;
import com.backrooms.world.BackroomsLevel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Port dari {@code com.sp.mixin.WorldRendererMixin} dan
 * {@code com.sp.mixin.renderlayer.WorldRendererMixin}:
 *
 * <ul>
 *   <li>layer langit Poolrooms dan layer PBR digambar sebelum layer solid;</li>
 *   <li>ID material blok dihitung saat frame pertama setelah reload;</li>
 *   <li>langit dan awan tidak digambar di Level 0 dan Poolrooms;</li>
 *   <li>penanda "sedang menggambar entity" untuk shader warp_player.</li>
 * </ul>
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Shadow
    protected abstract void renderChunkLayer(RenderType type, PoseStack poseStack, double x, double y, double z, Matrix4f projection);

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void backrooms$initBlockIDs(PoseStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera,
                                        GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        if (!BlockIdMap.init) {
            BlockIdMap.init();
        }
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLorg/joml/Matrix4f;)V",
            ordinal = 0))
    private void backrooms$renderExtraLayers(PoseStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera,
                                             GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        Vec3 cameraPos = camera.getPosition();
        this.renderChunkLayer(ModRenderTypes.getPoolroomsSky(), matrices, cameraPos.x(), cameraPos.y(), cameraPos.z(), projectionMatrix);
        this.renderChunkLayer(ModRenderTypes.getPbrLayer(), matrices, cameraPos.x(), cameraPos.y(), cameraPos.z(), projectionMatrix);
    }

    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    private void backrooms$renderSky(PoseStack poseStack, Matrix4f projectionMatrix, float tickDelta, Camera camera, boolean thickFog, Runnable fogCallback, CallbackInfo ci) {
        if (CameraEffect.isInLevel(BackroomsLevel.LEVEL0) || CameraEffect.isInLevel(BackroomsLevel.POOLROOMS)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void backrooms$renderClouds(PoseStack poseStack, Matrix4f projectionMatrix, float tickDelta, double cameraX, double cameraY, double cameraZ, CallbackInfo ci) {
        if (CameraEffect.isInLevel(BackroomsLevel.LEVEL0) || CameraEffect.isInLevel(BackroomsLevel.POOLROOMS)) {
            ci.cancel();
        }
    }

    /** popPush("entities") */
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V", ordinal = 10, shift = At.Shift.AFTER))
    private void backrooms$startWarp(PoseStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera,
                                     GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        CameraEffect.shouldRenderWarp = true;
    }

    /** popPush("blockentities") */
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V", ordinal = 11, shift = At.Shift.BEFORE))
    private void backrooms$stopWarp(PoseStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera,
                                    GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        CameraEffect.shouldRenderWarp = false;
    }
}
