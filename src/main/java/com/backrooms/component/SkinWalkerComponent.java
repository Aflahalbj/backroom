package com.backrooms.component;

import com.backrooms.entity.custom.SkinWalkerEntity;
import com.backrooms.entity.ik.components.IKAnimatable;
import com.backrooms.entity.ik.components.IKLegComponent;
import com.backrooms.entity.ik.parts.Segment;
import com.backrooms.entity.ik.parts.ik_chains.IKChain;
import com.backrooms.entity.ik.parts.ik_chains.TargetReachingIKChain;
import com.backrooms.entity.ik.parts.sever_limbs.ServerLimb;
import com.backrooms.network.ModNetwork;
import com.backrooms.network.SkinWalkerComponentS2C;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;

/**
 * Port dari {@code com.sp.cca_stuff.SkinWalkerComponent} (komponen CCA
 * {@code spb-revamped:skw}). Seperti di CCA, {@link #sync()} mengirim isi
 * {@link #writeToNbt} ke semua pemain yang melihat entity ini.
 */
public class SkinWalkerComponent {
    private final SkinWalkerEntity entity;
    private Player followTarget;
    private boolean isChasing;
    private UUID targetPlayerUUID;
    private boolean shouldLookAtTarget;
    private boolean active;
    private boolean trueForm;
    private boolean idle;
    private boolean shouldActNatural;
    private boolean currentlyActingNatural;
    private boolean isSneaking;
    private boolean beginReveal;
    private boolean beginRelease;
    private boolean isNoticing;
    private int suspicion;
    private BlockPos lastKnownTargetLocation;
    private final IKLegComponent<? extends IKChain, ? extends IKAnimatable<?>> IKComponent = new IKLegComponent<>(
            new IKLegComponent.LegSetting.Builder().maxDistance(1.5D).stepInFront(1.0D).movementSpeed(0.7D).maxStandingStillDistance(0.1D).standStillCounter(20).build(),
            List.of(
                    new ServerLimb(1.5D, 0.0D, 2.0D, (limb, legComponent, i, movementSpeed) -> com.backrooms.client.skinwalker.SkinWalkerClient.skinWalkerPlayStepSound(limb)),
                    new ServerLimb(-1.5D, 0.0D, 2.0D, (limb, legComponent, i, movementSpeed) -> com.backrooms.client.skinwalker.SkinWalkerClient.skinWalkerPlayStepSound(limb)),
                    new ServerLimb(1.5D, 0.0D, -2.0D, (limb, legComponent, i, movementSpeed) -> com.backrooms.client.skinwalker.SkinWalkerClient.skinWalkerPlayStepSound(limb)),
                    new ServerLimb(-1.5D, 0.0D, -2.0D, (limb, legComponent, i, movementSpeed) -> com.backrooms.client.skinwalker.SkinWalkerClient.skinWalkerPlayStepSound(limb))),
            leg(), leg(), leg(), leg());

    private static TargetReachingIKChain leg() {
        return new TargetReachingIKChain(
                new Segment.Builder().length(0.65D).build(),
                new Segment.Builder().length(0.9D).build(),
                new Segment.Builder().length(1.1D).build(),
                new Segment.Builder().length(0.9D).build());
    }

    public SkinWalkerComponent(SkinWalkerEntity entity) {
        this.entity = entity;
        this.isSneaking = false;
        this.active = false;
        this.trueForm = false;
        this.shouldActNatural = false;
        this.currentlyActingNatural = false;
        this.shouldLookAtTarget = true;
        this.beginReveal = false;
        this.beginRelease = false;
        this.isNoticing = false;
        this.followTarget = null;
        this.isChasing = false;
        this.suspicion = 0;
    }

    public boolean isChasing() {
        return this.isChasing;
    }

    public void setChasing(boolean chasing) {
        this.isChasing = chasing;
        this.sync();
    }

    public BlockPos getLastKnownTargetLocation() {
        return this.lastKnownTargetLocation;
    }

    public void setLastKnownTargetLocation(BlockPos lastKnownTargetLocation) {
        this.lastKnownTargetLocation = lastKnownTargetLocation;
    }

    @SuppressWarnings("rawtypes")
    public IKLegComponent getIKComponent() {
        return this.IKComponent;
    }

    public int getSuspicion() {
        return this.suspicion;
    }

    public void addSuspicion(int suspicion) {
        this.suspicion += suspicion;
    }

    public void addSuspicion() {
        ++this.suspicion;
    }

    public boolean shouldLookAtTarget() {
        return this.shouldLookAtTarget;
    }

    public void setShouldLookAtTarget(boolean shouldLookAtTarget) {
        this.shouldLookAtTarget = shouldLookAtTarget;
    }

    public Player getFollowTarget() {
        return this.followTarget;
    }

    public void setFollowTarget(Player followTarget) {
        this.followTarget = followTarget;
    }

    public boolean shouldActNatural() {
        return this.shouldActNatural;
    }

    public void setShouldActNatural(boolean shouldActNatural) {
        this.shouldActNatural = shouldActNatural;
    }

    public boolean isCurrentlyActingNatural() {
        return this.currentlyActingNatural;
    }

    public void setCurrentlyActingNatural(boolean currentlyActingNatural) {
        this.currentlyActingNatural = currentlyActingNatural;
    }

    public boolean isInTrueForm() {
        return this.trueForm;
    }

    public void setTrueForm(boolean trueForm) {
        this.trueForm = trueForm;
        this.sync();
    }

    public boolean isActive() {
        return this.active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isIdle() {
        return this.idle;
    }

    public void setIdle(boolean idle) {
        this.idle = idle;
    }

    public boolean isSneaking() {
        return this.isSneaking;
    }

    public void setSneaking(boolean sneaking) {
        this.isSneaking = sneaking;
        this.sync();
    }

    public boolean isNoticing() {
        return this.isNoticing;
    }

    public void setNoticing(boolean noticing) {
        this.isNoticing = noticing;
    }

    public boolean shouldBeginReveal() {
        return this.beginReveal;
    }

    public void setBeginReveal(boolean beginReveal) {
        this.beginReveal = beginReveal;
        this.sync();
    }

    public boolean shouldBeginRelease() {
        return this.beginRelease;
    }

    public void setShouldBeginRelease(boolean beginRelease) {
        this.beginRelease = beginRelease;
        this.sync();
    }

    public UUID getTargetPlayerUUID() {
        return this.targetPlayerUUID;
    }

    public void setTargetPlayerUUID(UUID targetPlayerUUID) {
        this.targetPlayerUUID = targetPlayerUUID;
        this.sync();
    }

    /** {@code InitializeComponents.SKIN_WALKER.sync(entity)}; di klien tidak melakukan apa-apa, seperti CCA. */
    public void sync() {
        if (this.entity.level() != null && !this.entity.level().isClientSide) {
            ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> this.entity), SkinWalkerComponentS2C.of(this.entity));
        }
    }

    public void readFromNbt(CompoundTag tag) {
        this.isSneaking = tag.getBoolean("isSneaking");
        // Di aslinya UUID selalu ada (getUuid melempar kalau tidak); di sini dicek dulu.
        this.targetPlayerUUID = tag.hasUUID("targetPlayerUUID") ? tag.getUUID("targetPlayerUUID") : null;
        this.isChasing = tag.getBoolean("isChasing");
        this.trueForm = tag.getBoolean("trueForm");
        this.beginReveal = tag.getBoolean("beginReveal");
    }

    public void writeToNbt(CompoundTag tag) {
        tag.putBoolean("isSneaking", this.isSneaking);
        if (this.targetPlayerUUID != null) {
            tag.putUUID("targetPlayerUUID", this.targetPlayerUUID);
        }
        tag.putBoolean("isChasing", this.isChasing);
        tag.putBoolean("trueForm", this.trueForm);
        tag.putBoolean("beginReveal", this.beginReveal);
    }
}
