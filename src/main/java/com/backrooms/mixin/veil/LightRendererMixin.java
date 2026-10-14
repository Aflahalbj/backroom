package com.backrooms.mixin.veil;

import com.backrooms.client.render.ShadowMapRenderer;
import com.backrooms.world.BackroomsLevel;
import com.mojang.blaze3d.systems.RenderSystem;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.deferred.light.renderer.LightRenderer;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Port dari {@code com.sp.mixin.lightshadows.LightRendererMixin}: shader
 * lampu Veil mendapat matriks shadow map Level 0 (bayangan dari lampu
 * plafon) dan waktu game.
 */
@Mixin(value = LightRenderer.class, remap = false)
public abstract class LightRendererMixin {

    @Inject(method = "applyShader", at = @At(value = "INVOKE", target = "Lfoundry/veil/api/client/render/shader/program/ShaderProgram;bind()V"))
    private void backrooms$setUniforms(CallbackInfo ci) {
        ShaderProgram shader = VeilRenderSystem.getShader();
        if (shader == null) return;
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;
        if (player != null && client.level != null) {
            ResourceKey<Level> registryKey = player.level().dimension();
            if (registryKey == BackroomsLevel.LEVEL0.dimension()) {
                this.backrooms$setShadowUniforms(shader);
                shader.setInt("InOverWorld", registryKey == Level.OVERWORLD ? 1 : 0);
                shader.setInt("ShouldRender", 1);
            } else {
                shader.setInt("InOverWorld", registryKey == Level.OVERWORLD ? 1 : 0);
                shader.setInt("ShouldRender", 0);
            }
        }
        shader.setFloat("gameTime", RenderSystem.getShaderGameTime());
    }

    @Unique
    private void backrooms$setShadowUniforms(ShaderProgram shaderProgram) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        shaderProgram.setMatrix("viewMatrix", ShadowMapRenderer.createShadowModelView(camera.getPosition().x, camera.getPosition().y, camera.getPosition().z, true).last().pose());
        shaderProgram.setMatrix("orthographMatrix", ShadowMapRenderer.createProjMat());
    }
}
