package com.backrooms.entity.ai.goals;

import com.backrooms.component.SkinWalkerComponent;
import com.backrooms.entity.custom.SkinWalkerEntity;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;

/** Port dari {@code com.sp.entity.ai.goals.FinalFormWanderGoal}. */
public class FinalFormWanderGoal extends WaterAvoidingRandomStrollGoal {
    private final SkinWalkerComponent component;

    public FinalFormWanderGoal(SkinWalkerEntity entity, double d) {
        super(entity, d);
        this.component = entity.getSkinWalkerComponent();
    }

    @Override
    public boolean canUse() {
        return super.canUse() && this.component.isInTrueForm() && this.component.isIdle();
    }
}
