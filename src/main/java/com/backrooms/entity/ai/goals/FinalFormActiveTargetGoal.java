package com.backrooms.entity.ai.goals;

import com.backrooms.component.SkinWalkerComponent;
import com.backrooms.entity.custom.SkinWalkerEntity;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;

/** Port dari {@code com.sp.entity.ai.goals.FinalFormActiveTargetGoal}. */
public class FinalFormActiveTargetGoal extends TargetGoal {
    private final SkinWalkerComponent component;

    public FinalFormActiveTargetGoal(SkinWalkerEntity entity) {
        super(entity, true, false);
        this.component = entity.getSkinWalkerComponent();
        this.unseenMemoryTicks = 100;
    }

    @Override
    public boolean canUse() {
        if (this.component.isInTrueForm() && !this.component.shouldBeginReveal()) {
            if (this.mob.getTarget() != null) {
                this.targetMob = this.mob.getTarget();
                return true;
            }

            // Sama dengan aslinya: TargetPredicate.DEFAULT ikut diubah (objeknya dipakai bersama).
            List<Player> playerEntityList = this.mob.level().getNearbyPlayers(
                    TargetingConditions.DEFAULT.ignoreInvisibilityTesting().ignoreLineOfSight().range(100.0D).selector(EntitySelector.NO_CREATIVE_OR_SPECTATOR::test),
                    this.mob, this.mob.getBoundingBox().inflate(100.0D));
            playerEntityList.sort(Comparator.comparingDouble(player -> -this.mob.position().distanceToSqr(player.position().x, player.position().y, player.position().z)));

            for (Player player : playerEntityList) {
                if (this.mob.hasLineOfSight(player)) {
                    this.targetMob = player;
                    ((SkinWalkerEntity) this.mob).beginTargeting(player);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return super.canContinueToUse() && this.canUse();
    }

    @Override
    public void tick() {
        if (this.targetMob != null) {
            this.component.setLastKnownTargetLocation(this.targetMob.blockPosition());
        }
    }
}
