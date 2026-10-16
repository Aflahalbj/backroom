package com.backrooms.client.lobby;

import com.backrooms.BackroomsMod;
import com.backrooms.lobby.LobbyPose;
import com.mojang.logging.LogUtils;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.api.layered.modifier.SpeedModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Memutar animasi pose lobby (Player Animator) pada karakter yang tampil di
 * set lobby: pemain sendiri dan anggota room-nya. Pergantian pose dibaurkan
 * supaya karakter tidak "melompat" ke pose baru.
 *
 * <p>Selama cutscene perkenalan ({@link DropCutscene}) animasi jatuhnya
 * menggantikan pose lobby, dan semua lapisan ikut melambat saat bullet time.
 */
final class LobbyAnimator {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** Di atas lapisan animasi mod lain (mis. pose senjata TACZ). */
    private static final int PRIORITY = 3000;
    private static final int FADE_TICKS = 8;
    /** Pergantian aksi di cutscene harus cepat (kamera sudah menunggu). */
    private static final int DROP_FADE_TICKS = 3;

    /** Kejadian di dalam animasi: tembakan ({@code shot}) atau tahap reload. */
    private record Event(int tick, String kind) {}

    private static final class Entry {
        /**
         * Objek pemain pemilik lapisan ini. Setelah pindah dimensi Minecraft
         * membuat objek pemain baru dengan ID entitas yang sama (dan
         * {@code Entity.equals} membandingkan ID), jadi kunci map saja tidak
         * cukup untuk tahu lapisannya terpasang di objek yang benar.
         */
        final WeakReference<AbstractClientPlayer> owner;
        final ModifierLayer<IAnimation> layer = new ModifierLayer<>();
        final SpeedModifier speed = new SpeedModifier(1.0F);
        /** Pose yang dipilih (bisa {@link LobbyPose#AUTO}). */
        LobbyPose current;
        /** Animasi cutscene yang sedang diputar, atau {@code null}. */
        String drop;
        KeyframeAnimationPlayer player;
        List<Event> events = List.of();
        int lastTick = -1;
        /** Mode Auto: urutan pose yang sedang diputar dan sisa tick-nya. */
        int autoIndex;
        int autoTicksLeft;

        Entry(AbstractClientPlayer owner) {
            this.owner = new WeakReference<>(owner);
            this.layer.addModifierLast(speed);
        }
    }

    private static final Map<AbstractClientPlayer, Entry> ENTRIES = new WeakHashMap<>();
    /** Pratinjau AnimDev: animasi yang dipaksakan ke pemain sendiri, atau {@code null}. */
    private static String forced;

    private LobbyAnimator() {}

    static void tick(Minecraft client) {
        if (client.level == null) return;
        for (Player player : client.level.players()) {
            if (!(player instanceof AbstractClientPlayer clientPlayer)) continue;
            LobbyPose wanted = LobbyClient.inLobbyScene(player) ? LobbyClient.poseOf(player) : null;
            String drop = DropCutscene.animation(player);
            if (forced != null && player == client.player && drop == null) drop = forced;
            Entry entry = ENTRIES.get(clientPlayer);
            if (entry != null && entry.owner.get() != clientPlayer) {
                ENTRIES.remove(clientPlayer);
                entry = null;
            }
            if (entry == null) {
                if (wanted == null && drop == null) continue;
                entry = new Entry(clientPlayer);
                PlayerAnimationAccess.getPlayerAnimLayer(clientPlayer).addAnimLayer(PRIORITY, entry.layer);
                ENTRIES.put(clientPlayer, entry);
            }
            entry.speed.speed = DropCutscene.animSpeed(player);
            if (drop != null) {
                if (!drop.equals(entry.drop)) {
                    entry.drop = drop;
                    play(entry, drop, DropCutscene.animationTick(player), DROP_FADE_TICKS);
                } else {
                    fireEvents(player, entry);
                }
                continue;
            }
            if (entry.drop != null) {
                // Cutscene selesai: mulai lagi pose lobby (kalau masih di lobby).
                entry.drop = null;
                entry.current = null;
            }
            if (wanted != entry.current) {
                entry.current = wanted;
                entry.autoIndex = 0;
                play(entry, wanted == LobbyPose.AUTO ? LobbyPose.AUTO_SEQUENCE[0] : wanted);
                continue;
            }
            fireEvents(player, entry);
            // Auto: setelah satu putaran penuh, lanjut ke pose berikutnya.
            if (wanted == LobbyPose.AUTO && --entry.autoTicksLeft <= 0) {
                entry.autoIndex = (entry.autoIndex + 1) % LobbyPose.AUTO_SEQUENCE.length;
                play(entry, LobbyPose.AUTO_SEQUENCE[entry.autoIndex]);
            }
        }
        LobbyFx.tick();
    }

    private static void play(Entry entry, LobbyPose pose) {
        play(entry, pose == null ? null : pose.animation(), 0, FADE_TICKS);
    }

    /** Putar animasi {@code name} mulai tick {@code startTick} (untuk menyusul), dibaurkan selama {@code fade} tick. */
    private static void play(Entry entry, String name, int startTick, int fade) {
        KeyframeAnimation animation = name == null ? null
                : PlayerAnimationRegistry.getAnimation(new ResourceLocation(BackroomsMod.MOD_ID, name));
        if (name != null && animation == null) {
            LOGGER.warn("Animasi lobby {} tidak ditemukan.", name);
        }
        entry.player = animation == null ? null : new KeyframeAnimationPlayer(animation, startTick);
        entry.events = animation == null ? List.of() : events(animation);
        entry.lastTick = -1;
        entry.autoTicksLeft = animation == null ? 20 : animation.endTick;
        entry.layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(fade, Ease.INOUTSINE), entry.player);
    }

    /** Picu kejadian yang terlewati sejak tick lalu (termasuk saat animasi kembali ke awal loop). */
    private static void fireEvents(Player player, Entry entry) {
        if (entry.player == null || entry.events.isEmpty() || !entry.player.isActive()) return;
        int tick = entry.player.getTick();
        int last = entry.lastTick;
        entry.lastTick = tick;
        if (last < 0 || tick == last) return;
        for (Event event : entry.events) {
            boolean crossed = tick > last ? event.tick > last && event.tick <= tick
                    : event.tick > last || event.tick <= tick;
            if (!crossed) continue;
            if (event.kind.equals("shot")) {
                LobbyFx.fire(player);
            } else if (DropCutscene.isMember(player)) {
                // Gerakan cutscene: tembakan pistol kiri, hitstop, impact, efek, bunyi gerakan.
                DropCutscene.animEvent(player, event.kind);
            } else {
                LobbyFx.reloadSound(player, event.kind);
            }
        }
    }

    private static List<Event> events(KeyframeAnimation animation) {
        List<Event> events = new ArrayList<>();
        if (animation.extraData.get("shots") instanceof String shots) {
            for (String part : shots.split(",")) {
                if (!part.isBlank()) events.add(new Event(Integer.parseInt(part.trim()), "shot"));
            }
        }
        if (animation.extraData.get("sounds") instanceof String sounds) {
            for (String part : sounds.split(",")) {
                int colon = part.indexOf(':');
                if (colon > 0) events.add(new Event(Integer.parseInt(part.substring(colon + 1).trim()), part.substring(0, colon).trim()));
            }
        }
        return events;
    }

    /** Pratinjau: paksa animasi {@code name} ke pemain sendiri ({@code null} = kembali ke pose lobby). */
    static void force(String name) {
        forced = name;
        restartAll();
    }

    /** Animasi dimulai ulang dari awal (mis. setelah sumber daya dimuat ulang). */
    static void restartAll() {
        for (Entry entry : ENTRIES.values()) {
            entry.current = null;
            entry.drop = null;
        }
    }
}
