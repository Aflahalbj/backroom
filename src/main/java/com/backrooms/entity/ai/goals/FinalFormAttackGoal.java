package com.backrooms.entity.ai.goals;

import com.backrooms.component.SkinWalkerComponent;
import com.backrooms.entity.custom.SkinWalkerEntity;
import com.backrooms.mixin.MeleeAttackGoalAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/** Port dari {@code com.sp.entity.ai.goals.FinalFormAttackGoal}. */
public class FinalFormAttackGoal extends MeleeAttackGoal {
    private final SkinWalkerComponent component;

    public FinalFormAttackGoal(SkinWalkerEntity entity) {
        super(entity, 1.15D, false);
        this.component = entity.getSkinWalkerComponent();
    }

    @Override
    public boolean canUse() {
        if (this.component.isInTrueForm()) {
            MeleeAttackGoalAccessor self = (MeleeAttackGoalAccessor) this;
            long l = this.mob.level().getGameTime();
            if (l - self.getLastCanUseCheck() < 20L) {
                return false;
            }
            self.setLastCanUseCheck(l);
            LivingEntity livingEntity = this.mob.getTarget();
            if (livingEntity == null) {
                this.moveToLastKnownLocation();
                return false;
            } else if (!livingEntity.isAlive()) {
                this.moveToLastKnownLocation();
                return false;
            } else {
                self.setPath(this.mob.getNavigation().createPath(livingEntity, 0));
                return self.getPath() != null || this.getAttackReachSqr(livingEntity) >= this.mob.distanceToSqr(livingEntity.getX(), livingEntity.getY(), livingEntity.getZ());
            }
        }
        return false;
    }

    private void moveToLastKnownLocation() {
        BlockPos pos = this.component.getLastKnownTargetLocation();
        if (pos != null) {
            this.mob.getNavigation().moveTo(pos.getX(), pos.getY(), pos.getZ(), 1.0D);
        }
    }
}
