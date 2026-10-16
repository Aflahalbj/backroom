package com.backrooms.client.lobby;

import com.backrooms.client.light.LightTicks;
import com.backrooms.lobby.LobbyWeapon;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import foundry.veil.api.client.render.deferred.light.PointLight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Efek tembakan karakter di lobby: api moncong (tekstur TACZ), kilatan
 * cahaya Veil yang menerangi sekitar, serta suara
 * tembakan/reload senjata itu sendiri (lewat pemutar suara TACZ).
 *
 * <p>Api digambar di ruang tangan kanan, sesaat sebelum senjata dirender
 * (lihat {@code ItemInHandLayerMixin}), jadi selalu tepat di moncong apa pun
 * pose dan gerakannya. Letak moncong per senjata ada di {@link #MUZZLE}.
 */
public final class LobbyFx {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation FLASH = new ResourceLocation("tacz", "textures/flash/common_muzzle_flash.png");
    private static final long FLASH_MILLIS = 60L;
    private static final RandomSource RANDOM = RandomSource.create();

    /** Letak moncong di ruang tangan kanan (setelah transformasi ItemInHandLayer), dalam blok. */
    private static final Map<LobbyWeapon, Vec3> MUZZLE = new EnumMap<>(LobbyWeapon.class);

    static {
        // Dikalibrasi dari pratinjau (AnimDev "markers"/"muzzle"); laras mengarah ke -Z.
        MUZZLE.put(LobbyWeapon.NONE, Vec3.ZERO);
        MUZZLE.put(LobbyWeapon.AK47, new Vec3(0.0D, 0.08D, -0.97D));
        MUZZLE.put(LobbyWeapon.M4A1, new Vec3(0.0D, 0.08D, -0.90D));
        MUZZLE.put(LobbyWeapon.SCAR_H, new Vec3(0.0D, 0.08D, -1.08D));
        MUZZLE.put(LobbyWeapon.HK416, new Vec3(0.0D, 0.08D, -0.90D));
        MUZZLE.put(LobbyWeapon.P90, new Vec3(0.0D, 0.09D, -0.33D));
        MUZZLE.put(LobbyWeapon.MP5, new Vec3(0.0D, 0.08D, -0.58D));
        MUZZLE.put(LobbyWeapon.M870, new Vec3(0.0D, 0.08D, -1.10D));
        MUZZLE.put(LobbyWeapon.AWP, new Vec3(0.0D, 0.08D, -1.50D));
    }

    /** Besar api per senjata: shotgun dan senapan runduk besar, SMG kecil. */
    private static final Map<LobbyWeapon, Float> FLASH_SCALE = new EnumMap<>(LobbyWeapon.class);

    static {
        for (LobbyWeapon weapon : LobbyWeapon.values()) FLASH_SCALE.put(weapon, 1.0F);
        FLASH_SCALE.put(LobbyWeapon.SCAR_H, 1.15F);
        FLASH_SCALE.put(LobbyWeapon.P90, 0.75F);
        FLASH_SCALE.put(LobbyWeapon.MP5, 0.75F);
        FLASH_SCALE.put(LobbyWeapon.M870, 1.4F);
        FLASH_SCALE.put(LobbyWeapon.AWP, 1.45F);
    }

    private static final class State {
        long flashUntil;
        float flashRoll;
        float flashScale = 1.0F;
        Vec3 muzzleWorld;
        /** Arah laras dan sisi kanan senjata di dunia (untuk peluru dan selongsong cutscene). */
        Vec3 barrel = new Vec3(0.0D, 0.0D, 1.0D);
        Vec3 side = new Vec3(1.0D, 0.0D, 0.0D);
        PointLight light;
        int lightTicks;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    /** Pistol tangan kiri (gerakan akimbo di cutscene perkenalan). */
    private static final Map<UUID, State> LEFT = new HashMap<>();
    /** Pistol akimbo: letak moncong di ruang tangan dan suaranya. */
    private static final Vec3 PISTOL_MUZZLE = new Vec3(0.0D, 0.1D, -0.32D);
    private static boolean markers;
    private static boolean holdFlash;
    private static Method playClientSound;
    private static boolean soundLookupDone;

    private LobbyFx() {}

    // ------------------------------------------------------------ pemicu

    /** Satu tembakan: api di frame berikutnya, kilatan cahaya, suara. */
    static void fire(Player player) {
        fire(player, false);
    }

    /** Satu tembakan senjata kanan atau pistol kiri ({@code left}). */
    static void fire(Player player, boolean left) {
        State s = (left ? LEFT : STATES).computeIfAbsent(player.getUUID(), id -> new State());
        s.flashUntil = System.currentTimeMillis() + FLASH_MILLIS;
        s.flashRoll = RANDOM.nextFloat() * 360.0F;
        s.flashScale = 0.85F + RANDOM.nextFloat() * 0.35F;
        // Kilatan cahaya sesaat yang menerangi sekitar.
        if (s.light == null) {
            s.light = new PointLight();
            s.light.setColor(1.0F, 0.62F, 0.26F).setRadius(7.0F).setBrightness(1.4F);
            Vec3 at = s.muzzleWorld != null ? s.muzzleWorld : player.getEyePosition();
            s.light.setPosition(new Vector3d(at.x, at.y, at.z));
            LightTicks.addLight(s.light);
        }
        s.lightTicks = 2;
        String sound = left ? DropCutscene.PISTOL_SOUND : LobbyClient.weaponOf(player).shootSound();
        if (sound != null) playTaczSound(player, sound, 0.45F);
        DropCutscene.onShot(player, left);
        if (s.muzzleWorld != null && DropCutscene.isMember(player)) {
            DropFx.shot(s.muzzleWorld, s.barrel, s.side, left ? null : LobbyClient.weaponOf(player));
        }
    }

    /** Suara TACZ apa saja (mis. kokang shotgun, bolt) dari karakter ini. */
    static void taczSound(Player player, String sound, float volume) {
        if (sound != null) playTaczSound(player, sound, volume);
    }

    /** Suara satu tahap reload ({@code magout}, {@code magin}, {@code end}). */
    static void reloadSound(Player player, String stage) {
        String sound = LobbyClient.weaponOf(player).reloadSound(stage);
        if (sound != null) playTaczSound(player, sound, 0.8F);
    }

    private static void playTaczSound(Entity entity, String sound, float volume) {
        if (!LobbyWeapons.available()) return;
        if (!soundLookupDone) {
            soundLookupDone = true;
            try {
                Class<?> manager = Class.forName("com.tacz.guns.client.sound.SoundPlayManager");
                playClientSound = manager.getMethod("playClientSound", Entity.class, ResourceLocation.class,
                        float.class, float.class, int.class);
            } catch (ReflectiveOperationException e) {
                LOGGER.warn("Pemutar suara TACZ tidak ditemukan; tembakan lobby tanpa suara.", e);
            }
        }
        if (playClientSound == null) return;
        if (DropCutscene.ownsCamera()) {
            // Badan asli anggota masih di lobby, jauh dari kamera cutscene di jurang (di luar
            // jangkauan dengar): suaranya diputar dari penanda di posisi kamera.
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
            if (client.level == null) return;
            Entity at = new net.minecraft.world.entity.Marker(net.minecraft.world.entity.EntityType.MARKER, client.level);
            net.minecraft.world.phys.Vec3 cam = client.gameRenderer.getMainCamera().getPosition();
            at.setPos(cam.x, cam.y, cam.z);
            entity = at;
        }
        try {
            // Gerak lambat: suara ikut melambat dan berat.
            float pitch = (0.95F + RANDOM.nextFloat() * 0.1F) * (0.55F + 0.45F * DropCutscene.animSpeed());
            playClientSound.invoke(null, entity, new ResourceLocation(sound), volume, pitch, 48);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.debug("Suara {} gagal diputar", sound, e);
        }
    }

    /** Tiap tick klien: padamkan kilatan cahaya yang sudah lewat. */
    static void tick() {
        tickLights(STATES);
        tickLights(LEFT);
    }

    private static void tickLights(Map<UUID, State> states) {
        for (State s : states.values()) {
            if (s.light != null && --s.lightTicks <= 0) {
                LightTicks.removeLight(s.light);
                s.light = null;
            }
        }
    }

    // ------------------------------------------------------------ render

    /** Pratinjau: api ditahan terus supaya bisa dilihat. */
    public static void toggleHoldFlash() {
        holdFlash = !holdFlash;
    }

    public static void toggleMarkers() {
        markers = !markers;
    }

    /** Untuk kalibrasi dari AnimDev: geser letak moncong senjata yang sedang dipakai. */
    public static void setMuzzle(LobbyWeapon weapon, double x, double y, double z) {
        MUZZLE.put(weapon, new Vec3(x, y, z));
    }

    public static Vec3 muzzle(LobbyWeapon weapon) {
        return MUZZLE.get(weapon);
    }

    /**
     * Dipanggil di ruang tangan kanan tepat sebelum senjata dirender: catat
     * posisi moncong di dunia (untuk kilatan cahaya) dan gambar api kalau
     * sedang menembak.
     */
    public static void renderAtHand(Player player, PoseStack poseStack, MultiBufferSource buffers, boolean left) {
        LobbyWeapon weapon = LobbyClient.weaponOf(player);
        if (left ? DropCutscene.offhandGun(player) == null : weapon.gunId() == null) return;
        Vec3 muzzle = left ? PISTOL_MUZZLE : MUZZLE.get(weapon);
        State s = (left ? LEFT : STATES).computeIfAbsent(player.getUUID(), id -> new State());

        poseStack.pushPose();
        poseStack.translate(muzzle.x, muzzle.y, muzzle.z);
        Matrix4f pose = poseStack.last().pose();
        s.muzzleWorld = toWorld(pose, 0, 0, 0);
        s.barrel = toWorld(pose, 0, 0, -1).subtract(s.muzzleWorld).normalize();
        s.side = toWorld(pose, 1, 0, 0).subtract(s.muzzleWorld).normalize();
        if (s.light != null) s.light.setPosition(new Vector3d(s.muzzleWorld.x, s.muzzleWorld.y, s.muzzleWorld.z));

        if (markers) drawMarkers(poseStack, buffers);
        long remaining = s.flashUntil - System.currentTimeMillis();
        if (remaining > 0 || holdFlash) {
            drawFlash(poseStack, buffers, s, left ? 0.7F : FLASH_SCALE.get(weapon), holdFlash ? 1.0F : (float) remaining / FLASH_MILLIS);
        }
        poseStack.popPose();
    }

    private static Vec3 toWorld(Matrix4f pose, float x, float y, float z) {
        Vector4f view = new Vector4f(x, y, z, 1.0F).mul(pose);
        Vector3f world = new Vector3f(view.x(), view.y(), view.z()).mul(new Matrix3f(RenderSystem.getInverseViewRotationMatrix()));
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        return camera.add(world.x(), world.y(), world.z());
    }

    /**
     * Api: tekstur TACZ digambar aditif (seperti cahaya) dengan warna oranye.
     * Bintang menghadap depan laras, ditambah dua lidah api bersilangan yang
     * berbentuk kerucut: sempit dan paling terang di moncong, melebar, lalu
     * meruncing ke depan. {@code life} 1 -> 0 selama api tampil.
     */
    private static void drawFlash(PoseStack poseStack, MultiBufferSource buffers, State s, float scale, float life) {
        VertexConsumer vc = buffers.getBuffer(RenderType.eyes(FLASH));
        float glow = 0.45F + 0.55F * life;
        int r = (int) (235 * glow);
        int g = (int) (150 * glow);
        int b = (int) (70 * glow);
        float k = scale * s.flashScale * (0.7F + 0.3F * life);
        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotationDegrees(s.flashRoll));
        Matrix4f m = poseStack.last().pose();
        Matrix3f n = poseStack.last().normal();
        float star = 0.13F * k;
        quad(vc, m, n, r, g, b,
                -star, -star, -0.02F, 0, 1, star, -star, -0.02F, 1, 1,
                star, star, -0.02F, 1, 0, -star, star, -0.02F, 0, 0);
        float base = 0.035F * k;
        float wide = 0.11F * k;
        float mid = -0.16F * k;
        float tip = -0.46F * k;
        for (int plane = 0; plane < 2; plane++) {
            // plane 0: bidang datar (x), plane 1: bidang tegak (y).
            float bx = plane == 0 ? base : 0, by = plane == 0 ? 0 : base;
            float wx = plane == 0 ? wide : 0, wy = plane == 0 ? 0 : wide;
            quad(vc, m, n, r, g, b,
                    -bx, -by, 0.03F, 0.4F, 0.5F, bx, by, 0.03F, 0.6F, 0.5F,
                    wx, wy, mid, 1, 0.3F, -wx, -wy, mid, 0, 0.3F);
            quad(vc, m, n, r, g, b,
                    -wx, -wy, mid, 0, 0.3F, wx, wy, mid, 1, 0.3F,
                    0, 0, tip, 0.5F, 0, 0, 0, tip, 0.5F, 0);
        }
        poseStack.popPose();
    }

    /** Segi empat dua sisi; tiap titik x, y, z, u, v. */
    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, int r, int g, int b,
                             float x1, float y1, float z1, float u1, float v1, float x2, float y2, float z2, float u2, float v2,
                             float x3, float y3, float z3, float u3, float v3, float x4, float y4, float z4, float u4, float v4) {
        vertex(vc, m, n, r, g, b, x1, y1, z1, u1, v1);
        vertex(vc, m, n, r, g, b, x2, y2, z2, u2, v2);
        vertex(vc, m, n, r, g, b, x3, y3, z3, u3, v3);
        vertex(vc, m, n, r, g, b, x4, y4, z4, u4, v4);
        vertex(vc, m, n, r, g, b, x4, y4, z4, u4, v4);
        vertex(vc, m, n, r, g, b, x3, y3, z3, u3, v3);
        vertex(vc, m, n, r, g, b, x2, y2, z2, u2, v2);
        vertex(vc, m, n, r, g, b, x1, y1, z1, u1, v1);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, int r, int g, int b, float x, float y, float z, float u, float v) {
        vc.vertex(m, x, y, z).color(r, g, b, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(n, 0.0F, 1.0F, 0.0F).endVertex();
    }

    /** Kalibrasi: sumbu X merah, Y hijau, Z biru (panjang 0,5 blok), kotak putih di titik moncong. */
    private static void drawMarkers(PoseStack poseStack, MultiBufferSource buffers) {
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = poseStack.last().pose();
        box(vc, m, 0, 0, 0, 0.03F, 255, 255, 255);
        for (int i = 1; i <= 5; i++) {
            float d = i * 0.1F;
            box(vc, m, d, 0, 0, 0.012F, 255, 40, 40);
            box(vc, m, 0, d, 0, 0.012F, 40, 255, 40);
            box(vc, m, 0, 0, d, 0.012F, 40, 80, 255);
        }
    }

    private static void box(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz, float r, int red, int green, int blue) {
        float[][] faces = {
                {-1, -1, -1, 1, -1, -1, 1, 1, -1, -1, 1, -1}, {-1, -1, 1, -1, 1, 1, 1, 1, 1, 1, -1, 1},
                {-1, -1, -1, -1, 1, -1, -1, 1, 1, -1, -1, 1}, {1, -1, -1, 1, -1, 1, 1, 1, 1, 1, 1, -1},
                {-1, 1, -1, 1, 1, -1, 1, 1, 1, -1, 1, 1}, {-1, -1, -1, -1, -1, 1, 1, -1, 1, 1, -1, -1}};
        for (float[] f : faces) {
            for (int i = 0; i < 4; i++) {
                vc.vertex(m, cx + f[i * 3] * r, cy + f[i * 3 + 1] * r, cz + f[i * 3 + 2] * r)
                        .color(red, green, blue, 255).endVertex();
            }
        }
    }

    static void forget(UUID player) {
        State s = STATES.remove(player);
        if (s != null && s.light != null) LightTicks.removeLight(s.light);
        State l = LEFT.remove(player);
        if (l != null && l.light != null) LightTicks.removeLight(l.light);
    }
}
