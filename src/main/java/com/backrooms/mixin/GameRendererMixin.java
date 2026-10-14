package com.backrooms.mixin;

import com.backrooms.client.CameraEffect;
import com.backrooms.client.ClientConfig;
import com.backrooms.client.VeilShaders;
import com.backrooms.client.lobby.IntroCutscene;
import com.backrooms.client.lobby.LobbyClient;
import com.backrooms.client.render.ShadowMapRenderer;
import com.backrooms.client.render.camera.CameraRoll;
import com.backrooms.util.MathStuff;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Port dari {@code com.sp.mixin.GameRendererMixin}:
 *
 * <ul>
 *   <li>roll kamera (menoleh + bergeser + guncangan) sebelum efek "hurt",
 *       atau rotasi {@code cameraRotZ} selama intro Level 0;</li>
 *   <li>pitch/yaw kamera dihaluskan (orang pertama);</li>
 *   <li>view bobbing diganti versi asli (lebih kuat, x10 dengan real camera);</li>
 *   <li>shader blok/entity diganti shader shadow map selama shadow pass;</li>
 *   <li>shader {@code spbrevamped:warp_player} untuk entity translucent saat
 *       entity digambar.</li>
 * </ul>
 *
 * Di Forge ada rotasi roll tambahan dari {@code ViewportEvent}, jadi rotasi
 * pitch/yaw kamera ada di ordinal 3 dan 4 (di Fabric 2 dan 3).
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Unique
    private static final ResourceLocation BACKROOMS$SHADOW_SOLID = new ResourceLocation("spb-revamped", "shadowmap/rendertype_solid");
    @Unique
    private static final ResourceLocation BACKROOMS$SHADOW_ENTITY = new ResourceLocation("spb-revamped", "shadowmap/rendertype_entity");
    @Unique
    private static final ResourceLocation BACKROOMS$WARP_ENTITY = new ResourceLocation("spbrevamped", "warp_player");

    @Unique
    private float backrooms$smoothPitch = 0.0F;
    @Unique
    private float backrooms$smoothYaw = 0.0F;

    @Shadow
    @Final
    Minecraft minecraft;

    @Shadow
    private static ShaderInstance rendertypeEntityTranslucentShader;

    @Shadow
    public abstract void setRenderBlockOutline(boolean renderBlockOutline);

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;bobHurt(Lcom/mojang/blaze3d/vertex/PoseStack;F)V"))
    private void backrooms$cameraRoll(float tickDelta, long limitTime, PoseStack matrices, CallbackInfo ci) {
        // Kamera lobby menatap karakter, bukan pandangan pemain: tanpa kotak blok yang dibidik.
        this.setRenderBlockOutline(!LobbyClient.overridesCamera());
        if (CameraEffect.shouldRenderCameraEffect()) {
            Player player = this.minecraft.player;
            if (player != null) {
                if (ClientConfig.enableRealCamera() && !IntroCutscene.isPlaying() && !LobbyClient.overridesCamera()
                        && this.minecraft.getCameraEntity() == player) {
                    matrices.mulPose(Axis.ZP.rotationDegrees(CameraRoll.doCameraRoll(player, tickDelta)));
                } else if (IntroCutscene.isPlaying()) {
                    matrices.mulPose(Axis.YP.rotationDegrees(IntroCutscene.cameraRotZ));
                }
            }
        }
    }

    @ModifyArg(method = "renderLevel", at = @At(value = "INVOKE", target = "Lcom/mojang/math/Axis;rotationDegrees(F)Lorg/joml/Quaternionf;", ordinal = 3))
    private float backrooms$smoothPitch(float deg) {
        if (!CameraEffect.shouldRenderCameraEffect()) {
            return deg;
        }
        if (this.minecraft.player != null && ClientConfig.enableSmoothCamera() && this.minecraft.options.getCameraType() == CameraType.FIRST_PERSON) {
            this.backrooms$smoothYaw = MathStuff.Lerp(this.backrooms$smoothYaw, deg, ClientConfig.cameraSmoothing(), this.minecraft.getDeltaFrameTime());
            return this.backrooms$smoothYaw;
        }
        this.backrooms$smoothYaw = deg;
        return deg;
    }

    @ModifyArg(method = "renderLevel", at = @At(value = "INVOKE", target = "Lcom/mojang/math/Axis;rotationDegrees(F)Lorg/joml/Quaternionf;", ordinal = 4))
    private float backrooms$smoothYaw(float deg) {
        if (!CameraEffect.shouldRenderCameraEffect()) {
            return deg;
        }
        if (this.minecraft.player != null && ClientConfig.enableSmoothCamera() && this.minecraft.options.getCameraType() == CameraType.FIRST_PERSON) {
            this.backrooms$smoothPitch = MathStuff.Lerp(this.backrooms$smoothPitch, deg, ClientConfig.cameraSmoothing(), this.minecraft.getDeltaFrameTime());
            return this.backrooms$smoothPitch;
        }
        this.backrooms$smoothPitch = deg;
        return deg;
    }

    /** Pengganti {@code @Overwrite bobView} aslinya. */
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void backrooms$bobView(PoseStack matrices, float tickDelta, CallbackInfo ci) {
        ci.cancel();
        if (this.minecraft.getCameraEntity() instanceof Player playerEntity) {
            float f = playerEntity.walkDist - playerEntity.walkDistO;
            float g = -(playerEntity.walkDist + f * tickDelta);
            float h = Mth.lerp(tickDelta, playerEntity.oBob, playerEntity.bob);
            Vector3f cameraBob = new Vector3f(Mth.sin(g * (float) Math.PI) * h * 0.5F, -Math.abs(Mth.cos(g * (float) Math.PI) * h), 0.0F);
            matrices.translate(cameraBob.x, cameraBob.y, cameraBob.z);
            CameraEffect.cameraBobOffset = new Vector3f(cameraBob);
            matrices.mulPose(Axis.ZP.rotationDegrees(Mth.sin(g * (float) Math.PI) * h * 3.0F));
            float multiplier = 5.0F;
            if (ClientConfig.enableRealCamera()) {
                multiplier = 10.0F;
            }
            matrices.mulPose(Axis.XP.rotationDegrees(Math.abs(Mth.cos(g * (float) Math.PI - 0.2F) * h) * multiplier));
        }
    }

    @Inject(method = {"getRendertypeSolidShader", "getRendertypeCutoutShader", "getRendertypeCutoutMippedShader"}, at = @At("HEAD"), cancellable = true)
    private static void backrooms$setSolidShader(CallbackInfoReturnable<ShaderInstance> cir) {
        if (ShadowMapRenderer.isRenderingShadowMap()) {
            ShaderInstance shader = VeilShaders.instance(BACKROOMS$SHADOW_SOLID);
            if (shader != null) {
                cir.setReturnValue(shader);
            }
        }
    }

    @Inject(method = {"getRendertypeEntityTranslucentShader", "getRendertypeEntitySolidShader", "getRendertypeEntityCutoutShader",
            "getRendertypeEntityCutoutNoCullShader", "getRendertypeEntityTranslucentCullShader"}, at = @At("TAIL"), cancellable = true)
    private static void backrooms$setPlayerShader(CallbackInfoReturnable<ShaderInstance> cir) {
        if (ShadowMapRenderer.isRenderingShadowMap()) {
            ShaderInstance shader = VeilShaders.instance(BACKROOMS$SHADOW_ENTITY);
            if (shader != null) {
                cir.setReturnValue(shader);
            }
        }
    }

    @Inject(method = "getRendertypeEntityTranslucentShader", at = @At("TAIL"), cancellable = true)
    private static void backrooms$setPlayerWarpShader(CallbackInfoReturnable<ShaderInstance> cir) {
        ShaderInstance shader = VeilShaders.instance(BACKROOMS$WARP_ENTITY);
        if (shader != null && CameraEffect.shouldRenderWarp) {
            cir.setReturnValue(shader);
        } else {
            cir.setReturnValue(rendertypeEntityTranslucentShader);
        }
    }
}
