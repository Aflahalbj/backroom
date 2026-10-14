package com.backrooms.mixin.veil;

import com.backrooms.client.CameraEffect;
import com.backrooms.client.render.PoolroomsDayCycle;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import foundry.veil.api.client.render.shader.program.MutableUniformAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Port dari {@code com.sp.mixin.MutableUniformAccessMixin}: semua shader Veil
 * ikut mendapat uniform tambahan mod asli (offset view bobbing, ukuran layar,
 * rasio atlas, waktu Poolrooms, matriks shadow map, sudut warp).
 */
@Mixin(value = MutableUniformAccess.class, remap = false)
public interface MutableUniformAccessMixin {

    /**
     * @author SP-Backrooms Revamped (port)
     * @reason sama dengan mod aslinya
     */
    @Overwrite
    default void applyRenderSystem() {
        MutableUniformAccess self = (MutableUniformAccess) this;
        self.setMatrix("RenderModelViewMat", RenderSystem.getModelViewMatrix());
        self.setMatrix("RenderProjMat", RenderSystem.getProjectionMatrix());
        self.setVector("ColorModulator", RenderSystem.getShaderColor());
        self.setFloat("GlintAlpha", RenderSystem.getShaderGlintAlpha());
        self.setFloat("FogStart", RenderSystem.getShaderFogStart());
        self.setFloat("FogEnd", RenderSystem.getShaderFogEnd());
        self.setVector("FogColor", RenderSystem.getShaderFogColor());
        self.setInt("FogShape", RenderSystem.getShaderFogShape().getIndex());
        self.setMatrix("TextureMatrix", RenderSystem.getTextureMatrix());
        self.setFloat("GameTime", RenderSystem.getShaderGameTime());
        if (CameraEffect.cameraBobOffset != null) {
            self.setVector("cameraBobOffset", CameraEffect.cameraBobOffset);
        }
        Minecraft client = Minecraft.getInstance();
        Window window = client.getWindow();
        self.setVector("ScreenSize", (float) window.getWidth(), (float) window.getHeight());
        TextureAtlas texture = client.getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);
        if (texture != null) {
            self.setFloat("atlasAspectRatio", (float) ((com.backrooms.mixin.TextureAtlasAccessor) texture).backrooms$getHeight() / (float) ((com.backrooms.mixin.TextureAtlasAccessor) texture).backrooms$getWidth());
        }
        if (client.level != null && CameraEffect.camera != null) {
            self.setFloat("sunsetTimer", PoolroomsDayCycle.getDayTime(client.level));
            CameraEffect.setShadowUniforms(self, client.level);
            self.setFloat("warpAngle", CameraEffect.getWarpTimer(client.level));
        }
    }
}
