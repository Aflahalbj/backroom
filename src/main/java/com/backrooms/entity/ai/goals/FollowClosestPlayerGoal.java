package com.backrooms.entity.ai.goals;

import com.backrooms.component.SkinWalkerComponent;
import com.backrooms.entity.custom.SkinWalkerEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/** Port dari {@code com.sp.entity.ai.goals.FollowClosestPlayerGoal}: menyamar, mengikuti pemain terdekat. */
public class FollowClosestPlayerGoal extends Goal {
    private final SkinWalkerEntity entity;
    private final SkinWalkerComponent component;
    private final float minDistance;
    private final float maxDistance;
    private Player target;
    private double speed;

    public FollowClosestPlayerGoal(SkinWalkerEntity entity, float minDistance, float maxDistance, float speed) {
        this.entity = entity;
        this.component = entity.getSkinWalkerComponent();
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
        this.speed = speed;
    }

    @Override
    public boolean canUse() {
        if (!this.component.isInTrueForm() && !this.component.shouldBeginReveal() && !this.component.isCurrentlyActingNatural()) {
            Player player = this.entity.level().getNearestPlayer(this.entity, 200.0D);
            if (player != null && !this.isTooClose(player) && !player.isSpectator() && !player.isCreative()) {
                this.target = player;
                return true;
            }
        }
        return false;
    }

    @Override
    public void start() {
        this.component.setFollowTarget(this.target);
        this.component.setShouldActNatural(false);
    }

    @Override
    public boolean canContinueToUse() {
        return this.isTooClose(this.target) ? false : super.canContinueToUse();
    }

    @Override
    public void stop() {
        if (this.target == null) {
            this.component.setFollowTarget(null);
        }
        this.target = null;
        this.component.setShouldActNatural(true);
        this.entity.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.isTooFar(this.target)) {
            this.entity.setSprinting(true);
        } else {
            this.entity.setSprinting(false);
        }
        this.entity.getNavigation().moveTo(this.target, this.speed);
    }

    private boolean isTooClose(Entity entity) {
        return this.entity.distanceToSqr(entity) < (double) (this.minDistance * this.minDistance);
    }

    private boolean isTooFar(Entity entity) {
        return this.entity.distanceToSqr(entity) > (double) (this.maxDistance * this.maxDistance);
    }
}
