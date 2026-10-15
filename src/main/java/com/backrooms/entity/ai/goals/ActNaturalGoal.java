package com.backrooms.entity.ai.goals;

import com.backrooms.component.SkinWalkerComponent;
import com.backrooms.entity.custom.SkinWalkerEntity;
import java.util.Random;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/** Port dari {@code com.sp.entity.ai.goals.ActNaturalGoal}: Skinwalker "berlagak" seperti pemain. */
public class ActNaturalGoal extends Goal {
    private final RandomSource random = RandomSource.create(7585889L);
    private final Random rand = new Random();
    private final SkinWalkerEntity entity;
    private final SkinWalkerComponent component;
    private Integer randomAction;
    private int actCooldown;
    private int currentActionCount = 0;
    private int currentActionCooldown = 0;
    private boolean currentActionSwitch = false;
    private Vec3 randLookDir;

    public ActNaturalGoal(SkinWalkerEntity entity) {
        this.entity = entity;
        this.component = entity.getSkinWalkerComponent();
    }

    @Override
    public boolean canUse() {
        return this.component.shouldActNatural() && !this.component.isInTrueForm() && !this.component.shouldBeginReveal();
    }

    @Override
    public void start() {
        this.actCooldown = this.adjustedTickDelay(40);
    }

    @Override
    public void stop() {
        this.randomAction = null;
        this.component.setSneaking(false);
        this.currentActionCooldown = 0;
        this.currentActionCount = 0;
        this.actCooldown = 0;
    }

    @Override
    public void tick() {
        if (!this.entity.level().isClientSide) {
            if (this.actCooldown > 0) {
                --this.actCooldown;
                return;
            }

            if (this.randomAction == null) {
                this.randomAction = this.random.nextIntBetweenInclusive(1, 4);
            }

            this.component.setCurrentlyActingNatural(true);
            switch (this.randomAction) {
                case 1 -> this.sneakTick();
                case 2 -> this.strafeTick();
                case 3 -> this.lookAndPunchTick();
                case 4 -> this.lookMultTick();
                default -> {
                }
            }
        }
    }

    private void sneakTick() {
        if (this.currentActionCooldown <= 0) {
            if (this.currentActionCount < 2 && !this.component.isSneaking()) {
                this.component.setSneaking(true);
                ++this.currentActionCount;
                this.currentActionCooldown = 0;
            } else if (this.component.isSneaking()) {
                this.component.setSneaking(false);
                this.currentActionCooldown = 0;
            } else {
                this.component.setCurrentlyActingNatural(false);
                this.randomAction = null;
                this.currentActionCount = 0;
                this.setRandomActCoolDown();
            }
        } else {
            --this.currentActionCooldown;
        }
    }

    private void strafeTick() {
        if (this.currentActionCooldown <= 0) {
            if (this.currentActionCount < 8 && !this.currentActionSwitch) {
                this.entity.xxa = 0.2F;
                this.currentActionSwitch = true;
                ++this.currentActionCount;
                this.currentActionCooldown = 2;
            } else if (this.currentActionSwitch) {
                this.entity.xxa = -0.2F;
                this.currentActionSwitch = false;
                this.currentActionCooldown = 2;
                ++this.currentActionCount;
            } else {
                this.component.setCurrentlyActingNatural(false);
                this.entity.xxa = 0.0F;
                this.currentActionSwitch = false;
                this.randomAction = null;
                this.currentActionCount = 0;
                this.setRandomActCoolDown();
            }
        } else {
            --this.currentActionCooldown;
        }
    }

    private void punchPlayer() {
        this.entity.swing(InteractionHand.MAIN_HAND);
        this.entity.doHurtTarget(this.component.getFollowTarget());
        this.component.setCurrentlyActingNatural(false);
        this.randomAction = null;
        this.setRandomActCoolDown();
    }

    private void lookAndPunchTick() {
        this.component.setShouldLookAtTarget(false);
        if (this.randLookDir == null) {
            float randX = this.rand.nextFloat(-45.0F, 45.0F);
            float randY = this.rand.nextFloat(-180.0F, 180.0F);
            this.randLookDir = this.eulerToVector(randX, randY);
            ((SkinWalkerEntity.SkinWalkerLookControl) this.entity.getLookControl()).lookAt(this.randLookDir, 5);
        }

        if (this.currentActionCooldown <= 0) {
            if (this.currentActionCount < 4) {
                this.entity.swing(InteractionHand.MAIN_HAND);
                ++this.currentActionCount;
                this.currentActionCooldown = 2;
            } else {
                this.component.setCurrentlyActingNatural(false);
                this.component.setShouldLookAtTarget(true);
                this.randomAction = null;
                this.randLookDir = null;
                this.currentActionCount = 0;
                this.setRandomActCoolDown();
            }
        } else {
            --this.currentActionCooldown;
        }
    }

    private void lookMultTick() {
        this.component.setShouldLookAtTarget(false);
        if (this.currentActionCooldown <= 0) {
            if (this.currentActionCount < 5) {
                float randX = this.rand.nextFloat(-45.0F, 45.0F);
                float randY = this.rand.nextFloat(-180.0F, 180.0F);
                ((SkinWalkerEntity.SkinWalkerLookControl) this.entity.getLookControl()).lookAt(this.eulerToVector(randX, randY), this.random.nextIntBetweenInclusive(4, 9));
                int shouldPunch = this.random.nextIntBetweenInclusive(1, 3);
                if (shouldPunch == 1) {
                    this.entity.swing(InteractionHand.MAIN_HAND);
                }
                ++this.currentActionCount;
                this.currentActionCooldown = this.random.nextIntBetweenInclusive(6, 12);
            } else {
                this.component.setCurrentlyActingNatural(false);
                this.component.setShouldLookAtTarget(true);
                this.randomAction = null;
                this.currentActionCount = 0;
                this.setRandomActCoolDown();
            }
        } else {
            --this.currentActionCooldown;
        }
    }

    private Vec3 eulerToVector(float pitch, float yaw) {
        float x = Mth.cos(yaw) * Mth.cos(pitch);
        float y = Mth.sin(yaw) * Mth.cos(pitch);
        float z = Mth.sin(pitch);
        return new Vec3(x, y, z).scale(180D / Math.PI);
    }

    private void setRandomActCoolDown() {
        this.actCooldown = this.adjustedTickDelay(this.random.nextIntBetweenInclusive(60, 150));
    }
}
