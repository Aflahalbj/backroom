package com.backrooms.client.lobby.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.function.IntSupplier;

/**
 * Tombol game: badan 3D berwarna, ikon di kiri, teks Lilita One. Tombol
 * utama (kuning) memakai motif wallpaper Level 0 dan kilau yang lewat
 * sesekali. Bisa diberi badge angka merah dan isian progres.
 */
public class GameButton extends AbstractButton {

    public enum Style {
        YELLOW(Ui.Tex.BTN_YELLOW), ORANGE(Ui.Tex.BTN_ORANGE), BLUE(Ui.Tex.BTN_BLUE), GRAY(Ui.Tex.BTN_GRAY);

        final Ui.Tex tex;

        Style(Ui.Tex tex) {
            this.tex = tex;
        }
    }

    private final Style style;
    private final Ui.Icon icon;
    private final Ui.Size size;
    private final Runnable action;
    private boolean hero;
    private IntSupplier badge = () -> 0;
    /** -1 = bukan tombol progres; 0..100 = sebagian terisi. */
    private IntSupplier progress = () -> -1;

    public GameButton(int x, int y, int width, int height, Style style, Ui.Icon icon, String label, Ui.Size size, Runnable action) {
        super(x, y, width, height, Ui.text(label, size));
        this.style = style;
        this.icon = icon;
        this.size = size;
        this.action = action;
    }

    /** Tombol utama halaman: motif wallpaper dan kilau. */
    public GameButton hero() {
        this.hero = true;
        return this;
    }

    public GameButton badge(IntSupplier badge) {
        this.badge = badge;
        return this;
    }

    public GameButton progress(IntSupplier progress) {
        this.progress = progress;
        return this;
    }

    @Override
    public void onPress() {
        action.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX(), w = getWidth(), h = getHeight();
        boolean hot = active && isHoveredOrFocused();
        int y = getY() - (hot ? 1 : 0);
        int fill = progress.getAsInt();
        Style look = active ? style : Style.GRAY;

        if (fill >= 0) {
            Ui.nine(g, Ui.Tex.BTN_GRAY, x, y, w, h);
            int fw = Math.max(0, Math.round(w * fill / 100.0F));
            g.enableScissor(x, y, x + fw, y + h);
            Ui.nine(g, style.tex, x, y, w, h);
            if (hero) Ui.wallpaper(g, x + 3, y + 2, w - 6, h - 6);
            g.disableScissor();
        } else {
            Ui.nine(g, look.tex, x, y, w, h);
            if (hero && active) {
                Ui.wallpaper(g, x + 3, y + 2, w - 6, h - 6);
                Ui.shine(g, x, y, w, h);
            }
        }
        if (hot) {
            // Sorotan tipis supaya terasa "hidup" di bawah kursor/fokus.
            g.fill(x + 3, y + 2, x + w - 3, y + h - 4, 0x22FFFFFF);
        }

        boolean dark = look == Style.YELLOW;
        int color = dark ? Ui.BROWN : Ui.WHITE;
        Component label = Ui.fit(getMessage(), w - (icon == null ? 12 : Math.round(h * 0.55F) + 16));
        int iconSize = icon == null ? 0 : Math.min(Math.round(h * 0.55F), 22);
        int gap = icon == null ? 0 : 4;
        int total = iconSize + gap + Ui.width(label);
        int left = x + (w - total) / 2;
        // Badan tombol tanpa bibir 3D di bawahnya (3 px): ikon dan teks di tengahnya.
        int bodyH = h - 3;
        if (icon != null) Ui.icon(g, icon, left, y + Math.round((bodyH - iconSize) / 2.0F), iconSize);
        Ui.drawMiddle(g, label, left + iconSize + gap, y, bodyH, color, !dark);

        int count = badge.getAsInt();
        if (count > 0) {
            Component number = Ui.text(String.valueOf(count), Ui.Size.SMALL);
            int bw = Math.max(12, Ui.width(number) + 6);
            int bx = x + w - bw + 4, by = y - 5;
            Ui.nine(g, Ui.Tex.PILL, bx, by, bw, 12, Ui.RED);
            Ui.drawMiddle(g, number, bx + (bw - Ui.width(number)) / 2.0F, by, 12, Ui.WHITE, false);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    @Override
    public void playDownSound(SoundManager manager) {
        manager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
