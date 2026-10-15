package com.backrooms.mixin;

import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Field privat {@link MeleeAttackGoal} yang dibuka access widener mod aslinya untuk {@code FinalFormAttackGoal}. */
@Mixin(MeleeAttackGoal.class)
public interface MeleeAttackGoalAccessor {
    @Accessor("lastCanUseCheck")
    long getLastCanUseCheck();

    @Accessor("lastCanUseCheck")
    void setLastCanUseCheck(long value);

    @Accessor("path")
    Path getPath();

    @Accessor("path")
    void setPath(Path path);
}
