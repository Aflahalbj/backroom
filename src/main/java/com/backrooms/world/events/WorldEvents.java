package com.backrooms.world.events;

import com.backrooms.compat.voicechat.BackroomsVoicechatPlugin;
import com.backrooms.component.PlayerComponent;
import com.backrooms.component.SkinWalkerComponent;
import com.backrooms.entity.ModEntities;
import com.backrooms.entity.custom.SkinWalkerEntity;
import com.backrooms.network.ModNetwork;
import com.backrooms.network.PersonalSoundS2C;
import com.backrooms.network.WorldEventsS2C;
import com.backrooms.world.BackroomsLevel;
import com.backrooms.world.events.generic.lights.LightLevelBlackout;
import com.backrooms.world.events.generic.lights.LightLevelFlicker;
import com.backrooms.world.events.level0.Level0IntercomBasic;
import com.backrooms.world.events.level0.Level0Music;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

/**
 * Port dari {@code com.sp.cca_stuff.WorldEvents} (komponen dunia CCA
 * {@code spb-revamped:events}) beserta status level-nya
 * ({@code Level0BackroomsLevel}: jumlah blackout/intercom dan kondisi lampu).
 *
 * <p>Di aslinya satu dimensi = satu level. Di port ini beberapa salinan
 * level berbagi satu dimensi (bangunan utama di slot 0, room di slot 1, 2, ...;
 * lihat {@code BuildArea}), jadi status ini dipegang per salinan: "pemain di
 * dunia" berarti pemain di salinan yang sama, dan lampu/Skinwalker satu room
 * tidak mengganggu room lain.
 *
 * <p>Event per level sama dengan aslinya: Level 0 (blackout, flicker, intercom,
 * musik), Level 1 (blackout + Smiler, flicker, ambience), Poolrooms (sunset,
 * ambience). Status level ikut di sini: lampu Level 0/1, waktu Poolrooms.
 *
 * <p>Tambahan port ini (tidak ada di mod asli): Skinwalker juga bisa muncul sendiri
 * di Level 1 dan Poolrooms, setelah {@link #SKINWALKER_EVENTS} event di level itu
 * (di Level 0 tetap syarat aslinya, 2 intercom), sekali per level per room.
 */
public class WorldEvents {
    private static final UUID nullUUID = UUID.randomUUID();
    /** Event yang harus sudah terjadi di Level 1/Poolrooms sebelum Skinwalker bisa muncul (kira-kira rata-rata Level 0). */
    public static final int SKINWALKER_EVENTS = 4;

    private final ServerLevel world;
    private final int slot;
    private final BackroomsLevel which;
    private AbstractEvent activeEvent;
    public int ticks;
    private int delay;
    private UUID activeSkinwalkerTarget;
    public SkinWalkerEntity activeSkinWalkerEntity;
    public boolean done;
    private int tick;

    // ---- status BackroomsLevel (singleton per level di aslinya)
    public final Random random = new Random();
    private final HashMap<String, Supplier<AbstractEvent>> events = new HashMap<>();
    private boolean shouldSync = false;
    private int blackoutCount = 0;
    private int intercomCount = 0;
    private LightState lightState = LightState.ON;
    // PoolroomsBackroomsLevel
    private float timeOfDay = 0.0F;
    private boolean sunsetTransitioning = false;
    /** Tambahan: Poolrooms jadi malam selama momen lampu mati Skinwalker. */
    private boolean skinwalkerNight = false;
    /** Tambahan: jumlah event yang sudah terjadi di salinan level ini (pemicu Skinwalker Level 1/Poolrooms). */
    private int eventCount = 0;

    WorldEvents(ServerLevel world, int slot) {
        this.world = world;
        this.slot = slot;
        this.which = BackroomsLevel.byDimension(world.dimension());
        this.ticks = 0;
        this.delay = 1800;
        this.activeSkinwalkerTarget = nullUUID;
        this.done = false;
        if (this.which == BackroomsLevel.LEVEL0) {
            this.events.put("blackout", LightLevelBlackout::new);
            this.events.put("flicker", LightLevelFlicker::new);
            this.events.put("intercom", Level0IntercomBasic::new);
            this.events.put("music", Level0Music::new);
        } else if (this.which == BackroomsLevel.LEVEL1) {
            this.events.put("blackout", com.backrooms.world.events.level1.Level1Blackout::new);
            this.events.put("flicker", LightLevelFlicker::new);
            this.events.put("ambience", com.backrooms.world.events.level1.Level1Ambience::new);
        } else if (this.which == BackroomsLevel.POOLROOMS) {
            this.events.put("sunset", com.backrooms.world.events.poolrooms.PoolroomsSunset::new);
            this.events.put("abience", com.backrooms.world.events.poolrooms.PoolroomsAmbience::new);
        }
    }

    /** Salinan level di tempat ini (lihat {@link WorldEventsManager}). */
    public static WorldEvents get(ServerLevel level, double x) {
        return WorldEventsManager.get(level, x);
    }

    public ServerLevel level() {
        return this.world;
    }

    public int slot() {
        return this.slot;
    }

    /** Level backrooms salinan ini; {@code null} di dimensi lain. */
    public BackroomsLevel which() {
        return this.which;
    }

    /** {@code world.getPlayers()}: pemain di salinan level ini saja. */
    public List<ServerPlayer> players() {
        List<ServerPlayer> list = new ArrayList<>();
        for (ServerPlayer player : this.world.players()) {
            if (WorldEventsManager.slotOf(this.world, player.getX()) == this.slot) {
                list.add(player);
            }
        }
        return list;
    }

    public void setActiveEvent(AbstractEvent activeEvent) {
        this.activeEvent = activeEvent;
    }

    public AbstractEvent getActiveEvent() {
        return this.activeEvent;
    }

    public Player getActiveSkinwalkerTarget() {
        return this.activeSkinwalkerTarget != null && !this.activeSkinwalkerTarget.equals(nullUUID)
                ? this.world.getPlayerByUUID(this.activeSkinwalkerTarget) : null;
    }

    public void setActiveSkinwalkerTarget(UUID uuid) {
        this.activeSkinwalkerTarget = uuid;
        this.sync();
    }

    public void sync() {
        CompoundTag tag = new CompoundTag();
        this.writeToNbt(tag);
        for (ServerPlayer player : this.players()) {
            ModNetwork.send(player, new WorldEventsS2C(tag));
        }
        WorldEventsManager.setDirty(this.world);
    }

    public void readFromNbt(CompoundTag tag) {
        this.readLevelNbt(tag);
        this.activeSkinwalkerTarget = tag.getUUID("activeSkinwalkerTarget");
        this.done = tag.getBoolean("skinwalkerDone");
    }

    public void writeToNbt(CompoundTag tag) {
        this.writeLevelNbt(tag);
        tag.putUUID("activeSkinwalkerTarget", this.activeSkinwalkerTarget);
        tag.putBoolean("skinwalkerDone", this.done);
    }

    public void serverTick() {
        if (!this.players().isEmpty() && this.which != null) {
            ++this.ticks;
            this.tickWorldEvents();
            this.tickSkinWalkerCapturing();
            this.shouldReleasePlayer();
        }
        this.shouldSync();
    }

    private void shouldReleasePlayer() {
        this.refreshActiveSkinWalker();
        if (this.activeSkinWalkerEntity != null) {
            SkinWalkerComponent component = this.activeSkinWalkerEntity.component;
            if (!component.shouldBeginRelease()) {
                if (this.getActiveSkinwalkerTarget() != null) {
                    ((ServerPlayer) this.getActiveSkinwalkerTarget()).setGameMode(GameType.SPECTATOR);
                    ((ServerPlayer) this.getActiveSkinwalkerTarget()).setCamera(this.activeSkinWalkerEntity);
                }
            } else {
                // Aslinya hanya di Level 0 (korban /skinwalker di level lain tidak pernah dilepas);
                // di sini korban dilepas di level mana pun (lampu/langit lewat skinwalkerLights).
                ServerPlayer target = (ServerPlayer) this.getActiveSkinwalkerTarget();
                // Di aslinya ini NullPointerException (server crash) kalau korban keluar
                // dari game saat dilepas; di sini urutan lepasnya tetap jalan tanpa korban.
                PlayerComponent targetComponent = target != null ? PlayerComponent.get(target) : new PlayerComponent(null);
                ++this.tick;
                if (this.tick == 1) {
                    this.skinwalkerLights(LightState.FLICKER);
                }

                if (this.tick == 80) {
                    this.skinwalkerLights(LightState.OFF);
                    targetComponent.setBeingReleased(true);
                    targetComponent.sync();
                    if (target != null) {
                        ModNetwork.send(target, new PersonalSoundS2C("skinwalker_release", 1.0F, 1.0F));
                        target.setGameMode(targetComponent.getPrevGameMode() != null ? targetComponent.getPrevGameMode() : GameType.SURVIVAL);
                        target.setCamera(target);
                    }

                    for (ServerPlayer player : this.players()) {
                        ModNetwork.turnOffFlashlight(player);
                    }

                    this.activeSkinWalkerEntity.discard();
                }

                if (this.tick >= 105) {
                    this.skinwalkerLights(LightState.ON);
                    targetComponent.setBeingReleased(false);
                    targetComponent.setHasBeenCaptured(false);
                    targetComponent.setShouldBeMuted(false);
                    targetComponent.sync();
                    this.activeSkinWalkerEntity = null;
                    // Aslinya penghitung ini tidak pernah kembali ke 0, jadi pelepasan berikutnya di sesi yang
                    // sama langsung loncat ke sini: korban tetap spectator dan Skinwalker tidak dihapus.
                    this.tick = 0;
                }
            }
        } else {
            if (this.getActiveSkinwalkerTarget() != null) {
                ServerPlayer target = (ServerPlayer) this.getActiveSkinwalkerTarget();
                PlayerComponent targetComponent = PlayerComponent.get(target);
                if (targetComponent.hasBeenCaptured() || targetComponent.isBeingCaptured()) {
                    target.setGameMode(GameType.SURVIVAL);
                    targetComponent.setHasBeenCaptured(false);
                    targetComponent.setShouldBeMuted(false);
                    targetComponent.sync();
                    ModNetwork.send(target, new PersonalSoundS2C("skinwalker_release", 1.0F, 1.0F));
                }
                this.activeSkinwalkerTarget = nullUUID;
            }
        }
    }

    /**
     * Aslinya Skinwalker aktif dipegang sebagai objek Java. Kalau chunk-nya sempat di-unload lalu dimuat
     * lagi, objek itu basi (entity yang hidup objek baru), jadi korban tidak pernah dilepas dan kameranya
     * menempel ke entity yang sudah tidak ada. Di sini dicari ulang lewat UUID; kalau Skinwalker-nya
     * sudah mati/dihapus, dianggap tidak ada supaya korban dilepas.
     */
    private void refreshActiveSkinWalker() {
        SkinWalkerEntity current = this.activeSkinWalkerEntity;
        // Saat dilepas, Skinwalker memang dihapus di tick 80 dan urutannya lanjut sampai 105.
        if (current == null || !current.isRemoved() || current.component.shouldBeginRelease()) return;
        net.minecraft.world.entity.Entity live = this.world.getEntity(current.getUUID());
        if (live instanceof SkinWalkerEntity skinWalker && !skinWalker.isRemoved()) {
            this.activeSkinWalkerEntity = skinWalker;
        } else if (current.getRemovalReason() != null && current.getRemovalReason().shouldDestroy()) {
            this.activeSkinWalkerEntity = null;
            this.tick = 0;
        }
    }

    private void tickSkinWalkerCapturing() {
        // Aslinya hanya Level 0 (2 intercom); Level 1/Poolrooms tambahan port ini.
        boolean ready = this.which == BackroomsLevel.LEVEL0 ? this.getIntercomCount() >= 2
                : (this.which == BackroomsLevel.LEVEL1 || this.which == BackroomsLevel.POOLROOMS) && this.eventCount >= SKINWALKER_EVENTS;
        if (ready) {
            if (this.players().size() > 1) {
                if (!this.done) {
                    if (this.activeSkinWalkerEntity == null) {
                        // Pemain yang paling sedikit bicara (lewat Simple Voice Chat) -- di antara
                        // pemain salinan level ini, karena tiap room punya Skinwalker sendiri.
                        Map.Entry<UUID, Float> min = null;
                        List<ServerPlayer> players = this.players();
                        for (Map.Entry<UUID, Float> entry : BackroomsVoicechatPlugin.speakingTime.entrySet()) {
                            if (players.stream().noneMatch(p -> p.getUUID().equals(entry.getKey()))) continue;
                            if (min == null || min.getValue() > entry.getValue()) {
                                min = entry;
                            }
                        }

                        if (min != null) {
                            Player target = this.world.getPlayerByUUID(min.getKey());
                            if (target != null && target.isAlive()) {
                                this.setActiveSkinwalkerTarget(target.getUUID());
                            }
                        }

                        if (this.getActiveSkinwalkerTarget() != null) {
                            Player target = this.getActiveSkinwalkerTarget();
                            PlayerComponent targetComponent = PlayerComponent.get(target);
                            if (!targetComponent.isSpeaking()) {
                                // Sama dengan aslinya: TargetPredicate.DEFAULT ikut diubah (objeknya dipakai bersama).
                                List<Player> playerEntityList = target.level().getNearbyPlayers(
                                        TargetingConditions.DEFAULT.ignoreInvisibilityTesting().ignoreLineOfSight().range(50.0D).selector(EntitySelector.NO_CREATIVE_OR_SPECTATOR::test),
                                        target, target.getBoundingBox().inflate(50.0D));
                                boolean seen = false;
                                for (Player player : playerEntityList) {
                                    if (player != target) {
                                        PlayerComponent playerComponent = PlayerComponent.get(player);
                                        if (playerComponent.canSeeActiveSkinWalkerTarget()) {
                                            seen = true;
                                            break;
                                        }
                                    }
                                }

                                if (!seen) {
                                    SkinWalkerEntity skinWalkerEntity = ModEntities.SKIN_WALKER.get().create(this.world);
                                    if (skinWalkerEntity != null) {
                                        skinWalkerEntity.moveTo(target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot());
                                        skinWalkerEntity.setDeltaMovement(target.getDeltaMovement());
                                        this.world.addFreshEntity(skinWalkerEntity);
                                        this.activeSkinWalkerEntity = skinWalkerEntity;
                                        targetComponent.setPrevGameMode(((ServerPlayer) target).gameMode.getGameModeForPlayer());
                                        targetComponent.setBeingCaptured(true);
                                        targetComponent.setHasBeenCaptured(true);
                                        targetComponent.setShouldBeMuted(true);
                                        targetComponent.sync();
                                        ((ServerPlayer) target).setGameMode(GameType.SPECTATOR);
                                        ((ServerPlayer) target).setCamera(skinWalkerEntity);
                                        this.done = true;
                                        this.sync();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void tickWorldEvents() {
        if (this.activeEvent == null) {
            --this.delay;
            if (this.delay <= 0) {
                this.delay = 0;
                this.activeEvent = this.getRandomEvent();
                this.activeEvent.init(this);
                if (!(this.activeEvent instanceof EmptyEvent)) {
                    ++this.eventCount;
                }
                this.ticks = 0;
                this.delay = this.nextEventDelay();
            }
        } else {
            if (this.activeEvent.duration() <= this.ticks) {
                this.activeEvent.finish(this);
                if (this.activeEvent.isDone()) {
                    this.activeEvent = null;
                }
            } else {
                this.activeEvent.ticks(this.ticks, this);
            }
        }
    }

    private void shouldSync() {
        boolean sync = this.shouldSync;
        this.shouldSync = false;
        if (sync) {
            this.sync();
        }
    }

    // ------------------------------------------------- BackroomsLevel

    /** {@code BackroomsLevel.getRandomEvent} plus batas blackout {@code Level0BackroomsLevel}. */
    private AbstractEvent getRandomEvent() {
        AbstractEvent activeEvent = this.pickEvent();
        if (this.which == BackroomsLevel.LEVEL0 && activeEvent instanceof LightLevelBlackout) {
            ++this.blackoutCount;
            if (this.blackoutCount > 2) {
                while (activeEvent instanceof LightLevelBlackout) {
                    activeEvent = this.pickEvent();
                }
            }
        }
        return activeEvent;
    }

    private AbstractEvent pickEvent() {
        if (this.events.isEmpty()) {
            return new EmptyEvent();
        }
        List<AbstractEvent> eventList = new ArrayList<>();
        this.events.forEach((key, value) -> eventList.add(value.get()));
        return eventList.get(this.random.nextInt(eventList.size()));
    }

    private int nextEventDelay() {
        if (this.which == BackroomsLevel.LEVEL0) return this.random.nextInt(1000, 1500);
        if (this.which == BackroomsLevel.LEVEL1) return this.random.nextInt(1000, 1600);
        return this.random.nextInt(800, 1000);
    }

    /** {@code BackroomsLevel.justChanged}. */
    public void justChanged() {
        this.shouldSync = true;
        WorldEventsManager.setDirty(this.world);
    }

    /**
     * {@code EventCommand.setEvent} asli ({@code /backroomsevent}): event aktif diselesaikan,
     * event baru langsung dimulai. Di port ini ikut dihitung untuk pemicu Skinwalker.
     */
    public void startEvent(AbstractEvent event) {
        if (this.activeEvent != null) {
            this.activeEvent.finish(this);
        }
        this.activeEvent = event;
        event.init(this);
        ++this.eventCount;
        this.ticks = 0;
    }

    /**
     * Efek lampu saat Skinwalker berubah wujud/melepas korban. Level 0 seperti aslinya;
     * tambahan port ini: Level 1 padam sementara (BLACKOUT, karena lampu neon tipis tidak
     * punya keadaan OFF), Poolrooms jadi malam selama lampu mati.
     */
    public void skinwalkerLights(LightState state) {
        if (this.which == BackroomsLevel.LEVEL0) {
            this.setLightState(state);
        } else if (this.which == BackroomsLevel.LEVEL1) {
            this.setLightState(state == LightState.OFF ? LightState.BLACKOUT : state);
        } else if (this.which == BackroomsLevel.POOLROOMS && state != LightState.FLICKER) {
            this.justChanged();
            this.skinwalkerNight = state == LightState.OFF;
        }
    }

    // ---- PoolroomsBackroomsLevel

    /** {@code PoolroomsBackroomsLevel.isNoon}: bukan senja (0.25) dan bukan fajar (0.75). */
    public boolean isNoon() {
        return (double) this.timeOfDay != 0.25D && (double) this.timeOfDay != 0.75D;
    }

    /** Air Poolrooms asam: senja/fajar (aslinya), atau malam karena Skinwalker (tambahan). */
    public boolean isWaterAcidic() {
        return this.which == BackroomsLevel.POOLROOMS && (!this.isNoon() || this.skinwalkerNight);
    }

    public float getTimeOfDay() {
        return this.timeOfDay;
    }

    public void setTimeOfDay(float timeOfDay) {
        this.justChanged();
        this.timeOfDay = timeOfDay;
    }

    public boolean isSunsetTransitioning() {
        return this.sunsetTransitioning;
    }

    public void setSunsetTransitioning(boolean sunsetTransitioning) {
        this.justChanged();
        this.sunsetTransitioning = sunsetTransitioning;
    }

    /** Level 0 dan Level 1 adalah {@code BackroomsLevelWithLights} di aslinya. */
    public boolean hasLights() {
        return this.which == BackroomsLevel.LEVEL0 || this.which == BackroomsLevel.LEVEL1;
    }

    public int getIntercomCount() {
        return this.intercomCount;
    }

    public void setIntercomCount(int intercomCount) {
        this.justChanged();
        this.intercomCount = intercomCount;
    }

    public void addIntercomCount() {
        this.justChanged();
        ++this.intercomCount;
    }

    public void setLightState(LightState lightState) {
        this.justChanged();
        this.lightState = lightState;
    }

    public LightState getLightState() {
        return this.lightState;
    }

    private void writeLevelNbt(CompoundTag nbt) {
        nbt.putInt("blackoutCount", this.blackoutCount);
        nbt.putInt("intercomCount", this.intercomCount);
        nbt.putString("lightState", this.lightState.name());
        nbt.putFloat("timeOfDay", this.timeOfDay);
        nbt.putBoolean("sunsetTransitioning", this.sunsetTransitioning);
        nbt.putBoolean("skinwalkerNight", this.skinwalkerNight);
        nbt.putInt("eventCount", this.eventCount);
    }

    private void readLevelNbt(CompoundTag nbt) {
        this.blackoutCount = nbt.getInt("blackoutCount");
        this.intercomCount = nbt.getInt("intercomCount");
        this.lightState = nbt.contains("lightState") ? LightState.valueOf(nbt.getString("lightState")) : LightState.ON;
        this.timeOfDay = nbt.getFloat("timeOfDay");
        this.sunsetTransitioning = nbt.getBoolean("sunsetTransitioning");
        this.skinwalkerNight = nbt.getBoolean("skinwalkerNight");
        this.eventCount = nbt.getInt("eventCount");
    }
}
