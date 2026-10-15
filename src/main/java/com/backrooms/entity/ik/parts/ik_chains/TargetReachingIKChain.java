package com.backrooms.entity.ik.parts.ik_chains;

import com.backrooms.entity.ik.parts.Segment;
import com.backrooms.entity.ik.util.MathUtil;
import net.minecraft.world.phys.Vec3;

/** Port dari {@code com.sp.entity.ik.parts.ik_chains.TargetReachingIKChain} (kaki Skinwalker). */
public class TargetReachingIKChain extends StretchingIKChain {
    public TargetReachingIKChain(double... lengths) {
        super(lengths);
    }

    public TargetReachingIKChain(Segment... segments) {
        super(segments);
    }

    @Override
    public Vec3 getStretchingPos(Vec3 target, Vec3 base) {
        Vec3 flatTargetDir = MathUtil.convertToFlatVector(target.subtract(base)).normalize();
        return base.add(flatTargetDir.scale(this.getMaxLength())).add(0.0D, this.getMaxLength(), 0.0D);
    }
}
