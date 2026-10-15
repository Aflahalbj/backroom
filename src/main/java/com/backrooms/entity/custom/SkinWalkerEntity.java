package com.backrooms.entity.custom;

import com.backrooms.component.PlayerComponent;
import com.backrooms.component.SkinWalkerComponent;
import com.backrooms.entity.ai.SlightlyBetterMobNavigation;
import com.backrooms.entity.ai.goals.ActNaturalGoal;
import com.backrooms.entity.ai.goals.FinalFormActiveTargetGoal;
import com.backrooms.entity.ai.goals.FinalFormAttackGoal;
import com.backrooms.entity.ai.goals.FinalFormIdleGoal;
import com.backrooms.entity.ai.goals.FinalFormWanderGoal;
import com.backrooms.entity.ai.goals.FollowClosestPlayerGoal;
import com.backrooms.entity.ai.goals.SkinWalkerActiveTarget;
import com.backrooms.entity.ai.goals.SpeakGoal;
import com.backrooms.entity.ik.components.IKAnimatable;
import com.backrooms.entity.ik.components.IKModelComponent;
import com.backrooms.network.ModNetwork;
import com.backrooms.sound.ModSounds;
import com.backrooms.world.BackroomsLevel;
import com.backrooms.world.events.LightState;
import com.backrooms.world.events.WorldEvents;
import foundry.veil.api.client.util.Easings;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Port dari {@code com.sp.entity.custom.SkinWalkerEntity}.
 *
 * <p>Satu-satunya penyesuaian: di aslinya "pemain di dunia" = semua pemain di
 * dimensi Level 0. Di port ini beberapa room berbagi dimensi itu, jadi yang
 * dihitung pemain di salinan level tempat Skinwalker berada (lihat
 * {@link WorldEvents#players()}). Karena posisinya baru diketahui setelah
 * entity dibuat, target awal dan {@code maxSuspicion} dihitung di tick pertama
 * di server (aslinya di konstruktor).
 */
public class SkinWalkerEntity extends Monster implements GeoEntity, GeoAnimatable, IKAnimatable<SkinWalkerEntity> {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    public static final RawAnimation TRANSITION = RawAnimation.begin().then("transition", Animation.LoopType.PLAY_ONCE);
    public SkinWalkerComponent component;
    public List<IKModelComponent<SkinWalkerEntity>> components = new ArrayList<>();
    private int maxSuspicion = -1;
    private Entity prevTarget;
    private int ticks;
    private int trueFormTime;
    /** {@code SkinWalkerChaseSoundInstance} di klien. */
    public Object chaseSoundInstance;

    @SuppressWarnings("unchecked")
    public SkinWalkerEntity(EntityType<? extends Monster> entityType, Level world) {
        super(entityType, world);
        this.navigation = new SlightlyBetterMobNavigation(this, world);
        this.lookControl = new SkinWalkerEntity.SkinWalkerLookControl(this);
        this.getSkinWalkerComponent();
        if (world.isClientSide) {
            this.component.setTargetPlayerUUID(this.getTargetPlayer(world));
        }
        this.component.setSneaking(false);
        this.setUpLimbs();
    }

    /**
     * {@code InitializeComponents.SKIN_WALKER.get(this)}. CCA sudah membuat komponen
     * sebelum konstruktor ini jalan, sedangkan {@code Mob} memanggil
     * {@link #registerGoals()} dari konstruktornya sendiri -- jadi dibuat saat pertama diminta.
     */
    public SkinWalkerComponent getSkinWalkerComponent() {
        if (this.component == null) {
            this.component = new SkinWalkerComponent(this);
        }
        return this.component;
    }

    @SuppressWarnings("unchecked")
    protected void setUpLimbs() {
        this.addComponent(this.component.getIKComponent());
    }

    private UUID getTargetPlayer(Level world) {
        if (world instanceof ServerLevel serverLevel) {
            WorldEvents events = WorldEvents.get(serverLevel, this.getX());
            if (events.getActiveSkinwalkerTarget() != null) {
                return events.getActiveSkinwalkerTarget().getUUID();
            }
        } else {
            UUID active = com.backrooms.client.skinwalker.ClientWorldEvents.activeSkinwalkerTarget();
            if (active != null && world.getPlayerByUUID(active) != null) {
                return active;
            }
        }
        List<? extends Player> players = this.worldPlayers(world);
        int rand;
        if (players.size() <= 1) {
            rand = 0;
        } else {
            rand = RandomSource.create().nextIntBetweenInclusive(0, players.size() - 1);
        }
        return players.isEmpty() ? null : players.get(rand).getUUID();
    }

    /** {@code world.getPlayers()}: di server, pemain di salinan level ini. */
    private List<? extends Player> worldPlayers(Level world) {
        if (world instanceof ServerLevel serverLevel) {
            return WorldEvents.get(serverLevel, this.getX()).players();
        }
        return world.players();
    }

    public static AttributeSupplier.Builder createSkinWalkerAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 10000.0D)
                .add(Attributes.FOLLOW_RANGE, 1000.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32F)
                .add(Attributes.ATTACK_DAMAGE, 12.0D);
    }

    @Override
    protected void registerGoals() {
        this.targetSelector.addGoal(2, new SkinWalkerActiveTarget(this));
        this.targetSelector.addGoal(1, new FinalFormActiveTargetGoal(this));
        this.goalSelector.addGoal(5, new FinalFormWanderGoal(this, 1.0D));
        this.goalSelector.addGoal(4, new SpeakGoal(this));
        this.goalSelector.addGoal(3, new FollowClosestPlayerGoal(this, 5.0F, 15.0F, 1.0F));
        this.goalSelector.addGoal(3, new ActNaturalGoal(this));
        this.goalSelector.addGoal(2, new FinalFormIdleGoal(this, 60, 60));
        this.goalSelector.addGoal(1, new FinalFormAttackGoal(this));
    }

    @Override
    public boolean killedEntity(ServerLevel world, LivingEntity other) {
        this.setTarget(null);
        this.getNavigation().stop();
        this.component.setShouldBeginRelease(true);
        return super.killedEntity(world, other);
    }

    @Override
    public void tick() {
        if (this.component.getTargetPlayerUUID() == null) {
            this.component.setTargetPlayerUUID(this.getTargetPlayer(this.level()));
        }
        if (this.maxSuspicion < 0 && !this.level().isClientSide) {
            this.maxSuspicion = 1800 + 900 * (this.worldPlayers(this.level()).size() - 1);
        }

        this.setInvulnerable(this.component.isInTrueForm());
        if (this.level().isClientSide && this.component.isInTrueForm()) {
            this.tickComponentsServer(this);
        }

        if (!this.level().isClientSide) {
            if (this.getTarget() != null) {
                if (!this.component.isChasing()) {
                    this.component.setChasing(true);
                }
            } else if (this.component.isChasing()) {
                this.component.setChasing(false);
            }

            if (!this.component.isInTrueForm() && !this.component.shouldBeginReveal()) {
                if (this.tickCount >= 2400 || this.component.getSuspicion() > this.maxSuspicion) {
                    this.component.setBeginReveal(true);
                }

                this.updateLookAtSuspicion();
                if (this.getTarget() != null && this.component.shouldLookAtTarget()) {
                    ((SkinWalkerEntity.SkinWalkerLookControl) this.getLookControl()).lookAt(this.getTarget(), 3);
                }
            }

            if (this.component.isInTrueForm() && !this.component.shouldBeginRelease()) {
                ++this.trueFormTime;
                if (this.trueFormTime >= 1200) {
                    this.component.setShouldBeginRelease(true);
                }
            }

            if (this.component.shouldBeginReveal()) {
                this.tickReveal();
            } else if (this.ticks > 100) {
                this.ticks = 0;
            }
        }

        super.tick();
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return super.getAmbientSound();
    }

    public void tickReveal() {
        if (this.prevTarget == null && this.getTarget() != null) {
            this.prevTarget = this.getTarget();
        }

        this.setTarget(null);
        ++this.ticks;
        this.getNavigation().stop();
        // BackroomsLevels.getLevel(world).ifPresent(...): hanya di dimensi level backrooms.
        BackroomsLevel backroomsLevel = BackroomsLevel.byDimension(this.level().dimension());
        if (backroomsLevel != null && this.level() instanceof ServerLevel serverLevel) {
            WorldEvents level = WorldEvents.get(serverLevel, this.getX());
            if (this.ticks == 9) {
                this.level().playSound(null, this, ModSounds.event("skinwalker_bone_crack"), SoundSource.HOSTILE, 10.0F, 1.0F);
            }

            if (this.ticks == 39) {
                this.level().playSound(null, this, ModSounds.event("skinwalker_bone_crack_long"), SoundSource.HOSTILE, 10.0F, 1.0F);
            }

            if (this.ticks == 99) {
                this.level().playSound(null, this, ModSounds.event("skinwalker_reveal"), SoundSource.HOSTILE, 100.0F, 1.0F);
            }

            if (this.ticks == 110 && backroomsLevel == BackroomsLevel.LEVEL0) {
                level.setLightState(LightState.FLICKER);
            }

            if (this.ticks == 195) {
                if (backroomsLevel == BackroomsLevel.LEVEL0) {
                    level.setLightState(LightState.OFF);
                }

                for (ServerPlayer player : level.players()) {
                    ModNetwork.turnOffFlashlight(player);
                }
            }

            if (this.ticks >= 220) {
                if (backroomsLevel == BackroomsLevel.LEVEL0) {
                    level.setLightState(LightState.ON);
                }

                this.component.setBeginReveal(false);
                this.component.setTrueForm(true);
                if (this.prevTarget != null) {
                    this.beginTargeting((Player) this.prevTarget);
                    this.prevTarget = null;
                }

                this.ticks = 0;
            }
        }
    }

    private void updateLookAtSuspicion() {
        HashSet<Player> otherPlayers = new HashSet<>(this.worldPlayers(this.level()));
        List<Player> players = this.level().getNearbyPlayers(TargetingConditions.DEFAULT, this,
                new AABB(this.position(), this.position().add(15.0D, 15.0D, 15.0D)).move(-7.5D, -7.5D, -7.5D));
        players.forEach(otherPlayers::remove);

        for (Player player : players) {
            PlayerComponent playerComponent = PlayerComponent.get(player);
            Entity targetEntity = playerComponent.getTargetEntity();
            if (targetEntity != null) {
                if (targetEntity.equals(this)) {
                    if (playerComponent.getSkinWalkerLookDelay() <= 0) {
                        this.component.addSuspicion();
                    } else {
                        playerComponent.subtractSkinWalkerLookDelay();
                    }
                } else {
                    playerComponent.setSkinWalkerLookDelay(60);
                }
            } else {
                playerComponent.setSkinWalkerLookDelay(60);
            }
        }

        for (Player player : otherPlayers) {
            PlayerComponent playerComponent = PlayerComponent.get(player);
            playerComponent.setSkinWalkerLookDelay(60);
        }
    }

    public void noticePlayer(Player player) {
        this.component.setNoticing(true);
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        this.playSound(ModSounds.event("skinwalker_notice"), 10.0F, 1.0F);
        this.getLookControl().setLookAt(player, 360.0F, 360.0F);
        executorService.schedule(() -> {
            // Aslinya dijalankan langsung di thread executor; di sini diserahkan ke thread server
            // supaya aman, waktunya tetap 4,3 detik.
            Runnable task = () -> {
                this.level().broadcastEntityEvent(this, (byte) 123);
                this.setTarget(player);
                this.component.setNoticing(false);
            };
            if (this.getServer() != null) {
                this.getServer().execute(task);
            } else {
                task.run();
            }
            executorService.shutdown();
        }, 4300L, TimeUnit.MILLISECONDS);
    }

    public void beginTargeting(Player player) {
        this.getLookControl().setLookAt(player, 360.0F, 360.0F);
        this.setTarget(player);
        this.level().broadcastEntityEvent(this, (byte) 123);
    }

    @Override
    public void handleEntityEvent(byte status) {
        if (status == 123 && this.level().isClientSide) {
            com.backrooms.client.skinwalker.SkinWalkerClient.handleSkinWalkerEntityClientSide(this);
        }
        super.handleEntityEvent(status);
    }

    @Override
    public void onClientRemoval() {
        if (this.level().isClientSide()) {
            com.backrooms.client.skinwalker.SkinWalkerClient.onRemoveSkinWalkerClientSide(this);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean bl = super.hurt(source, amount);
        if (this.level().isClientSide) {
            return false;
        }
        if (bl && source.getEntity() instanceof Player) {
            this.component.addSuspicion(100);
        }
        return bl;
    }

    @Override
    protected float tickHeadTurn(float bodyRotation, float headRotation) {
        if (this.attackAnim > 0.0F) {
            bodyRotation = this.getYHeadRot();
        }

        float f = Mth.wrapDegrees(bodyRotation - this.yBodyRot);
        this.yBodyRot += f * 0.3F;
        float g = Mth.wrapDegrees(this.getYHeadRot() - this.yBodyRot);
        if (Math.abs(g) > 50.0F) {
            this.yBodyRot += g - (float) (Mth.sign(g) * 50);
        }

        boolean bl = g < -90.0F || g >= 90.0F;
        if (bl) {
            headRotation *= -1.0F;
        }
        return headRotation;
    }

    @Override
    public int getMaxHeadYRot() {
        return 360;
    }

    @Override
    public int getMaxHeadXRot() {
        return 360;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        // Komponen CCA ikut tersimpan bersama entity.
        CompoundTag component = new CompoundTag();
        this.component.writeToNbt(component);
        tag.put("spb-revamped:skw", component);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("spb-revamped:skw")) {
            this.component.readFromNbt(tag.getCompound("spb-revamped:skw"));
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 10, state -> {
            if (this.component.shouldBeginReveal()) {
                return state.setAndContinue(TRANSITION);
            } else {
                state.resetCurrentAnimation();
                return null;
            }
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public List<IKModelComponent<SkinWalkerEntity>> getComponents() {
        return this.components;
    }

    @Override
    public double getSize() {
        return 1.0D;
    }

    public class SkinWalkerLookControl extends LookControl {
        private int maxLookAtTimer = 5;
        private final Easings.Easing easing = Easings.Easing.easeInOutCubic;

        public SkinWalkerLookControl(Mob entity) {
            super(entity);
        }

        public void lookAt(Entity entity, int lookTimer) {
            this.lookAt(new Vec3(entity.getX(), getLookingHeightFor(entity), entity.getZ()), lookTimer);
        }

        public void lookAt(Vec3 vec3d, int lookTimer) {
            this.wantedX = vec3d.x;
            this.wantedY = vec3d.y;
            this.wantedZ = vec3d.z;
            this.yMaxRotSpeed = (float) this.mob.getMaxHeadYRot();
            this.xMaxRotAngle = (float) this.mob.getMaxHeadXRot();
            this.lookAtCooldown = lookTimer;
            this.maxLookAtTimer = lookTimer;
        }

        @Override
        public void tick() {
            if (this.lookAtCooldown > 0) {
                --this.lookAtCooldown;
                this.getYRotD().ifPresent(yaw -> this.mob.yHeadRot = this.changeAngle2(this.mob.yHeadRot, yaw, this.yMaxRotSpeed));
                this.getXRotD().ifPresent(pitch -> this.mob.setXRot(this.changeAngle2(this.mob.getXRot(), pitch, this.xMaxRotAngle)));
            } else {
                this.mob.yHeadRot = this.rotateTowards(this.mob.yHeadRot, this.mob.yBodyRot, 10.0F);
            }
            this.clampHeadRotationToBody();
        }

        private float changeAngle2(float from, float to, float max) {
            float f = Mth.degreesDifference(from, to);
            float g = Mth.clamp(f, -max, max);
            return from + g * this.easing.ease(1.0F - (float) this.lookAtCooldown / (float) this.maxLookAtTimer);
        }

        private static double getLookingHeightFor(Entity entity) {
            return entity instanceof LivingEntity ? entity.getEyeY() : (entity.getBoundingBox().minY + entity.getBoundingBox().maxY) / 2.0D;
        }
    }
}
