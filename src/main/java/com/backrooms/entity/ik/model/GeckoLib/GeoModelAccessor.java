package com.backrooms.entity.ik.model.GeckoLib;

import com.backrooms.entity.ik.model.BoneAccessor;
import com.backrooms.entity.ik.model.ModelAccessor;
import com.backrooms.entity.ik.util.PrAnCommonClass;
import java.util.Optional;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;

/** Port dari {@code com.sp.entity.ik.model.GeckoLib.GeoModelAccessor}. */
public class GeoModelAccessor implements ModelAccessor {
    private final GeoModel<? extends GeoAnimatable> model;

    public GeoModelAccessor(GeoModel<? extends GeoAnimatable> model) {
        this.model = model;
    }

    @Override
    public Optional<BoneAccessor> getBone(String boneName) {
        Optional<GeoBone> optionalGeoBone = this.model.getBone(boneName);
        if (optionalGeoBone.isEmpty()) {
            PrAnCommonClass.throwInDevOnly(new IllegalArgumentException("Bone not found: " + boneName));
            return Optional.empty();
        }
        GeoBone bone = optionalGeoBone.get();
        return Optional.of((BoneAccessor) bone);
    }
}
