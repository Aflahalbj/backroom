package com.backrooms.entity.ik.parts.ik_chains;

import com.backrooms.entity.ik.parts.Segment;
import com.backrooms.entity.ik.util.MathUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Port dari {@code com.sp.entity.ik.parts.ik_chains.EntityLeg}. */
public class EntityLeg extends AngleConstraintIKChain {
    public Entity entity;

    public EntityLeg(double... lengths) {
        super(lengths);
    }

    public EntityLeg(Segment... segments) {
        super(segments);
    }

    @Override
    public Vec3 getReferencePoint() {
        Vec3 referencePoint = MathUtil.getFlatRotationVector(this.entity.getVisualRotationYInDegrees() + 90.0F);
        return this.getFirst().getPosition().add(referencePoint.scale(100.0D));
    }

    @Override
    public Vec3 getStretchingPos(Vec3 target, Vec3 base) {
        return base.add(MathUtil.getFlatRotationVector(this.entity).scale(this.getMaxLength() * 2.0D));
    }

    @Override
    public Vec3 getDownNormalOnLegPlane() {
        Vec3 baseRotated = this.getFirst().getPosition().yRot(-this.entity.getVisualRotationYInDegrees());
        Vec3 targetRotated = this.endJoint.yRot(-this.entity.getVisualRotationYInDegrees());
        Vec3 flatRotatedBase = new Vec3(baseRotated.x, baseRotated.y, 0.0D);
        Vec3 flatRotatedTarget = new Vec3(targetRotated.x, targetRotated.y, 0.0D);
        Vec3 flatBase = flatRotatedBase.yRot(this.entity.getVisualRotationYInDegrees());
        Vec3 flatTarget = flatRotatedTarget.yRot(this.entity.getVisualRotationYInDegrees());
        return flatTarget.subtract(flatBase).normalize();
    }

    public Vec3 getConstrainedPosForRootSegment() {
        return this.getConstrainedPosForRootSegment(this.getDownNormalOnLegPlane());
    }
}
