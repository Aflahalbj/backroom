package com.backrooms.entity.ai.goals;

import com.backrooms.component.SkinWalkerComponent;
import com.backrooms.entity.custom.SkinWalkerEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

/** Port dari {@code com.sp.entity.ai.goals.SkinWalkerActiveTarget}. */
public class SkinWalkerActiveTarget extends NearestAttackableTargetGoal<Player> {
    private final SkinWalkerComponent component;

    public SkinWalkerActiveTarget(SkinWalkerEntity entity) {
        super(entity, Player.class, false);
        this.component = entity.getSkinWalkerComponent();
    }

    @Override
    public boolean canUse() {
        return !this.component.isInTrueForm() && !this.component.shouldBeginReveal() ? super.canUse() : false;
    }
}
