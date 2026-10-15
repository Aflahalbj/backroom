package com.backrooms.entity.ik.components;

import com.backrooms.entity.ik.model.ModelAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/** Port dari {@code com.sp.entity.ik.components.IKAnimatable}. */
public interface IKAnimatable<E extends IKAnimatable<E>> {
    List<IKModelComponent<E>> getComponents();

    double getSize();

    default boolean containsComponent(Class<?> type) {
        return !this.getComponents().stream().filter(component -> component.getClass() == type).toList().isEmpty();
    }

    default List<? extends IKModelComponent<E>> getComponentOfType(Class<?> type) {
        return this.getComponents().stream().filter(component -> component.getClass() == type).toList();
    }

    default void addComponent(IKModelComponent<E> component) {
        this.getComponents().add(component);
    }

    default void tickComponentsClient(E animatable, ModelAccessor model) {
        this.getComponents().forEach(component -> component.tickClient(animatable, model));
    }

    default void tickComponentsServer(E animatable) {
        this.getComponents().forEach(component -> component.tickServer(animatable));
    }

    default void getModelPositions(E animatable, ModelAccessor model) {
        this.getComponents().forEach(component -> component.getModelPositions(animatable, model));
    }

    default void renderDebug(PoseStack poseStack, E animatable, RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        this.getComponents().forEach(component -> component.renderDebug(poseStack, animatable, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay));
    }
}
