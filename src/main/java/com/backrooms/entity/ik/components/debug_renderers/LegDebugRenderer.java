package com.backrooms.entity.ik.components.debug_renderers;

import com.backrooms.entity.ik.components.IKAnimatable;
import com.backrooms.entity.ik.components.IKLegComponent;
import com.backrooms.entity.ik.parts.Segment;
import com.backrooms.entity.ik.parts.ik_chains.EntityLeg;
import com.backrooms.entity.ik.parts.ik_chains.EntityLegWithFoot;
import com.backrooms.entity.ik.parts.ik_chains.IKChain;
import com.backrooms.entity.ik.parts.sever_limbs.ServerLimb;
import com.backrooms.entity.ik.util.MathUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Port dari {@code com.sp.entity.ik.components.debug_renderers.LegDebugRenderer} (F3+J di dev). */
public class LegDebugRenderer<E extends IKAnimatable<E>, C extends IKChain> extends IKChainDebugRenderer<E, IKLegComponent<C, E>> {
    @Override
    public void renderDebug(IKLegComponent<C, E> component, E animatable, PoseStack poseStack, RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        super.renderDebug(component, animatable, poseStack, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);

        for (C limb : component.getLimbs()) {
            if (!(animatable instanceof Entity entity)) {
                return;
            }

            Vec3 entityPos = entity.position();
            this.renderLeg(poseStack, bufferSource, limb, entity);

            for (ServerLimb endPoint : component.getEndPoints()) {
                Vec3 limbOffset = endPoint.baseOffset.scale(component.getScale());
                if (component.hasMovedOverLastTick(entity)) {
                    limbOffset = limbOffset.add(0.0D, 0.0D, component.getSettings().get(0).stepInFront() * component.getScale());
                }

                limbOffset = limbOffset.yRot((float) Math.toRadians(-entity.getVisualRotationYInDegrees()));
                Vec3 rotatedLimbOffset = limbOffset.add(entity.position());
                BlockHitResult rayCastResult = IKLegComponent.rayCastToGround(rotatedLimbOffset, entity, ClipContext.Fluid.NONE);
                Vec3 rayCastHitPos = rayCastResult.getLocation();
                double distance = endPoint.target.distanceTo(rayCastHitPos);
                if (distance < 0.1D) {
                    distance = 0.0D;
                }

                if (distance != 0.0D) {
                    IKDebugRenderer.drawLine(poseStack, bufferSource, entityPos, endPoint.getPos(), endPoint.target, 255, 100, 255, 127);
                }

                IKDebugRenderer.drawBox(poseStack, bufferSource, endPoint.getPos(), entity, endPoint.isGrounded() ? 0 : 255, endPoint.isGrounded() ? 255 : 0, 0, 127);
                IKDebugRenderer.drawBox(poseStack, bufferSource, endPoint.oldTarget, entity, 0, 255, 255, 127);
                IKDebugRenderer.drawBox(poseStack, bufferSource, rayCastHitPos, entity, 0, 0, 255, 127);
            }
        }
    }

    private void renderLeg(PoseStack poseStack, MultiBufferSource bufferSource, C chain, Entity entity) {
        Vec3 entityPos = entity.position();
        for (int i = 0; i < chain.getJoints().size() - 1; ++i) {
            if (i > 0) {
                this.drawAngleConstraints(i, chain, entity, poseStack, bufferSource);
            } else {
                this.drawAngleConstraintsForBase(chain, entity, poseStack, bufferSource);
            }
        }

        if (chain instanceof EntityLegWithFoot entityLegWithFoot) {
            Vec3 footPos = entityLegWithFoot.foot.getPosition();
            IKDebugRenderer.drawLineToBox(poseStack, bufferSource, entityPos, chain.endJoint, footPos, entity, 255, 165, 0, 127);
            Vec3 angleConstraint = entityLegWithFoot.getFootPosition(entityLegWithFoot.foot.angleSize);
            Vec3 referencePoint = entityLegWithFoot.getFootPosition(0.0D);
            IKDebugRenderer.drawLine(poseStack, bufferSource, entityPos, chain.endJoint, angleConstraint, 255, 0, 0, 127);
            IKDebugRenderer.drawLine(poseStack, bufferSource, entityPos, chain.endJoint, referencePoint, 0, 255, 0, 127);
        }
    }

    private void drawAngleConstraintsForBase(C chain, Entity entity, PoseStack matrices, MultiBufferSource vertexConsumers) {
        if (chain instanceof EntityLeg entityLeg) {
            Vec3 entityPos = entity.position();
            Vec3 base = entityLeg.getFirst().getPosition();
            Vec3 referencePoint = entityLeg.rotatePointOnLegPlane(base.add(entityLeg.getDownNormalOnLegPlane()), base, chain.getFirst().angleOffset);
            Vec3 dotBaseDir = referencePoint.subtract(base).normalize();
            Vec3 dotTargetDir = chain.get(1).getPosition().subtract(base).normalize();
            double angle = Math.toDegrees(Math.acos(dotBaseDir.dot(dotTargetDir)));
            double angleDifference = chain.getFirst().angleSize - angle;
            Vec3 rotatedPos = MathUtil.rotatePointOnAPlaneAround(chain.getFirst().getPosition().add(entityLeg.getDownNormalOnLegPlane()), chain.getFirst().getPosition(), chain.getFirst().angleSize, entityLeg.getLegPlane());
            Vec3 rotatedPos2 = MathUtil.rotatePointOnAPlaneAround(chain.getFirst().getPosition().add(entityLeg.getDownNormalOnLegPlane()), chain.getFirst().getPosition(), -chain.getFirst().angleSize, entityLeg.getLegPlane());
            Vec3 newPos = MathUtil.rotatePointOnAPlaneAround(chain.get(1).getPosition(), chain.getFirst().getPosition(), angleDifference, entityLeg.getLegPlane());
            IKDebugRenderer.drawLine(matrices, vertexConsumers, entityPos, chain.getFirst().getPosition(), rotatedPos, 255, 0, 0, 127);
            IKDebugRenderer.drawLine(matrices, vertexConsumers, entityPos, chain.getFirst().getPosition(), rotatedPos2, 0, 255, 0, 127);
            IKDebugRenderer.drawLine(matrices, vertexConsumers, entityPos, chain.getFirst().getPosition(), newPos, 0, 0, 255, 127);
            IKDebugRenderer.drawLine(matrices, vertexConsumers, entityPos, chain.getFirst().getPosition(), chain.getFirst().getPosition().add(entityLeg.getDownNormalOnLegPlane()), 180, 180, 180, 127);
            IKDebugRenderer.drawLine(matrices, vertexConsumers, entityPos, chain.getFirst().getPosition(), chain.getFirst().getPosition().add(entityLeg.getLegPlane()), 12, 12, 12, 127);
        }
    }

    private void drawAngleConstraints(int i, C chain, Entity entity, PoseStack matrices, MultiBufferSource vertexConsumers) {
        if (chain instanceof EntityLeg entityLeg) {
            Vec3 entityPos = entity.position();
            Segment currentSegment = chain.get(i);
            List<Vec3> positions = this.getConstrainedPositions(chain.get(i - 1).getPosition(), currentSegment, chain.getJoints().get(i + 1), entityLeg);
            IKDebugRenderer.drawLine(matrices, vertexConsumers, entityPos, currentSegment.getPosition(), positions.get(0), 255, 0, 0, 127);
            IKDebugRenderer.drawLine(matrices, vertexConsumers, entityPos, currentSegment.getPosition(), positions.get(1), 180, 180, 180, 127);
            IKDebugRenderer.drawLine(matrices, vertexConsumers, entityPos, currentSegment.getPosition(), positions.get(2), 0, 255, 0, 127);
        }
    }

    private List<Vec3> getConstrainedPositions(Vec3 reference, Segment middle, Vec3 endpoint, EntityLeg chain) {
        Vec3 normal = chain.getLegPlane();
        Vec3 referencePoint = MathUtil.rotatePointOnAPlaneAround(reference, middle.getPosition(), middle.angleOffset, normal);
        double angle = Math.toDegrees(MathUtil.calculateAngle(middle.getPosition(), endpoint, referencePoint));
        double angleDelta = middle.angleSize - angle;
        Vec3 newPos = MathUtil.rotatePointOnAPlaneAround(endpoint, middle.getPosition(), angleDelta, normal);
        Vec3 otherNewPos = MathUtil.rotatePointOnAPlaneAround(endpoint, middle.getPosition(), angleDelta - middle.angleSize * 2.0D, normal);
        Vec3 middlePos = MathUtil.rotatePointOnAPlaneAround(endpoint, middle.getPosition(), angleDelta - middle.angleSize, normal);
        return List.of(newPos, middlePos, otherNewPos);
    }
}
