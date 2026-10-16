package com.backrooms.component;

import com.backrooms.compat.voicechat.BackroomsVoicechatPlugin;
import com.backrooms.network.ModNetwork;
import com.backrooms.network.PlayerComponentS2C;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Bagian Skinwalker dari {@code com.sp.cca_stuff.PlayerComponent} (komponen
 * CCA {@code spb-revamped:player}), di Forge sebagai capability pemain.
 *
 * <p>Yang ikut disinkronkan ke klien sama dengan field NBT aslinya yang
 * relevan: {@code isBeingCaptured}, {@code hasBeenCaptured},
 * {@code isBeingReleased}, {@code shouldBeMuted}. Status senter tetap lewat
 * sistem senter port ini ({@link ModNetwork#setFlashlight}).
 */
public class PlayerComponent {
    public static final Capability<PlayerComponent> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {});
    public static final ResourceLocation ID = new ResourceLocation("spb-revamped", "player");

    public Player player;
    private boolean isBeingCaptured;
    public boolean hasBeenCaptured;
    private boolean isBeingReleased;
    private Entity targetEntity;
    private int skinWalkerLookDelay;
    private boolean shouldBeMuted;
    private boolean isSpeaking;
    private int speakingBuffer;
    private float prevSpeakingTime;
    private boolean visibleToEntity;
    private int visibilityTimer;
    private int visibilityTimerCooldown;
    private boolean talkingTooLoud;
    private int talkingTooLoudTimer;
    private GameType prevGameMode;
    private boolean canSeeActiveSkinWalker;
    // ---- glitch Smiler
    public float glitchTimer;
    private boolean shouldGlitch;
    public int glitchTick;
    public boolean shouldInflictGlitchDamage;
    /** {@code PlayerComponent.GlitchAmbience} (klien). */
    public Object glitchAmbience;

    public PlayerComponent(Player player) {
        this.player = player;
        this.isBeingCaptured = false;
        this.hasBeenCaptured = false;
        this.isBeingReleased = false;
        this.skinWalkerLookDelay = 60;
        this.shouldBeMuted = false;
        this.isSpeaking = false;
        this.speakingBuffer = 80;
        this.prevSpeakingTime = 0.0F;
        this.visibleToEntity = false;
        this.visibilityTimer = 15;
        this.visibilityTimerCooldown = 0;
        this.talkingTooLoud = false;
        this.talkingTooLoudTimer = 20;
        this.canSeeActiveSkinWalker = false;
    }

    /**
     * {@code InitializeComponents.PLAYER.get(player)}. Komponen CCA selalu ada; capability
     * Forge sudah dibuang setelah pemain dihapus (mis. fake player yang mati masih di-tick),
     * jadi untuk kasus itu dipakai komponen cadangan supaya tidak pernah gagal.
     */
    public static PlayerComponent get(Player player) {
        PlayerComponent component = player.getCapability(CAPABILITY).resolve()
                .orElseGet(() -> DETACHED.computeIfAbsent(player, PlayerComponent::new));
        component.player = player;
        return component;
    }

    private static final java.util.Map<Player, PlayerComponent> DETACHED =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    public boolean isBeingCaptured() {
        return this.isBeingCaptured;
    }

    public void setBeingCaptured(boolean beingCaptured) {
        this.isBeingCaptured = beingCaptured;
    }

    public boolean hasBeenCaptured() {
        return this.hasBeenCaptured;
    }

    public void setHasBeenCaptured(boolean hasBeenCaptured) {
        this.hasBeenCaptured = hasBeenCaptured;
    }

    public boolean isBeingReleased() {
        return this.isBeingReleased;
    }

    public void setBeingReleased(boolean beingReleased) {
        this.isBeingReleased = beingReleased;
    }

    public Entity getTargetEntity() {
        return this.targetEntity;
    }

    public void setTargetEntity(Entity targetEntity) {
        this.targetEntity = targetEntity;
    }

    public int getSkinWalkerLookDelay() {
        return this.skinWalkerLookDelay;
    }

    public void setSkinWalkerLookDelay(int skinWalkerLookDelay) {
        this.skinWalkerLookDelay = skinWalkerLookDelay;
    }

    public void subtractSkinWalkerLookDelay() {
        --this.skinWalkerLookDelay;
    }

    public boolean shouldBeMuted() {
        return this.shouldBeMuted;
    }

    public void setShouldBeMuted(boolean shouldStayUnmuted) {
        this.shouldBeMuted = shouldStayUnmuted;
    }

    public boolean isSpeaking() {
        return this.isSpeaking;
    }

    public void setSpeaking(boolean speaking) {
        this.isSpeaking = speaking;
    }

    public boolean isVisibleToEntity() {
        return this.visibleToEntity;
    }

    public void setVisibleToEntity(boolean visibleToEntity) {
        this.visibleToEntity = visibleToEntity;
    }

    public boolean isTalkingTooLoud() {
        return this.talkingTooLoud;
    }

    public void setTalkingTooLoud(boolean talkingTooLoud) {
        this.talkingTooLoud = talkingTooLoud;
    }

    public void resetTalkingTooLoudTimer() {
        this.talkingTooLoudTimer = 20;
    }

    public GameType getPrevGameMode() {
        return this.prevGameMode;
    }

    public void setPrevGameMode(GameType prevGameMode) {
        this.prevGameMode = prevGameMode;
    }

    public boolean canSeeActiveSkinWalkerTarget() {
        return this.canSeeActiveSkinWalker;
    }

    public void setCanSeeActiveSkinWalkerTarget(boolean canSeeActiveSkinWalker) {
        this.canSeeActiveSkinWalker = canSeeActiveSkinWalker;
    }

    public float getGlitchTimer() {
        return this.glitchTimer;
    }

    public boolean shouldGlitch() {
        return this.shouldGlitch;
    }

    public void setShouldGlitch(boolean shouldGlitch) {
        this.shouldGlitch = shouldGlitch;
    }

    public void setShouldInflictGlitchDamage(boolean shouldInflictGlitchDamage) {
        this.shouldInflictGlitchDamage = shouldInflictGlitchDamage;
    }

    public void readFromNbt(CompoundTag tag) {
        this.isBeingCaptured = tag.getBoolean("isBeingCaptured");
        this.hasBeenCaptured = tag.getBoolean("hasBeenCaptured");
        this.isBeingReleased = tag.getBoolean("isBeingReleased");
        this.shouldBeMuted = tag.getBoolean("shouldBeMuted");
    }

    public void writeToNbt(CompoundTag tag) {
        tag.putBoolean("isBeingCaptured", this.isBeingCaptured);
        tag.putBoolean("hasBeenCaptured", this.hasBeenCaptured);
        tag.putBoolean("isBeingReleased", this.isBeingReleased);
        tag.putBoolean("shouldBeMuted", this.shouldBeMuted);
    }

    /** {@code InitializeComponents.PLAYER.sync(player)}: ke pemain itu sendiri dan semua yang melihatnya. */
    public void sync() {
        if (this.player instanceof ServerPlayer serverPlayer) {
            ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> serverPlayer), PlayerComponentS2C.of(this));
            // Untuk daftar pemain (tab) di semua klien, juga yang tidak melihat pemain ini.
            ModNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), this.capturedPacket());
        }
    }

    public com.backrooms.network.CapturedPlayerS2C capturedPacket() {
        return new com.backrooms.network.CapturedPlayerS2C(this.player.getUUID(), this.hasBeenCaptured || this.isBeingCaptured);
    }

    /** Bagian Skinwalker dan Smiler dari {@code PlayerComponent.serverTick}. */
    public void serverTick() {
        if (this.shouldInflictGlitchDamage) {
            this.player.hurt(com.backrooms.entity.ModDamageTypes.of(this.player.level(), com.backrooms.entity.ModDamageTypes.SMILER), 1.0F);
        }

        Float speakingTime = BackroomsVoicechatPlugin.speakingTime.get(this.player.getUUID());
        if (speakingTime != null && speakingTime == this.prevSpeakingTime) {
            if (this.isSpeaking()) {
                --this.speakingBuffer;
                if (this.speakingBuffer <= 0) {
                    this.setSpeaking(false);
                    this.speakingBuffer = 80;
                }
            }
        } else {
            this.speakingBuffer = 80;
        }

        this.updateEntityVisibility();
        if (BackroomsVoicechatPlugin.speakingTime.containsKey(this.player.getUUID())) {
            this.prevSpeakingTime = BackroomsVoicechatPlugin.speakingTime.get(this.player.getUUID());
        }
    }

    private void updateEntityVisibility() {
        if (this.isTalkingTooLoud() && this.talkingTooLoudTimer >= 0) {
            this.setVisibleToEntity(true);
            --this.talkingTooLoudTimer;
        } else if (this.talkingTooLoudTimer <= 0) {
            this.setVisibleToEntity(false);
            this.setTalkingTooLoud(false);
            this.resetTalkingTooLoudTimer();
        }

        if (!this.isVisibleToEntity()) {
            float speed = this.player.walkDist - this.player.walkDistO;
            if (!this.player.isShiftKeyDown() && speed != 0.0F) {
                --this.visibilityTimer;
            }
            if (this.player.isSprinting()) {
                --this.visibilityTimer;
            }
            if (speed == 0.0F) {
                this.visibilityTimer = 15;
            }
            if (this.visibilityTimer <= 0) {
                this.visibilityTimerCooldown = 20;
                this.setVisibleToEntity(true);
            }
        } else if (this.visibilityTimerCooldown > 0) {
            --this.visibilityTimerCooldown;
        } else {
            this.visibilityTimer = 15;
            if (!this.isTalkingTooLoud()) {
                this.setVisibleToEntity(false);
            }
        }
    }

    /** Penyedia capability; datanya ikut tersimpan di NBT pemain seperti komponen CCA. */
    public static final class Provider implements ICapabilitySerializable<CompoundTag> {
        private final PlayerComponent component;
        private final LazyOptional<PlayerComponent> optional;

        public Provider(Player player) {
            this.component = new PlayerComponent(player);
            this.optional = LazyOptional.of(() -> this.component);
        }

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return CAPABILITY.orEmpty(cap, this.optional);
        }

        @Override
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            this.component.writeToNbt(tag);
            return tag;
        }

        @Override
        public void deserializeNBT(CompoundTag tag) {
            this.component.readFromNbt(tag);
        }
    }
}
