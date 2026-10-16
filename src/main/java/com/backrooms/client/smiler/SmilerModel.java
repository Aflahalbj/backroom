package com.backrooms.client.smiler;

import com.backrooms.entity.custom.SmilerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Port dari {@code com.sp.entity.client.model.SmilerModel}: satu bidang datar 44x44.
 *
 * <p>Aslinya opacity {@code SmilerComponent} dimasukkan lewat mixin ke warna
 * {@code LivingEntityRenderer.render}; di sini warnanya dikali opacity di model.
 */
public class SmilerModel<T extends SmilerEntity> extends HierarchicalModel<T> {
    /** {@code ModModelLayers.SMILER}. */
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation("spb-revamped", "smiler"), "main");

    private final ModelPart bone;
    private float opacity = 1.0F;

    public SmilerModel(ModelPart root) {
        super(RenderType::eyes);
        this.bone = root.getChild("bone");
    }

    public static LayerDefinition getTexturedModelData() {
        MeshDefinition modelData = new MeshDefinition();
        PartDefinition modelPartData = modelData.getRoot();
        modelPartData.addOrReplaceChild("bone", CubeListBuilder.create().texOffs(1, 0).addBox(-22.0F, -22.0F, 0.0F, 44.0F, 44.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 2.0F, 0.0F));
        return LayerDefinition.create(modelData, 96, 96);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.opacity = entity.getOpacity();
    }

    @Override
    public void renderToBuffer(PoseStack matrices, VertexConsumer vertexConsumer, int light, int overlay, float red, float green, float blue, float alpha) {
        this.bone.render(matrices, vertexConsumer, light, overlay, red * this.opacity, green * this.opacity, blue * this.opacity, alpha * this.opacity);
    }

    @Override
    public ModelPart root() {
        return this.bone;
    }
}
