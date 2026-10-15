package com.backrooms.entity.ik.model;

import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Port dari {@code com.sp.entity.ik.model.BoneAccessor}. */
public interface BoneAccessor {
    Vec3 getPosition();

    void moveTo(Vec3 to, @Nullable Vec3 facing, Entity entity);

    List<BoneAccessor> getChildren();
}
