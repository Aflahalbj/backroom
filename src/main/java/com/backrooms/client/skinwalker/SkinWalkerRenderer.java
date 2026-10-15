package com.backrooms.client.skinwalker;

import com.backrooms.client.render.ModRenderTypes;
import com.backrooms.component.SkinWalkerComponent;
import com.backrooms.entity.custom.SkinWalkerEntity;
import com.backrooms.entity.ik.model.GeckoLib.GeoModelAccessor;
import com.backrooms.entity.ik.model.GeckoLib.MowzieGeoBone;
import com.backrooms.entity.ik.parts.sever_limbs.ServerLimb;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.ints.IntIntImmutablePair;
import it.unimi.dsi.fastutil.ints.IntIntPair;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.renderer.DynamicGeoEntityRenderer;
import software.bernie.geckolib.util.RenderUtils;

/**
 * Port dari {@code com.sp.entity.client.renderer.SkinWalkerRenderer}:
 * animasi model pemain (jalan, ayun tangan, jongkok, pukul) saat menyamar;
 * di wujud asli kaki laba-laba IK, kepala terdistorsi, dan mata menyala.
 */
public class SkinWalkerRenderer extends DynamicGeoEntityRenderer<SkinWalkerEntity> {
    private final ResourceLocation SPIDER_LEGS_TEXTURE = new ResourceLocation("spb-revamped", "textures/entity/skinwalker/skinwalker_legs_texture.png");
    private final ResourceLocation HEAD_TEXTURE = new ResourceLocation("spb-revamped", "textures/entity/skinwalker/final_form_head_texture.png");
    private final ResourceLocation EYES_TEXTURE = new ResourceLocation("spb-revamped", "textures/entity/skinwalker/skinwalker_eyes.png");
    private final List<String> spiderLegBones = new ArrayList<>();

    public SkinWalkerRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new SkinWalkerModel());
        this.addRenderLayer(new IKDebugRenderLayer<>(this));
        for (int leg = 1; leg <= 4; ++leg) {
            for (int seg = 1; seg <= 4; ++seg) {
                this.spiderLegBones.add("seg" + seg + "_leg" + leg);
            }
        }
    }

    @Override
    public void preRender(PoseStack poseStack, SkinWalkerEntity animatable, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
        float speed = animatable.walkAnimation.speed(partialTick);
        float pos = animatable.walkAnimation.position(partialTick);
        if (speed > 1.0F) {
            speed = 1.0F;
        }

        float h = Mth.rotLerp(partialTick, animatable.yBodyRotO, animatable.yBodyRot);
        float j = Mth.rotLerp(partialTick, animatable.yHeadRotO, animatable.yHeadRot);
        float yaw = j - h;
        float pitch = Mth.lerp(partialTick, animatable.xRotO, animatable.getXRot());
        float animationProgress = this.getAnimationProgress(animatable, partialTick);
        this.animateModel(animatable, pos, speed, animationProgress, -yaw, -pitch, partialTick);
    }

    @Override
    public void render(SkinWalkerEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        if (entity.component.isInTrueForm()) {
            entity.getModelPositions(entity, new GeoModelAccessor(this.model));
        }
    }

    protected void animateModel(SkinWalkerEntity entity, float limbPos, float limbSpeed, float animationProgress, float yaw, float pitch, float partialTicks) {
        Optional<GeoBone> headBone = this.getGeoModel().getBone("head");
        Optional<GeoBone> bodyBone = this.getGeoModel().getBone("body");
        Optional<GeoBone> rightArm = this.getGeoModel().getBone("right_arm");
        Optional<GeoBone> leftArm = this.getGeoModel().getBone("left_arm");
        Optional<GeoBone> rightLeg = this.getGeoModel().getBone("right_leg");
        Optional<GeoBone> leftLeg = this.getGeoModel().getBone("left_leg");
        if (headBone.isPresent() && bodyBone.isPresent() && rightArm.isPresent() && leftArm.isPresent() && rightLeg.isPresent() && leftLeg.isPresent()) {
            GeoBone head = headBone.get();
            GeoBone body = bodyBone.get();
            GeoBone right_arm = rightArm.get();
            GeoBone left_arm = leftArm.get();
            GeoBone right_leg = rightLeg.get();
            GeoBone left_leg = leftLeg.get();
            SkinWalkerComponent component = entity.component;
            float pitchOffset = 0.0F;
            if (component.isInTrueForm()) {
                Vector3f offset = new Vector3f();
                for (Object limbs : component.getIKComponent().getEndPoints()) {
                    ServerLimb limb = (ServerLimb) limbs;
                    offset.add(limb.pos.toVector3f());
                }
                offset.mul(0.25F);
                body.setWorldSpaceMatrix(new Matrix4f().identity().translate(offset.sub(entity.getPosition(partialTicks).toVector3f()).mul(0.2F)));
                pitchOffset = 35.0F;
            }

            if (!component.shouldBeginReveal()) {
                head.setRotZ(0.0F);
                head.setRotY(yaw * ((float) Math.PI / 180F));
                head.setRotX((pitch + pitchOffset) * ((float) Math.PI / 180F));
            }

            if (!component.isInTrueForm()) {
                right_arm.setRotX(Mth.cos(limbPos * 0.6662F + (float) Math.PI) * 2.0F * limbSpeed * 0.5F);
                left_arm.setRotX(Mth.cos(limbPos * 0.6662F) * 2.0F * limbSpeed * 0.5F);
                right_arm.setRotZ(0.0F);
                left_arm.setRotZ(0.0F);
                right_leg.setRotX(Mth.cos(limbPos * 0.6662F) * 1.4F * limbSpeed);
                left_leg.setRotX(Mth.cos(limbPos * 0.6662F + (float) Math.PI) * 1.4F * limbSpeed);
                right_leg.setRotY(0.005F);
                left_leg.setRotY(-0.005F);
                right_leg.setRotZ(0.005F);
                left_leg.setRotZ(-0.005F);
                right_arm.setRotY(0.0F);
                left_arm.setRotY(0.0F);
                this.punch(entity, partialTicks, body, head, right_arm, left_arm);
                if (component.isSneaking()) {
                    body.setRotX(-0.5F);
                    right_arm.setRotX(right_arm.getRotX() - 0.4F);
                    left_arm.setRotX(left_arm.getRotX() - 0.4F);
                    head.setPosY(-3.2F);
                    body.setPosY(-3.2F);
                    right_arm.setPosY(-3.2F);
                    left_arm.setPosY(-3.2F);
                    right_leg.setPosZ(4.0F);
                    left_leg.setPosZ(4.0F);
                } else {
                    right_leg.setPosZ(0.0F);
                    left_leg.setPosZ(0.0F);
                }

                this.swingArm(right_arm, animationProgress, 1.0F);
                this.swingArm(left_arm, animationProgress, -1.0F);
            }
        }
    }

    private void punch(SkinWalkerEntity entity, float tickDelta, GeoBone body, GeoBone head, GeoBone rightArm, GeoBone leftArm) {
        if (!(entity.getAttackAnim(tickDelta) <= 0.0F)) {
            float f = entity.getAttackAnim(tickDelta);
            body.setRotY(Mth.sin(Mth.sqrt(f) * ((float) Math.PI * 2F)) * 0.2F);
            rightArm.setPivotZ(-Mth.sin(body.getRotY()) * 5.0F);
            rightArm.setPivotX(Mth.cos(body.getRotY()) * 5.0F);
            leftArm.setPivotZ(Mth.sin(body.getRotY()) * 5.0F);
            leftArm.setPivotX(-Mth.cos(body.getRotY()) * 5.0F);
            rightArm.setRotY(rightArm.getRotY() + body.getRotY());
            leftArm.setRotY(-(leftArm.getRotY() + body.getRotY()));
            leftArm.setRotX(-(leftArm.getRotX() + body.getRotY()));
            f = 1.0F - entity.getAttackAnim(tickDelta);
            f *= f;
            f *= f;
            f = 1.0F - f;
            float g = Mth.sin(f * (float) Math.PI);
            float h = Mth.sin(entity.getAttackAnim(tickDelta) * (float) Math.PI) * -(-head.getRotX() - 0.7F) * 0.75F;
            rightArm.setRotX(-(rightArm.getRotX() - g * 1.2F - h));
            rightArm.setRotY(-(rightArm.getRotY() + body.getRotY() * 2.0F));
            rightArm.setRotZ(rightArm.getRotZ() + Mth.sin(entity.getAttackAnim(tickDelta) * (float) Math.PI) * -0.4F);
        }
    }

    private void swingArm(GeoBone arm, float animationProgress, float sigma) {
        arm.setRotZ(arm.getRotZ() + sigma * (Mth.cos(animationProgress * 0.09F) * 0.05F + 0.05F));
        arm.setRotX(arm.getRotX() + sigma * Mth.sin(animationProgress * 0.067F) * 0.05F);
    }

    protected float getAnimationProgress(Entity entity, float tickDelta) {
        return (float) entity.tickCount + tickDelta;
    }

    @Override
    protected ResourceLocation getTextureOverrideForBone(GeoBone bone, SkinWalkerEntity animatable, float partialTick) {
        SkinWalkerComponent component = animatable.component;
        String name = bone.getName();
        if (component.isInTrueForm()) {
            if (this.spiderLegBones.contains(name)) {
                return this.SPIDER_LEGS_TEXTURE;
            }
            if (name.equals("head") || name.equals("headwear")) {
                return this.HEAD_TEXTURE;
            }
        }
        return null;
    }

    protected boolean boneRenderOverride(SkinWalkerEntity animatable, PoseStack poseStack, GeoBone bone, MultiBufferSource bufferSource) {
        SkinWalkerComponent component = animatable.component;
        if (component.isInTrueForm() && bone.getName().equals("headwear")) {
            VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.eyes(this.EYES_TEXTURE));
            if (bone.isHidden()) {
                return false;
            }
            for (GeoCube cube : bone.getCubes()) {
                poseStack.pushPose();
                this.renderCube(poseStack, cube, vertexConsumer, 15728640, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
                poseStack.popPose();
            }
            return true;
        }
        return false;
    }

    @Override
    public Vec3 getRenderOffset(SkinWalkerEntity entity, float tickDelta) {
        return entity.component.isSneaking() ? new Vec3(0.0D, -0.125D, 0.0D) : super.getRenderOffset(entity, tickDelta);
    }

    @Override
    public void createVerticesOfQuad(GeoQuad quad, Matrix4f poseState, Vector3f normal, VertexConsumer buffer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        if (this.textureOverride == null) {
            super.createVerticesOfQuad(quad, poseState, normal, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        } else {
            IntIntPair boneTextureSize = this.computeTextureSize(this.textureOverride);
            IntIntPair entityTextureSize = IntIntImmutablePair.of(64, 64);
            if (boneTextureSize == null) {
                super.createVerticesOfQuad(quad, poseState, normal, buffer, packedLight, packedOverlay, red, green, blue, alpha);
            } else {
                for (GeoVertex vertex : quad.vertices()) {
                    Vector4f vector4f = poseState.transform(new Vector4f(vertex.position().x(), vertex.position().y(), vertex.position().z(), 1.0F));
                    float texU = vertex.texU() * (float) entityTextureSize.firstInt() / (float) boneTextureSize.firstInt();
                    float texV = vertex.texV() * (float) entityTextureSize.secondInt() / (float) boneTextureSize.secondInt();
                    buffer.vertex(vector4f.x(), vector4f.y(), vector4f.z(), red, green, blue, alpha, texU, texV, packedOverlay, packedLight, normal.x(), normal.y(), normal.z());
                }
            }
        }
    }

    @Override
    public void renderRecursively(PoseStack poseStack, SkinWalkerEntity animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        if (bone == null) {
            return;
        }
        poseStack.pushPose();
        transform:
        {
            if (bone instanceof MowzieGeoBone mowzieGeoBone && mowzieGeoBone.isForceMatrixTransform() && animatable != null) {
                PoseStack.Pose last = poseStack.last();
                double d0 = animatable.getX();
                double d1 = animatable.getY();
                double d2 = animatable.getZ();
                Matrix4f matrix4f = new Matrix4f();
                matrix4f = matrix4f.translate(0.0F, -0.01F, 0.0F);
                matrix4f = matrix4f.translate((float) (-d0), (float) (-d1), (float) (-d2));
                matrix4f = matrix4f.mul(bone.getWorldSpaceMatrix());
                last.pose().mul(matrix4f);
                last.normal().mul(bone.getWorldSpaceNormal());
                RenderUtils.translateAwayFromPivotPoint(poseStack, bone);
                break transform;
            }

            if (animatable != null) {
                SkinWalkerComponent component = animatable.component;
                if (component.isInTrueForm() && bone.getName().equals("body")) {
                    Matrix4f matrix4f = new Matrix4f();
                    matrix4f = matrix4f.mul(bone.getWorldSpaceMatrix());
                    poseStack.last().pose().mul(matrix4f);
                }
            }

            boolean rotOverride = false;
            if (bone instanceof MowzieGeoBone mowzieGeoBone) {
                rotOverride = mowzieGeoBone.rotationOverride != null;
            }

            RenderUtils.translateMatrixToBone(poseStack, bone);
            RenderUtils.translateToPivotPoint(poseStack, bone);
            if (bone instanceof MowzieGeoBone mowzieGeoBone) {
                if (!mowzieGeoBone.inheritRotation && !mowzieGeoBone.inheritTranslation) {
                    poseStack.last().pose().identity();
                    poseStack.last().pose().mul(this.entityRenderTranslations);
                } else if (!mowzieGeoBone.inheritRotation) {
                    Vector4f t = new Vector4f().mul(poseStack.last().pose());
                    poseStack.last().pose().identity();
                    poseStack.translate(t.x, t.y, t.z);
                } else if (!mowzieGeoBone.inheritTranslation) {
                    MowzieGeoBone.removeMatrixTranslation(poseStack.last().pose());
                    poseStack.last().pose().mul(this.entityRenderTranslations);
                }
            }

            if (rotOverride) {
                MowzieGeoBone mowzieGeoBone = (MowzieGeoBone) bone;
                poseStack.last().pose().mul(mowzieGeoBone.rotationOverride);
                poseStack.last().normal().mul(new Matrix3f(mowzieGeoBone.rotationOverride));
            } else {
                RenderUtils.rotateMatrixAroundBone(poseStack, bone);
            }

            RenderUtils.scaleMatrixForBone(poseStack, bone);
            if (bone.isTrackingMatrices()) {
                Matrix4f poseState = new Matrix4f(poseStack.last().pose());
                Matrix4f localMatrix = RenderUtils.invertAndMultiplyMatrices(poseState, this.entityRenderTranslations);
                bone.setModelSpaceMatrix(RenderUtils.invertAndMultiplyMatrices(poseState, this.modelRenderTranslations));
                bone.setLocalSpaceMatrix(RenderUtils.translateMatrix(localMatrix, this.getRenderOffset(this.animatable, 1.0F).toVector3f()));
                bone.setWorldSpaceMatrix(RenderUtils.translateMatrix(new Matrix4f(localMatrix), this.animatable.position().toVector3f()));
            }

            RenderUtils.translateAwayFromPivotPoint(poseStack, bone);
        }

        this.textureOverride = this.getTextureOverrideForBone(bone, this.animatable, partialTick);
        ResourceLocation texture = this.textureOverride == null ? this.getTextureLocation(this.animatable) : this.textureOverride;
        RenderType renderTypeOverride = this.getRenderTypeOverrideForBone(bone, this.animatable, texture, bufferSource, partialTick);
        if (texture != null && renderTypeOverride == null) {
            renderTypeOverride = this.getRenderType(this.animatable, texture, bufferSource, partialTick);
        }

        if (renderTypeOverride != null) {
            buffer = bufferSource.getBuffer(renderTypeOverride);
        }

        if (!this.boneRenderOverride(animatable, poseStack, bone, bufferSource)) {
            super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        }

        if (renderTypeOverride != null) {
            buffer = bufferSource.getBuffer(this.getRenderType(this.animatable, this.getTextureLocation(this.animatable), bufferSource, partialTick));
        }

        if (!isReRender) {
            this.applyRenderLayersForBone(poseStack, animatable, bone, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);
        }

        this.renderChildBones(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
        poseStack.popPose();
    }

    @Override
    protected @Nullable RenderType getRenderTypeOverrideForBone(GeoBone bone, SkinWalkerEntity animatable, ResourceLocation texturePath, MultiBufferSource bufferSource, float partialTick) {
        return Objects.equals(bone.getName(), "head") && animatable.component.isInTrueForm()
                ? ModRenderTypes.getDistortedEntity(texturePath)
                : super.getRenderTypeOverrideForBone(bone, animatable, texturePath, bufferSource, partialTick);
    }
}
