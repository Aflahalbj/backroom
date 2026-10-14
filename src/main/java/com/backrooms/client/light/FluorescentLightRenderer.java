package com.backrooms.client.light;

import com.backrooms.block.Lights;
import com.backrooms.block.entity.FluorescentLightBlockEntity;
import com.backrooms.client.ClientConfig;
import com.backrooms.client.render.ModRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import org.joml.Matrix4f;

/**
 * Port dari {@code FluorescentLightBlockEntityRenderer}: panel neon yang
 * menyala digambar sebagai kubus penuh dengan shader
 * {@code spb-revamped:light/fluorescent_light}.
 */
public class FluorescentLightRenderer implements BlockEntityRenderer<FluorescentLightBlockEntity> {

    public FluorescentLightRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(FluorescentLightBlockEntity entity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        boolean blackout = entity.currentState.getValue(Lights.BLACKOUT);
        boolean on = entity.currentState.getValue(Lights.ON);
        if (!blackout && on) {
            Matrix4f matrix4f = matrices.last().pose();
            renderCube(matrix4f, vertexConsumers.getBuffer(ModRenderTypes.FLUORESCENT_LIGHT));
        }
    }

    private static void renderCube(Matrix4f matrix, VertexConsumer buffer) {
        renderFace(matrix, buffer, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F);
        renderFace(matrix, buffer, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        renderFace(matrix, buffer, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F);
        renderFace(matrix, buffer, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F);
        renderFace(matrix, buffer, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F);
        renderFace(matrix, buffer, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F);
    }

    static void renderFace(Matrix4f matrix, VertexConsumer buffer, float f, float g, float h, float i, float j, float k, float l, float m) {
        buffer.vertex(matrix, f, h, j).endVertex();
        buffer.vertex(matrix, g, h, k).endVertex();
        buffer.vertex(matrix, g, i, l).endVertex();
        buffer.vertex(matrix, f, i, m).endVertex();
    }

    @Override
    public int getViewDistance() {
        return ClientConfig.lightRenderDistance();
    }
}
