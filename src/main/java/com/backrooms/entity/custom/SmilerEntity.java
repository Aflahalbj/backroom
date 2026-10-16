package com.backrooms.entity.custom;

import com.backrooms.network.ModNetwork;
import com.backrooms.world.BackroomsLevel;
import com.backrooms.world.events.LightState;
import com.backrooms.world.events.WorldEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Port dari {@code com.sp.entity.custom.SmilerEntity} beserta {@code SmilerComponent}
 * (tekstur acak dan "harus menghilang", di aslinya komponen CCA yang disinkronkan;
 * di sini data entity yang disinkronkan).
 *
 * <p>Muncul saat blackout Level 1, diam di tempat. Kalau melihat pemain, layar
 * pemain itu glitch dan darahnya berkurang (lihat {@code SmilerClient}). Hilang
 * kalau disorot senter, setelah 5 detik, kalau tidak ada pemain dalam 15 blok,
 * atau kalau blackout Level 1 selesai.
 */
public class SmilerEntity extends Mob {
    private static final EntityDataAccessor<Integer> RANDOM_TEXTURE = SynchedEntityData.defineId(SmilerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SHOULD_DISAPPEAR = SynchedEntityData.defineId(SmilerEntity.class, EntityDataSerializers.BOOLEAN);

    private int finalTicks;
    private float liveTime;

    // ---- SmilerComponent (klien)
    private float opacity;
    private float maxOpacity;
    private int tick;
    /** {@code SmilerComponent.shouldDisappear} di klien: disalin dari server, di-reset setelah fade. */
    private boolean clientShouldDisappear;

    public SmilerEntity(EntityType<? extends Mob> entityType, Level world) {
        super(entityType, world);
        if (!world.isClientSide) {
            RandomSource random = RandomSource.create();
            this.setRandomTexture(random.nextIntBetweenInclusive(1, 3));
        }

        this.finalTicks = 20;
        this.liveTime = 100.0F;
        this.opacity = 0.0F;
        this.tick = 20;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(RANDOM_TEXTURE, 1);
        this.entityData.define(SHOULD_DISAPPEAR, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SHOULD_DISAPPEAR.equals(key) && this.level().isClientSide) {
            this.clientShouldDisappear = this.entityData.get(SHOULD_DISAPPEAR);
        }
    }

    @Override
    protected void registerGoals() {
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 0, true, false, null));
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide) {
            if (!this.shouldDisappear()) {
                if (this.level().getNearestPlayer(this, 15.0D) != null) {
                    for (Player player : this.level().getNearbyPlayers(TargetingConditions.forNonCombat().range(15.0D), this, this.getBoundingBox().inflate(15.0D, 1.0D, 15.0D))) {
                        if (this.shouldDisappear(player)) {
                            this.setShouldDisappear(true);
                            break;
                        }
                    }
                } else {
                    this.setShouldDisappear(true);
                }

                if (this.liveTime > 0.0F) {
                    --this.liveTime;
                } else {
                    this.setShouldDisappear(true);
                }
            }

            if (this.shouldDisappear()) {
                --this.finalTicks;
                if (this.finalTicks <= 0) {
                    this.discard();
                }
            }

            if (this.level() instanceof ServerLevel serverLevel && BackroomsLevel.at(serverLevel, this.getX()) == BackroomsLevel.LEVEL1) {
                if (WorldEvents.get(serverLevel, this.getX()).getLightState() != LightState.BLACKOUT) {
                    this.discard();
                }
            }
        } else {
            this.clientTick();
        }

        super.tick();
    }

    /** {@code SmilerComponent.clientTick}: muncul pelan, hilang pelan. */
    private void clientTick() {
        if (this.tickCount <= 30) {
            this.opacity = Math.min((float) this.tickCount / 30.0F, 1.0F);
            this.maxOpacity = this.opacity;
        }

        if (this.clientShouldDisappear) {
            --this.tick;
            this.opacity = (float) this.tick / 30.0F * this.maxOpacity;
            if (this.tick <= 0) {
                this.tick = 30;
                this.clientShouldDisappear = false;
            }
        }
    }

    private boolean shouldDisappear(Player player) {
        return player instanceof ServerPlayer serverPlayer && ModNetwork.isFlashlightOn(serverPlayer) && this.isPlayerStaring(player);
    }

    private boolean isPlayerStaring(Player player) {
        Vec3 vec3d = player.getViewVector(1.0F).normalize();
        Vec3 vec3d2 = new Vec3(this.getX() - player.getX(), this.getEyeY() - player.getEyeY(), this.getZ() - player.getZ());
        double d = vec3d2.length();
        vec3d2 = vec3d2.normalize();
        double e = vec3d.dot(vec3d2);
        return e > 1.0D - 0.35D / d && player.hasLineOfSight(this);
    }

    public int getRandomTexture() {
        return this.entityData.get(RANDOM_TEXTURE);
    }

    public void setRandomTexture(int randomTexture) {
        this.entityData.set(RANDOM_TEXTURE, randomTexture);
    }

    public boolean shouldDisappear() {
        return this.entityData.get(SHOULD_DISAPPEAR);
    }

    public void setShouldDisappear(boolean shouldDisappear) {
        this.entityData.set(SHOULD_DISAPPEAR, shouldDisappear);
    }

    public float getOpacity() {
        return this.opacity;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("randomTexture", this.getRandomTexture());
        tag.putBoolean("shouldDisappear", this.shouldDisappear());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setRandomTexture(tag.getInt("randomTexture"));
        this.setShouldDisappear(tag.getBoolean("shouldDisappear"));
    }

    public static AttributeSupplier.Builder createSmilerAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 1000.0D).add(Attributes.MOVEMENT_SPEED, 0.0D).add(Attributes.KNOCKBACK_RESISTANCE, 1000.0D);
    }
}
