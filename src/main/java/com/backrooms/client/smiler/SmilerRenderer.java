package com.backrooms.client.smiler;

import com.backrooms.entity.custom.SmilerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Port dari {@code com.sp.entity.client.renderer.SmilerRenderer}: wajah yang selalu menghadap kamera. */
public class SmilerRenderer extends MobRenderer<SmilerEntity, SmilerModel<SmilerEntity>> {
    private static final ResourceLocation defaultTexture = new ResourceLocation("spb-revamped", "textures/entity/smiler/smiler.png");
    private static final ResourceLocation texture1 = new ResourceLocation("spb-revamped", "textures/entity/smiler/smiler1.png");
    private static final ResourceLocation texture2 = new ResourceLocation("spb-revamped", "textures/entity/smiler/smiler2.png");

    public SmilerRenderer(EntityRendererProvider.Context context) {
        super(context, new SmilerModel<>(context.bakeLayer(SmilerModel.LAYER)), 0.0F);
    }

    @Override
    public void render(SmilerEntity mobEntity, float f, float g, PoseStack matrixStack, MultiBufferSource vertexConsumerProvider, int i) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        matrixStack.translate(0.0F, 1.0F, 0.0F);
        float angle = lookAtEntityAroundYAxis(mobEntity.getEyePosition(), camera.getPosition());
        matrixStack.mulPose(new Quaternionf().rotateXYZ(0.0F, (float) Math.toRadians(angle), 0.0F));
        matrixStack.translate(0.0F, -1.0F, 0.0F);
        super.render(mobEntity, f, g, matrixStack, vertexConsumerProvider, i);
    }

    @Override
    public ResourceLocation getTextureLocation(SmilerEntity entity) {
        return switch (entity.getRandomTexture()) {
            case 1 -> texture1;
            case 2 -> texture2;
            default -> defaultTexture;
        };
    }

    public static float lookAtEntityAroundYAxis(Vec3 position1, Vec3 position2) {
        double d = position2.x() - position1.x();
        double e = position2.z() - position1.z();
        float h = -((float) (Mth.atan2(e, d) * 180.0D / (double) (float) Math.PI)) - 90.0F;
        return h - 180.0F;
    }
}
