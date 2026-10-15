package com.backrooms.client.skinwalker;

import com.backrooms.entity.ik.components.IKAnimatable;
import com.backrooms.entity.ik.util.PrAnCommonClass;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/** Port dari {@code com.sp.entity.client.debug.IKDebugRenderLayer} (garis bantu kaki IK, F3+J di dev). */
public class IKDebugRenderLayer<T extends GeoAnimatable> extends GeoRenderLayer<T> {
    public static int getArgb(int alpha, int red, int green, int blue) {
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    public IKDebugRenderLayer(GeoRenderer<T> entityRendererIn) {
        super(entityRendererIn);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        if (PrAnCommonClass.shouldRenderDebugLegs && animatable instanceof IKAnimatable ikAnimatable) {
            ikAnimatable.renderDebug(poseStack, ikAnimatable, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);
        }
    }
}
