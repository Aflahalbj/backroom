package com.backrooms.entity.ai.goals;

import com.backrooms.component.PlayerComponent;
import com.backrooms.component.SkinWalkerComponent;
import com.backrooms.entity.custom.SkinWalkerEntity;
import com.backrooms.sound.ModSounds;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;

/** Port dari {@code com.sp.entity.ai.goals.FinalFormIdleGoal}: wujud asli diam, mengendus, menunggu pemain bersuara/bergerak. */
public class FinalFormIdleGoal extends Goal {
    private final SkinWalkerEntity entity;
    private final SkinWalkerComponent component;
    private final int chance;
    private int idleActionCoolDown;
    private final int maxIdleActionCoolDown;

    public FinalFormIdleGoal(SkinWalkerEntity entity, int chance, int cooldown) {
        this.entity = entity;
        this.component = entity.getSkinWalkerComponent();
        this.chance = chance;
        this.maxIdleActionCoolDown = reducedTickDelay(cooldown);
    }

    @Override
    public boolean canUse() {
        return this.component.isInTrueForm() && !this.component.isNoticing() && this.entity.getTarget() == null;
    }

    @Override
    public void start() {
        this.component.setIdle(true);
    }

    @Override
    public void stop() {
        this.component.setIdle(false);
    }

    @Override
    public void tick() {
        if (!this.entity.level().isClientSide) {
            // Sama dengan aslinya: TargetPredicate.DEFAULT ikut diubah (objeknya dipakai bersama).
            for (Player player : this.entity.level().getNearbyPlayers(
                    TargetingConditions.DEFAULT.ignoreInvisibilityTesting().ignoreLineOfSight().range(100.0D).selector(EntitySelector.NO_CREATIVE_OR_SPECTATOR::test),
                    this.entity, this.entity.getBoundingBox().inflate(100.0D))) {
                PlayerComponent playerComponent = PlayerComponent.get(player);
                if (playerComponent.isVisibleToEntity()) {
                    this.entity.noticePlayer(player);
                    return;
                }
            }
        }

        if (this.idleActionCoolDown > 0) {
            --this.idleActionCoolDown;
        } else if (this.entity.getRandom().nextInt(reducedTickDelay(this.chance)) == 0) {
            if (this.entity.getRandom().nextBoolean()) {
                this.entity.playSound(ModSounds.event("skinwalker_ambience"), 10.0F, 1.0F);
            } else {
                this.entity.playSound(ModSounds.event("skinwalker_sniff"), 10.0F, 1.0F);
            }
            this.idleActionCoolDown = this.maxIdleActionCoolDown;
        }
    }
}
