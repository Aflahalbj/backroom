package com.backrooms.client.render;

import com.backrooms.mixin.LevelRendererAccessor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * Port dari {@code com.sp.render.ShadowMapRenderer}: shadow map ortografis
 * yang digambar ke framebuffer {@code spb-revamped:shadowmap} sebelum dunia
 * (stage AFTER_SKY). Level 0 memakai cahaya tegak lurus dari atas; Poolrooms
 * mengikuti sudut matahari {@link PoolroomsDayCycle} dan ikut menggambar entity.
 */
public final class ShadowMapRenderer {

    private static final ResourceLocation SHADOW_MAP = new ResourceLocation("spb-revamped", "shadowmap");
    private static boolean renderingShadowMap;

    private ShadowMapRenderer() {}

    public static void renderShadowMap(Camera camera, float tickDelta, Level world) {
        Minecraft client = Minecraft.getInstance();
        LevelRendererAccessor accessor = (LevelRendererAccessor) client.levelRenderer;
        Vec3 cameraPos = camera.getPosition();
        PoseStack shadowModelView = createShadowModelView(cameraPos.x, cameraPos.y, cameraPos.z, world, true);
        Matrix4f shadowProjMat = createProjMat();
        Matrix4f backupProjMat = RenderSystem.getProjectionMatrix();
        int width = client.getMainRenderTarget().viewWidth;
        int height = client.getMainRenderTarget().viewHeight;
        AdvancedFbo shadowMap = VeilRenderSystem.renderer().getFramebufferManager().getFramebuffer(SHADOW_MAP);
        if (shadowMap != null) {
            RenderSystem.setProjectionMatrix(shadowProjMat, VertexSorting.ORTHOGRAPHIC_Z);
            shadowMap.bind(true);
            setRenderingShadowMap(true);
            Frustum frustum = new Frustum(shadowModelView.last().pose(), shadowProjMat);
            frustum.prepare(cameraPos.x, cameraPos.y, cameraPos.z);
            accessor.backrooms$setCullingFrustum(frustum);
            accessor.backrooms$invokeSetupRender(camera, frustum, false, false);
            accessor.backrooms$invokeRenderChunkLayer(RenderType.cutout(), shadowModelView, cameraPos.x, cameraPos.y, cameraPos.z, shadowProjMat);
            accessor.backrooms$invokeRenderChunkLayer(RenderType.cutoutMipped(), shadowModelView, cameraPos.x, cameraPos.y, cameraPos.z, shadowProjMat);
            accessor.backrooms$invokeRenderChunkLayer(RenderType.solid(), shadowModelView, cameraPos.x, cameraPos.y, cameraPos.z, shadowProjMat);
            if (client.level != null) {
                MultiBufferSource.BufferSource immediate = accessor.backrooms$getRenderBuffers().bufferSource();
                for (Entity entity : client.level.entitiesForRendering()) {
                    if (accessor.backrooms$getEntityRenderDispatcher().shouldRender(entity, accessor.backrooms$getCullingFrustum(), cameraPos.x, cameraPos.y, cameraPos.z) || entity.isSpectator()) {
                        accessor.backrooms$invokeRenderEntity(entity, cameraPos.x, cameraPos.y, cameraPos.z, tickDelta, shadowModelView, immediate);
                    }
                }
                immediate.endBatch();
            }
            setRenderingShadowMap(false);
            AdvancedFbo.unbind();
            RenderSystem.viewport(0, 0, width, height);
            RenderSystem.setProjectionMatrix(backupProjMat, VertexSorting.DISTANCE_TO_ORIGIN);
        }
    }

    public static void renderLevel0ShadowMap(Camera camera, Level world) {
        Minecraft client = Minecraft.getInstance();
        LevelRendererAccessor accessor = (LevelRendererAccessor) client.levelRenderer;
        Vec3 cameraPos = camera.getPosition();
        PoseStack shadowModelView = createShadowModelView(cameraPos.x, cameraPos.y, cameraPos.z, true);
        Matrix4f shadowProjMat = createProjMat();
        Matrix4f backupProjMat = RenderSystem.getProjectionMatrix();
        int width = client.getMainRenderTarget().viewWidth;
        int height = client.getMainRenderTarget().viewHeight;
        AdvancedFbo shadowMap = VeilRenderSystem.renderer().getFramebufferManager().getFramebuffer(SHADOW_MAP);
        if (shadowMap != null) {
            RenderSystem.setProjectionMatrix(shadowProjMat, VertexSorting.ORTHOGRAPHIC_Z);
            shadowMap.bind(true);
            setRenderingShadowMap(true);
            GL11.glEnable(GL11.GL_CLIP_PLANE0);
            if (world.dimension() != Level.OVERWORLD) {
                Frustum frustum = new Frustum(shadowModelView.last().pose(), shadowProjMat);
                frustum.prepare(cameraPos.x, cameraPos.y, cameraPos.z);
                accessor.backrooms$setCullingFrustum(frustum);
                accessor.backrooms$invokeSetupRender(camera, frustum, false, false);
            }
            accessor.backrooms$invokeRenderChunkLayer(RenderType.cutout(), shadowModelView, cameraPos.x, cameraPos.y, cameraPos.z, shadowProjMat);
            accessor.backrooms$invokeRenderChunkLayer(RenderType.cutoutMipped(), shadowModelView, cameraPos.x, cameraPos.y, cameraPos.z, shadowProjMat);
            accessor.backrooms$invokeRenderChunkLayer(RenderType.solid(), shadowModelView, cameraPos.x, cameraPos.y, cameraPos.z, shadowProjMat);
            GL11.glDisable(GL11.GL_CLIP_PLANE0);
            setRenderingShadowMap(false);
            AdvancedFbo.unbind();
            RenderSystem.viewport(0, 0, width, height);
            RenderSystem.setProjectionMatrix(backupProjMat, VertexSorting.DISTANCE_TO_ORIGIN);
        }
    }

    public static PoseStack createShadowModelView(double cameraX, double cameraY, double cameraZ, Level world, boolean doInterval) {
        PoseStack shadowModelView = new PoseStack();
        shadowModelView.last().normal().identity();
        shadowModelView.last().pose().identity();
        shadowModelView.last().pose().translate(0.0F, 0.0F, -100.0F);
        rotateShadowModelView(shadowModelView.last().pose(), world);
        if (doInterval) {
            applyInterval(shadowModelView, cameraX, cameraY, cameraZ);
        }
        return shadowModelView;
    }

    public static PoseStack createShadowModelView(double cameraX, double cameraY, double cameraZ, boolean doInterval) {
        PoseStack shadowModelView = new PoseStack();
        shadowModelView.last().normal().identity();
        shadowModelView.last().pose().identity();
        shadowModelView.last().pose().translate(0.0F, 0.0F, -100.0F);
        rotateShadowModelView(shadowModelView.last().pose());
        if (doInterval) {
            applyInterval(shadowModelView, cameraX, cameraY, cameraZ);
        }
        return shadowModelView;
    }

    private static void applyInterval(PoseStack shadowModelView, double cameraX, double cameraY, double cameraZ) {
        float offsetX = (float) cameraX % 2.0F;
        float offsetY = (float) cameraY % 2.0F;
        float offsetZ = (float) cameraZ % 2.0F;
        float halfIntervalSize = 1.0F;
        offsetX -= halfIntervalSize;
        offsetY -= halfIntervalSize;
        offsetZ -= halfIntervalSize;
        shadowModelView.last().pose().translate(offsetX, offsetY, offsetZ);
    }

    public static void rotateShadowModelView(Matrix4f shadowModelView) {
        shadowModelView.rotate(Axis.XP.rotationDegrees(90.0F));
    }

    public static void rotateShadowModelView(Matrix4f shadowModelView, Level world) {
        shadowModelView.rotate(Axis.XP.rotationDegrees(PoolroomsDayCycle.getSunAngle()));
    }

    public static Matrix4f createProjMat() {
        return orthographicMatrix(160.0F, 0.05F, 256.0F);
    }

    public static Matrix4f orthographicMatrix(float halfPlaneLength, float nearPlane, float farPlane) {
        return new Matrix4f(1.0F / halfPlaneLength, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F / halfPlaneLength, 0.0F, 0.0F,
                0.0F, 0.0F, 2.0F / (nearPlane - farPlane), 0.0F, 0.0F, 0.0F, -(farPlane + nearPlane) / (farPlane - nearPlane), 1.0F);
    }

    public static boolean isRenderingShadowMap() {
        return renderingShadowMap;
    }

    public static void setRenderingShadowMap(boolean value) {
        renderingShadowMap = value;
    }
}
