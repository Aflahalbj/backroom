package com.backrooms.client.lobby.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/** Tombol ikon di pojok layar, dengan label kuning berkontur di bawahnya (boleh tanpa label). */
public class IconButton extends AbstractButton {

    private final Ui.Icon icon;
    private final Runnable action;
    private final boolean showLabel;

    public IconButton(int x, int y, int size, Ui.Icon icon, String label, boolean showLabel, Runnable action) {
        super(x, y, size, size, Component.literal(label));
        this.icon = icon;
        this.action = action;
        this.showLabel = showLabel;
    }

    @Override
    public void onPress() {
        action.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hot = isHoveredOrFocused();
        int x = getX(), y = getY() - (hot ? 1 : 0), s = getWidth();
        Ui.nine(g, Ui.Tex.CARD, x, y, s, s);
        if (hot) g.fill(x + 2, y + 2, x + s - 2, y + s - 2, 0x22FFFFFF);
        int is = Math.round(s * 0.62F);
        Ui.icon(g, icon, x + (s - is) / 2, y + (s - is) / 2, is);
        if (showLabel) {
            Component label = Ui.text(getMessage().getString(), Ui.Size.SMALL);
            Ui.drawCentered(g, label, x + s / 2, y + s + 3, hot ? Ui.WHITE : 0xFFFFD84A, true);
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
