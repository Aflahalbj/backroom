package com.backrooms.entity.ik.model;

import java.util.Optional;

/** Port dari {@code com.sp.entity.ik.model.ModelAccessor}. */
public interface ModelAccessor {
    Optional<BoneAccessor> getBone(String name);
}
