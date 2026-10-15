package com.backrooms.entity.ik.components;

import com.backrooms.entity.ik.model.ModelAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/** Port dari {@code com.sp.entity.ik.components.IKModelComponent}. */
public interface IKModelComponent<E extends IKAnimatable<E>> {
    void tickServer(E animatable);

    void tickClient(E animatable, ModelAccessor model);

    void getModelPositions(E animatable, ModelAccessor model);

    void renderDebug(PoseStack poseStack, E animatable, RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay);
}
