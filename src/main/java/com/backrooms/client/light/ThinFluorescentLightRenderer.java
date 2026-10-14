package com.backrooms.client.light;

import com.backrooms.block.Lights;
import com.backrooms.block.entity.ThinFluorescentLightBlockEntity;
import com.backrooms.block.pipe.ThinPipeBlock;
import com.backrooms.client.CameraEffect;
import com.backrooms.client.ClientConfig;
import com.backrooms.client.render.ModRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.properties.AttachFace;
import org.joml.Matrix4f;

/**
 * Port dari {@code ThinFluorescentLightBlockEntityRenderer}: tabung neon
 * tipis yang menyala digambar dengan shader
 * {@code spb-revamped:light/fluorescent_light}, diputar mengikuti pemasangannya.
 */
public class ThinFluorescentLightRenderer implements BlockEntityRenderer<ThinFluorescentLightBlockEntity> {

    private static final ResourceLocation SHADER = new ResourceLocation("spb-revamped", "light/fluorescent_light");

    public ThinFluorescentLightRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ThinFluorescentLightBlockEntity entity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        Minecraft client = Minecraft.getInstance();
        boolean blackout = entity.currentState.getValue(Lights.BLACKOUT);
        boolean on = entity.currentState.getValue(Lights.ON);
        ShaderProgram shader = VeilRenderSystem.setShader(SHADER);
        if (shader == null) return;
        if (client.level != null) {
            shader.setFloat("warAngle", CameraEffect.getWarpTimer(client.level));
        }
        if (!blackout && on) {
            Direction facing = entity.currentState.getValue(ThinPipeBlock.FACING);
            AttachFace wall = entity.currentState.getValue(ThinPipeBlock.FACE);
            matrices.translate(0.5D, 0.5D, 0.5D);
            if (wall == AttachFace.CEILING) {
                matrices.mulPose(Axis.YP.rotationDegrees(facing.getOpposite().toYRot()));
            }
            if (wall == AttachFace.WALL) {
                matrices.mulPose(facing.getOpposite().getRotation());
            }
            if (wall == AttachFace.FLOOR) {
                matrices.mulPose(Axis.XP.rotationDegrees(180.0F));
                matrices.mulPose(Axis.YP.rotationDegrees(facing.toYRot()));
            }
            matrices.translate(-0.5D, -0.5D, -0.5D);
            Matrix4f matrix4f = matrices.last().pose();
            shader.bind();
            renderCube(matrix4f, vertexConsumers.getBuffer(ModRenderTypes.FLUORESCENT_LIGHT));
            ShaderProgram.unbind();
        }
    }

    private static void renderCube(Matrix4f matrix, VertexConsumer buffer) {
        FluorescentLightRenderer.renderFace(matrix, buffer, 0.375F, 0.625F, 0.875F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F);
        FluorescentLightRenderer.renderFace(matrix, buffer, 0.375F, 0.625F, 1.0F, 0.875F, 0.0F, 0.0F, 0.0F, 0.0F);
        FluorescentLightRenderer.renderFace(matrix, buffer, 0.625F, 0.625F, 1.0F, 0.875F, 0.0F, 1.0F, 1.0F, 0.0F);
        FluorescentLightRenderer.renderFace(matrix, buffer, 0.375F, 0.375F, 0.875F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F);
        FluorescentLightRenderer.renderFace(matrix, buffer, 0.375F, 0.625F, 0.875F, 0.875F, 0.0F, 0.0F, 1.0F, 1.0F);
        FluorescentLightRenderer.renderFace(matrix, buffer, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
    }

    @Override
    public int getViewDistance() {
        return ClientConfig.lightRenderDistance();
    }
}
