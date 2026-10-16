package com.backrooms.client.lobby;

import com.backrooms.client.lobby.screen.Ui;
import com.backrooms.lobby.DropTiming;
import com.backrooms.lobby.RoomInfo;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;

/**
 * Lapisan layar cutscene jatuh: pita sinema (letterbox), garis kecepatan,
 * kilat "freeze frame" saat waktu melambat, kartu nama tiap anggota, kartu
 * room di bagian akhir, dan tirai hitam.
 *
 * <p>Kartu nama ala kartu perkenalan film/game (Suicide Squad, Borderlands,
 * Apex): pita miring berwarna tim dibanting dari kiri dengan overshoot dan
 * squash &amp; stretch, nama menyusul sedikit di belakangnya (overlapping
 * action), garis aksen menyapu terakhir (follow-through); keluar dengan
 * cepat ke kiri sebelum kamera menyambar ke anggota berikutnya.
 */
public final class DropOverlay {

    private static final int GOLD = 0xFFFFC93C;
    private static final long CARD_IN = 240L;
    private static final long CARD_OUT = 200L;

    private DropOverlay() {}

    public static void render(GuiGraphics g, int w, int h) {
        if (!DropCutscene.isRunning()) return;
        long t = DropCutscene.time();
        long total = DropCutscene.total();
        if (t < total && !DropCutscene.landed()) {
            bulletTimeGrade(g, w, h);
            speedLines(g, w, h, t);
            card(g, w, h, t);
            cardSparks(g);
            finaleCard(g, w, h, t);
            letterbox(g, w, h, t, total);
        }
        float black = DropCutscene.blackAlpha();
        if (black > 0.0F) g.fill(0, 0, w, h, ((int) (black * 255.0F) << 24));
        // Impact frame: warna layar dibalik sesaat (gaya anime), di atas segalanya.
        if (DropCutscene.impactFrame() && black < 1.0F) invert(g, w, h);
    }

    // ------------------------------------------------------- impact, grade

    private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/misc/white.png");
    private static final ResourceLocation VIGNETTE = new ResourceLocation("minecraft", "textures/misc/vignette.png");

    /** Balik warna seluruh layar: tekstur putih dengan blend ONE_MINUS_DST_COLOR (blit tidak memasang blend sendiri). */
    private static void invert(GuiGraphics g, int w, int h) {
        g.flush();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ZERO,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        g.blit(WHITE, 0, 0, 0, 0, w, h, 16, 16);
        g.flush();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /** Bullet time: vignette gelap tipis di tepi layar (tanpa rona warna). */
    private static void bulletTimeGrade(GuiGraphics g, int w, int h) {
        float slow = DropCutscene.slowness();
        if (slow <= 0.02F) return;
        g.flush();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        float k = Mth.clamp(slow * 0.45F, 0.0F, 1.0F);
        g.setColor(k, k, k, 1.0F);
        g.blit(VIGNETTE, 0, 0, 0, 0, w, h, w, h);
        g.flush();
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    // ------------------------------------------------------------ sinema

    private static void letterbox(GuiGraphics g, int w, int h, long t, long total) {
        float in = DropCutscene.ease(t / 600.0F);
        int bar = Math.round(h * 0.105F * in);
        if (bar <= 0) return;
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);
    }

    /** Garis kecepatan dari tepi layar: kuat saat jatuh cepat dan saat kamera menyambar, hilang saat waktu melambat. */
    private static void speedLines(GuiGraphics g, int w, int h, long t) {
        float strength = 0.0F;
        if (t > DropTiming.DROP + 300L) strength = (DropCutscene.speed() - 0.5F) * 2.0F;
        if (DropCutscene.currentIndex() >= 0 && DropCutscene.slotTime() < DropCutscene.whip()) {
            strength = Math.max(strength, 0.5F * (float) Math.sin(Math.PI * DropCutscene.slotTime() / DropCutscene.whip()));
        }
        strength = Mth.clamp(strength, 0.0F, 1.4F);
        if (strength <= 0.05F) return;
        RandomSource random = RandomSource.create(t / 45L);
        float cx = w / 2.0F, cy = h / 2.0F;
        float far = (float) Math.sqrt(cx * cx + cy * cy);
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0.0F);
        for (int i = 0; i < 26; i++) {
            float angle = random.nextFloat() * 360.0F;
            float inner = far * (0.55F + random.nextFloat() * 0.3F);
            float thick = 0.4F + random.nextFloat() * 0.9F;
            int a = (int) (Mth.clamp(strength * (25 + random.nextInt(45)), 0, 120));
            g.pose().pushPose();
            g.pose().mulPose(Axis.ZP.rotationDegrees(angle));
            fillF(g, inner, -thick, far + 4.0F, thick, (a << 24) | 0xFFFFFF);
            g.pose().popPose();
        }
        g.pose().popPose();
    }

    /** Kilat putih sesaat ketika waktu membeku (bullet time mulai). */
    private static void flash(GuiGraphics g, int w, int h, long t) {
        long at;
        if (DropCutscene.currentIndex() >= 0) {
            at = DropCutscene.slotTime() - DropCutscene.whip();
        } else {
            at = t - DropCutscene.finaleStart() - DropCutscene.whip();
        }
        if (at < 0L || at > 160L) return;
        int a = (int) (80.0F * (1.0F - at / 160.0F));
        g.fill(0, 0, w, h, (a << 24) | 0xFFFFFF);
    }

    // ---------------------------------------------------------- kartu

    private static void card(GuiGraphics g, int w, int h, long t) {
        int index = DropCutscene.currentIndex();
        if (index < 0 || index >= DropCutscene.members().size()) return;
        DropCutscene.Member m = DropCutscene.members().get(index);
        long u = DropCutscene.slotTime() - DropCutscene.whip() - 40L;
        long out = DropCutscene.slot() - DropCutscene.whip() - 40L - 260L;
        if (u < 0L) return;
        float in = u / (float) CARD_IN;
        float leave = Mth.clamp((u - out) / (float) CARD_OUT, 0.0F, 1.0F);
        if (leave >= 1.0F) return;

        Component name = Ui.text(m.name.toUpperCase(java.util.Locale.ROOT), Ui.Size.HUGE);
        boolean host = m.info != null && m.info.isHost();
        int team = m.info != null ? m.info.team() : -1;
        int accent = host ? GOLD : team >= 0 ? RoomInfo.teamColor(team) : 0xFFFF3B47;
        String role = host ? "HOST" : team >= 0 ? "TIM " + (team + 1) : "PEMAIN";
        Component sub = Ui.text(m.weapon.displayName().toUpperCase(java.util.Locale.ROOT) + "  •  " + role, Ui.Size.NORMAL);
        Component number = Ui.text(String.format("%02d", m.index + 1), Ui.Size.HUGE);

        float nameScale = Mth.clamp(h / 150.0F, 1.4F, 2.6F);
        int avatar = Math.round(22 * nameScale);
        float bannerW = Math.max(Ui.width(name) * nameScale + avatar + 70.0F, w * 0.36F);
        float bannerH = 30.0F * nameScale;
        float baseX = w * 0.05F;
        float baseY = h * 0.60F;

        // Banting masuk dengan overshoot, keluar dipercepat ke kiri.
        float slam = snap(in);
        float x = baseX - (1.0F - slam) * (bannerW + baseX + 40.0F) - easeIn(leave) * (bannerW + baseX + 60.0F);
        // Squash & stretch: memipih saat menghantam, lalu memantul.
        float impact = in < 0.55F ? 0.0F : (float) (Math.exp(-(in - 0.55F) * 5.0F) * Math.cos((in - 0.55F) * 14.0F));
        float sx = 1.0F + 0.10F * impact + 0.12F * (1.0F - Mth.clamp(in * 1.8F, 0.0F, 1.0F));
        float sy = 1.0F - 0.16F * impact - 0.10F * (1.0F - Mth.clamp(in * 1.8F, 0.0F, 1.0F));

        g.pose().pushPose();
        g.pose().translate(x, baseY, 0.0F);
        g.pose().scale(sx, sy, 1.0F);

        // Nomor urut besar dan pudar di belakang.
        g.pose().pushPose();
        g.pose().translate(bannerW - 52.0F * nameScale, -38.0F * nameScale, 0.0F);
        g.pose().scale(nameScale * 1.9F, nameScale * 1.9F, 1.0F);
        Ui.draw(g, number, 0.0F, 0.0F, 0x50FFFFFF, false);
        g.pose().popPose();

        // Pita miring: bayangan, warna tim, kilau tipis di atas.
        shear(g, -0.28F, () -> {
            fillF(g, 6.0F, 6.0F, bannerW + 6.0F, bannerH + 6.0F, 0x88000000);
            fillF(g, -30.0F, 0.0F, bannerW, bannerH, 0xF0141B33);
            fillF(g, bannerW - 10.0F * nameScale, 0.0F, bannerW, bannerH, accent);
            fillF(g, -30.0F, 0.0F, bannerW, 2.0F, 0x40FFFFFF);
        });
        // Garis aksen menyapu terakhir (follow-through).
        float wipe = DropCutscene.ease((u - 120L) / 260.0F);
        if (wipe > 0.0F) {
            shear(g, -0.28F, () -> fillF(g, -30.0F, bannerH + 3.0F, -30.0F + (bannerW + 30.0F) * wipe, bannerH + 3.0F + 2.0F * nameScale, accent));
        }

        // Avatar dan nama menyusul sedikit di belakang pita (overlap).
        float lag = (1.0F - snap((u - 50L) / (float) CARD_IN)) * -60.0F;
        g.pose().translate(lag, 0.0F, 0.0F);
        int ay = Math.round((bannerH - avatar) / 2.0F);
        Ui.avatar(g, m.id, 4, ay, avatar, host);
        g.pose().pushPose();
        g.pose().translate(avatar + 12.0F, bannerH * 0.5F - Ui.capHeight(Ui.Size.HUGE) * nameScale * 0.5F, 0.0F);
        g.pose().scale(nameScale, nameScale, 1.0F);
        // Pecah RGB ala pita VHS: makin rapat saat kartu mengendap, bergetar acak saat glitch.
        boolean tear = u < 140L || leave > 0.0F;
        float split = 3.0F * (1.0F - Mth.clamp(in, 0.0F, 1.0F)) + (tear ? 1.5F : 0.0F) + 4.0F * DropCutscene.glitch();
        if (split > 0.2F) {
            float jitter = tear ? (RANDOM.nextFloat() - 0.5F) * 3.0F : 0.0F;
            Ui.draw(g, name, -split + jitter, 0.0F, 0xB0FF2040, false);
            Ui.draw(g, name, split - jitter, 0.0F, 0xB020FFFF, false);
        }
        Ui.draw(g, name, 0.0F, 0.0F, Ui.WHITE, true);
        g.pose().popPose();
        // Baris kecil: senjata dan tim.
        float subIn = Mth.clamp((u - 140L) / 200.0F, 0.0F, 1.0F);
        if (subIn > 0.0F) {
            g.pose().pushPose();
            g.pose().translate(avatar + 14.0F, bannerH + 9.0F * nameScale * 0.7F + 6.0F, 0.0F);
            float ss = nameScale * 0.75F;
            g.pose().scale(ss, ss, 1.0F);
            int alpha = (int) (subIn * 255.0F) << 24;
            Ui.draw(g, sub, (1.0F - DropCutscene.ease(subIn)) * -20.0F, 0.0F, alpha | (accent & 0xFFFFFF), true);
            g.pose().popPose();
        }
        // Percikan dari ujung pita saat kartu menghantam.
        if (u >= 110L && sparkSlot != m.index) {
            sparkSlot = m.index;
            burst(x + bannerW * sx, baseY + bannerH * 0.5F, accent);
        }
        g.pose().popPose();
    }

    // ---------------------------------------------------------- percikan GUI

    private static final RandomSource RANDOM = RandomSource.create();
    private static final java.util.List<float[]> SPARKS = new java.util.ArrayList<>();
    private static int sparkSlot = -1;
    private static long lastSparkFrame;

    /** Semburan percikan: {x, y, vx, vy, umur, hidup, warna}. */
    private static void burst(float x, float y, int accent) {
        for (int i = 0; i < 18; i++) {
            double angle = Math.PI + (RANDOM.nextDouble() - 0.5D) * Math.PI * 1.4D;
            float v = 150.0F + RANDOM.nextFloat() * 260.0F;
            SPARKS.add(new float[]{x, y, (float) Math.cos(angle) * -v, (float) Math.sin(angle) * v - 80.0F, 0.0F,
                    0.25F + RANDOM.nextFloat() * 0.2F, Float.intBitsToFloat(i % 3 == 0 ? 0xFFFFFF : accent & 0xFFFFFF)});
        }
    }

    private static void cardSparks(GuiGraphics g) {
        long now = System.nanoTime();
        float dt = lastSparkFrame == 0L ? 0.0F : Math.min(0.05F, (now - lastSparkFrame) / 1.0E9F);
        lastSparkFrame = now;
        java.util.Iterator<float[]> it = SPARKS.iterator();
        while (it.hasNext()) {
            float[] p = it.next();
            p[4] += dt;
            if (p[4] > p[5]) {
                it.remove();
                continue;
            }
            p[0] += p[2] * dt;
            p[1] += p[3] * dt;
            p[3] += 600.0F * dt;
            float life = 1.0F - p[4] / p[5];
            float len = (float) Math.sqrt(p[2] * p[2] + p[3] * p[3]) * 0.02F;
            float angle = (float) Math.toDegrees(Math.atan2(p[3], p[2]));
            g.pose().pushPose();
            g.pose().translate(p[0], p[1], 0.0F);
            g.pose().mulPose(Axis.ZP.rotationDegrees(angle));
            fillF(g, -len, -0.6F, 0.0F, 0.6F, ((int) (life * 255.0F) << 24) | Float.floatToRawIntBits(p[6]));
            g.pose().popPose();
        }
    }

    /** Akhir: nama room dan mode di tengah atas, saat semua dalam satu bingkai. */
    private static void finaleCard(GuiGraphics g, int w, int h, long t) {
        long u = t - DropCutscene.finaleStart() - DropCutscene.whip() - 60L;
        long end = DropCutscene.DIVE - DropCutscene.whip() - 60L;
        if (u < 0L || u > end) return;
        float in = snap(u / 300.0F);
        float out = Mth.clamp((u - (end - 250L)) / 250.0F, 0.0F, 1.0F);
        RoomInfo room = LobbyClient.room();
        String title = "BACKROOM WAR";
        int count = DropCutscene.members().size();
        String mode = (room != null ? RoomInfo.teamMode(room.teamSize()).toUpperCase(java.util.Locale.ROOT) + "  •  " : "") + count + " ANGGOTA";
        Component big = Ui.text(title.toUpperCase(java.util.Locale.ROOT), Ui.Size.HUGE);
        Component small = Ui.text(mode, Ui.Size.NORMAL);
        float scale = Mth.clamp(h / 160.0F, 1.3F, 2.4F);
        int alpha = (int) ((1.0F - out) * 255.0F) << 24;
        g.pose().pushPose();
        g.pose().translate(w / 2.0F, h * 0.17F, 0.0F);
        float s = scale * (1.35F - 0.35F * in);
        g.pose().scale(s, s * (0.8F + 0.2F * in), 1.0F);
        Ui.drawCentered(g, big, 0.0F, 0.0F, alpha | 0xFFFFFF, true);
        g.pose().popPose();
        float subIn = Mth.clamp((u - 160L) / 220.0F, 0.0F, 1.0F) * (1.0F - out);
        if (subIn > 0.0F) {
            g.pose().pushPose();
            g.pose().translate(w / 2.0F, h * 0.17F + Ui.capHeight(Ui.Size.HUGE) * scale + 8.0F, 0.0F);
            g.pose().scale(scale * 0.75F, scale * 0.75F, 1.0F);
            float lineW = Ui.width(small) / 2.0F + 18.0F;
            fillF(g, -lineW * DropCutscene.ease(subIn), -4.0F, lineW * DropCutscene.ease(subIn), -3.0F, ((int) (subIn * 200) << 24) | 0xFFC93C);
            Ui.drawCentered(g, small, 0.0F, 2.0F, ((int) (subIn * 255) << 24) | 0xFFC93C, true);
            g.pose().popPose();
        }
    }

    // ------------------------------------------------------------ util

    private static float snap(float x) {
        x = Mth.clamp(x, 0.0F, 1.0F) - 1.0F;
        float s = 2.2F;
        return 1.0F + (s + 1.0F) * x * x * x + s * x * x;
    }

    private static float easeIn(float x) {
        x = Mth.clamp(x, 0.0F, 1.0F);
        return x * x * x;
    }

    /** Gambar miring (x bergeser sebanding y), untuk pita bergaya. */
    private static void shear(GuiGraphics g, float k, Runnable draw) {
        g.pose().pushPose();
        g.pose().mulPoseMatrix(new Matrix4f().m10(k));
        draw.run();
        g.pose().popPose();
    }

    /** {@code fill} dengan koordinat pecahan (lewat transformasi), supaya gerak halus. */
    private static void fillF(GuiGraphics g, float x0, float y0, float x1, float y1, int color) {
        g.pose().pushPose();
        g.pose().translate(x0, y0, 0.0F);
        g.pose().scale((x1 - x0) / 64.0F, (y1 - y0) / 64.0F, 1.0F);
        g.fill(0, 0, 64, 64, color);
        g.pose().popPose();
    }
}
