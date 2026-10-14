package com.backrooms.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Port dari {@code com.sp.mixin.WorldRendererAccessor}: dipakai untuk menggambar shadow map. */
@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {

    @Invoker("renderChunkLayer")
    void backrooms$invokeRenderChunkLayer(RenderType type, PoseStack poseStack, double x, double y, double z, Matrix4f projection);

    @Invoker("setupRender")
    void backrooms$invokeSetupRender(Camera camera, Frustum frustum, boolean hasCapturedFrustum, boolean isSpectator);

    @Invoker("renderEntity")
    void backrooms$invokeRenderEntity(Entity entity, double x, double y, double z, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource);

    @Accessor("cullingFrustum")
    Frustum backrooms$getCullingFrustum();

    @Accessor("cullingFrustum")
    void backrooms$setCullingFrustum(Frustum frustum);

    @Accessor("entityRenderDispatcher")
    EntityRenderDispatcher backrooms$getEntityRenderDispatcher();

    @Accessor("renderBuffers")
    RenderBuffers backrooms$getRenderBuffers();
}
