package com.backrooms.client.lobby.screen;

import com.backrooms.BackroomsMod;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bahan GUI lobby gaya game mobile: tombol 3D, panel kaca, kartu, bingkai
 * avatar, ikon, dan teks Lilita One berkontur. Teksturnya digambar 3 piksel
 * per piksel GUI dan ditampilkan dengan filter halus.
 */
public final class Ui {

    public static final int WHITE = 0xFFFFFFFF;
    public static final int OUTLINE = 0xFF1A1430;
    public static final int DIM = 0xFFAEB7D3;
    public static final int GOLD = 0xFFFFC93C;
    public static final int BROWN = 0xFF4A2C00;
    public static final int RED = 0xFFFF3B47;
    public static final int GREEN = 0xFF3FCF6A;
    public static final int NAVY = 0xE6141B33;

    private static final int DENSITY = 3;

    public enum Size {
        SMALL("game_small", 8), NORMAL("game_normal", 10), BIG("game_big", 14), HUGE("game_huge", 20);

        final ResourceLocation font;
        final int px;

        Size(String font, int px) {
            this.font = new ResourceLocation(BackroomsMod.MOD_ID, font);
            this.px = px;
        }
    }

    /** Tekstur 9-slice: ukuran asli dan lebar tepi (dalam texel). */
    public enum Tex {
        BTN_YELLOW("btn_yellow", 144, 72, 27),
        BTN_ORANGE("btn_orange", 144, 72, 27),
        BTN_BLUE("btn_blue", 144, 72, 27),
        BTN_GRAY("btn_gray", 144, 72, 27),
        PANEL("panel", 192, 192, 36),
        CARD("card", 96, 96, 24),
        CARD_GOLD("card_gold", 96, 96, 24),
        CARD_EMPTY("card_empty", 96, 96, 24),
        CARD_SELECTED("card_selected", 96, 96, 24),
        PILL("pill", 72, 36, 18),
        RING("ring", 96, 96, 0),
        RING_PLAIN("ring_plain", 96, 96, 0),
        WALLPAPER("wallpaper", 48, 48, 0),
        SHINE("shine", 72, 192, 0);

        final ResourceLocation id;
        final int width;
        final int height;
        final int border;

        Tex(String name, int width, int height, int border) {
            this.id = gui(name);
            this.width = width;
            this.height = height;
            this.border = border;
        }
    }

    public enum Icon {
        BACK, CHAT, CHECK, CLOSE, CROWN, EXIT, GEAR, PEOPLE, PLUS, SHIRT;

        final ResourceLocation id = gui("icon_" + name().toLowerCase(java.util.Locale.ROOT));
    }

    private static final Map<ResourceLocation, AbstractTexture> SMOOTHED = new HashMap<>();

    private Ui() {}

    private static ResourceLocation gui(String name) {
        return new ResourceLocation(BackroomsMod.MOD_ID, "textures/gui/lobby/" + name + ".png");
    }

    /** Pastikan tekstur ini difilter halus (bukan piksel tajam ala Minecraft). */
    private static void smooth(ResourceLocation id) {
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(id);
        if (SMOOTHED.get(id) != texture) {
            texture.setFilter(true, false);
            SMOOTHED.put(id, texture);
        }
    }

    // ----------------------------------------------------------- gambar

    /** Tekstur 9-slice direntangkan ke kotak (x, y, w, h). */
    public static void nine(GuiGraphics g, Tex tex, int x, int y, int w, int h) {
        smooth(tex.id);
        RenderSystem.enableBlend();
        int b = tex.border;
        int gb = Math.min(Math.round(b / (float) DENSITY), Math.min(w, h) / 2);
        int tw = tex.width, th = tex.height;
        int cw = tw - 2 * b, ch = th - 2 * b;
        int iw = w - 2 * gb, ih = h - 2 * gb;
        // sudut
        g.blit(tex.id, x, y, gb, gb, 0, 0, b, b, tw, th);
        g.blit(tex.id, x + w - gb, y, gb, gb, tw - b, 0, b, b, tw, th);
        g.blit(tex.id, x, y + h - gb, gb, gb, 0, th - b, b, b, tw, th);
        g.blit(tex.id, x + w - gb, y + h - gb, gb, gb, tw - b, th - b, b, b, tw, th);
        // tepi
        g.blit(tex.id, x + gb, y, iw, gb, b, 0, cw, b, tw, th);
        g.blit(tex.id, x + gb, y + h - gb, iw, gb, b, th - b, cw, b, tw, th);
        g.blit(tex.id, x, y + gb, gb, ih, 0, b, b, ch, tw, th);
        g.blit(tex.id, x + w - gb, y + gb, gb, ih, tw - b, b, b, ch, tw, th);
        // tengah
        g.blit(tex.id, x + gb, y + gb, iw, ih, b, b, cw, ch, tw, th);
        RenderSystem.disableBlend();
    }

    public static void nine(GuiGraphics g, Tex tex, int x, int y, int w, int h, int color) {
        g.setColor(((color >> 16) & 255) / 255.0F, ((color >> 8) & 255) / 255.0F, (color & 255) / 255.0F, ((color >>> 24) & 255) / 255.0F);
        nine(g, tex, x, y, w, h);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void image(GuiGraphics g, Tex tex, int x, int y, int w, int h) {
        smooth(tex.id);
        RenderSystem.enableBlend();
        g.blit(tex.id, x, y, w, h, 0, 0, tex.width, tex.height, tex.width, tex.height);
        RenderSystem.disableBlend();
    }

    public static void icon(GuiGraphics g, Icon icon, int x, int y, int size) {
        smooth(icon.id);
        RenderSystem.enableBlend();
        g.blit(icon.id, x, y, size, size, 0, 0, 48, 48, 48, 48);
        RenderSystem.disableBlend();
    }

    /** Motif wallpaper Level 0 diulang memenuhi kotak (dipotong scissor). */
    public static void wallpaper(GuiGraphics g, int x, int y, int w, int h) {
        smooth(Tex.WALLPAPER.id);
        RenderSystem.enableBlend();
        g.enableScissor(x, y, x + w, y + h);
        for (int ty = y; ty < y + h; ty += 16) {
            for (int tx = x; tx < x + w; tx += 16) {
                g.blit(Tex.WALLPAPER.id, tx, ty, 16, 16, 0, 0, 48, 48, 48, 48);
            }
        }
        g.disableScissor();
        RenderSystem.disableBlend();
    }

    /** Kilau yang menyapu tombol utama sesekali. */
    public static void shine(GuiGraphics g, int x, int y, int w, int h) {
        long period = 3200L;
        float t = (System.currentTimeMillis() % period) / 900.0F;
        if (t > 1.0F) return;
        int sx = x - 30 + Math.round((w + 40) * t);
        smooth(Tex.SHINE.id);
        RenderSystem.enableBlend();
        g.enableScissor(x + 2, y + 1, x + w - 2, y + h - 4);
        g.pose().pushPose();
        g.pose().translate(sx, y, 0.0F);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(18.0F));
        g.blit(Tex.SHINE.id, 0, -h, 18, h * 3, 0, 0, Tex.SHINE.width, Tex.SHINE.height, Tex.SHINE.width, Tex.SHINE.height);
        g.pose().popPose();
        g.disableScissor();
        RenderSystem.disableBlend();
    }

    /** Wajah skin di dalam bingkai avatar. */
    public static void avatar(GuiGraphics g, UUID player, int x, int y, int size, boolean gold) {
        int inset = Math.max(2, Math.round(size * 3 / 32.0F) + 1);
        g.fill(x + inset, y + inset, x + size - inset, y + size - inset, 0xFF0E1226);
        PlayerFaceRenderer.draw(g, skin(player), x + inset, y + inset, size - inset * 2);
        image(g, gold ? Tex.RING : Tex.RING_PLAIN, x, y, size, size);
    }

    private static ResourceLocation skin(UUID player) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(player);
        return info != null ? info.getSkinLocation() : DefaultPlayerSkin.getDefaultSkin(player);
    }

    // ------------------------------------------------------------- teks

    public static Component text(String text, Size size) {
        return Component.literal(text).withStyle(style -> style.withFont(size.font));
    }

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    public static int width(Component text) {
        return font().width(text);
    }

    /*
     * Posisi vertikal teks diukur dari metrik Lilita One (ascent 923, descent
     * 220, huruf kapital 222..923 per 1000 em): Minecraft menskalakan font TTF
     * sehingga ascent+descent = ukuran font, dan menaruh garis ascent-nya
     * sekitar 3,75 piksel di atas Y yang diminta (diukur dari screenshot).
     */
    private static final float CAP_RATIO = (923 - 222) / 1143.0F;
    private static final float CAP_DROP = 222 / 1143.0F;
    private static final float ASCENT_LIFT = 3.75F;

    /**
     * Teks Lilita One dengan {@code capTop} = tepi atas huruf kapital;
     * {@code outline} memberi kontur gelap tebal seperti teks game mobile.
     */
    public static void draw(GuiGraphics g, Component text, float x, float capTop, int color, boolean outline) {
        float lineY = capTop + ASCENT_LIFT - CAP_DROP * sizeOf(text).px;
        g.pose().pushPose();
        g.pose().translate(x, lineY, 0.0F);
        if (outline) {
            font().drawInBatch8xOutline(text.getVisualOrderText(), 0.0F, 0.0F, color, OUTLINE,
                    g.pose().last().pose(), g.bufferSource(), LightTexture.FULL_BRIGHT);
            g.flush();
        } else {
            g.drawString(font(), text, 0, 0, color, false);
        }
        g.pose().popPose();
    }

    /** Teks yang huruf kapitalnya tepat di tengah kotak setinggi {@code boxH} mulai {@code boxTop}. */
    public static void drawMiddle(GuiGraphics g, Component text, float x, float boxTop, float boxH, int color, boolean outline) {
        draw(g, text, x, boxTop + (boxH - capHeightF(sizeOf(text))) / 2.0F, color, outline);
    }

    public static void drawCentered(GuiGraphics g, Component text, float cx, float capTop, int color, boolean outline) {
        draw(g, text, cx - width(text) / 2.0F, capTop, color, outline);
    }

    public static void drawRight(GuiGraphics g, Component text, float rx, float capTop, int color, boolean outline) {
        draw(g, text, rx - width(text), capTop, color, outline);
    }

    /** Tinggi huruf kapital untuk ukuran ini. */
    public static int capHeight(Size size) {
        return Math.round(capHeightF(size));
    }

    private static float capHeightF(Size size) {
        return CAP_RATIO * size.px;
    }

    private static Size sizeOf(Component text) {
        ResourceLocation font = text.getStyle().getFont();
        for (Size s : Size.values()) {
            if (s.font.equals(font)) return s;
        }
        return Size.NORMAL;
    }

    public static Component fit(Component text, int width) {
        if (width(text) <= width) return text;
        String plain = text.getString();
        Component cut = text;
        while (plain.length() > 1 && width(cut) > width) {
            plain = plain.substring(0, plain.length() - 1);
            cut = Component.literal(plain + "…").setStyle(text.getStyle());
        }
        return cut;
    }
}
