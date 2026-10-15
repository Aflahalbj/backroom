package com.backrooms.client.lobby.screen;

import com.backrooms.client.ClientConfig;
import com.backrooms.client.lobby.LobbyClient;
import com.backrooms.client.lobby.LobbyTrack;
import com.backrooms.lobby.LobbyPose;
import com.backrooms.lobby.LobbyWeapon;
import com.backrooms.client.lobby.LobbyWeapons;
import com.backrooms.network.LobbyActionC2S;
import com.backrooms.world.BackroomsLevel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import java.util.function.BooleanSupplier;

/**
 * Lemari penampilan: panel di kanan dengan tab Latar / Pose / Senjata / Musik dan
 * kartu pilihan; karakter bergeser ke kiri supaya tetap terlihat, dan
 * setiap pilihan langsung tampak padanya. Di dalam room, tab Latar hanya
 * untuk owner (latar room ikut pilihannya); anggota lain hanya Pose,
 * Senjata, dan Musik.
 */
public class CustomizeScreen extends LobbyPageScreen {

    private enum Tab { LATAR, POSE, SENJATA, MUSIK }

    /** Warna khas tiap level, untuk garis di kartu latar. */
    private static final int[] LEVEL_COLORS = {0xFFE2C64F, 0xFF8A8F99, 0xFF41D7E3};
    private static Tab tab = Tab.LATAR;

    public CustomizeScreen() {
        super("Customize");
    }

    @Override
    public LobbyClient.Page page() {
        return LobbyClient.Page.CUSTOMIZE;
    }

    private int panelW() {
        return Mth.clamp(Math.round(width * 0.38F), 176, 230);
    }

    private int panelX() {
        return width - panelW() - 12;
    }

    private int panelY() {
        return 12;
    }

    private int panelH() {
        return height - 24;
    }

    @Override
    protected void build() {
        addRenderableWidget(new IconButton(12, 10, 24, Ui.Icon.BACK, "Kembali", false, LobbyClient::back));

        int x = panelX() + 10, w = panelW() - 20;
        Tab[] tabs = canPickBackground() ? Tab.values() : new Tab[] {Tab.POSE, Tab.SENJATA, Tab.MUSIK};
        if (tab == Tab.LATAR && !canPickBackground()) tab = Tab.POSE;
        int tabW = (w - 4 * (tabs.length - 1)) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            addRenderableWidget(new GameButton(x + i * (tabW + 4), panelY() + 10, tabW, 22,
                    tab == t ? GameButton.Style.YELLOW : GameButton.Style.BLUE, null, tabName(t), Ui.Size.NORMAL, () -> {
                tab = t;
                rebuildWidgets();
            }));
        }

        int y = panelY() + 42;
        int colW = (w - 6) / 2;
        switch (tab) {
            case LATAR -> {
                BackroomsLevel[] levels = BackroomsLevel.values();
                for (int i = 0; i < levels.length; i++) {
                    int value = i;
                    addRenderableWidget(new OptionCard(x, y + i * 38, w, 32, levels[i].displayName(), LEVEL_COLORS[i],
                            () -> LobbyClient.background() == value,
                            () -> LobbyClient.send(LobbyActionC2S.Action.SET_BACKGROUND, value)));
                }
            }
            case POSE -> {
                LobbyPose[] poses = LobbyPose.values();
                for (int i = 0; i < poses.length; i++) {
                    int value = i;
                    addRenderableWidget(new OptionCard(x + (i % 2) * (colW + 6), y + (i / 2) * 36, colW, 30,
                            poses[i].displayName(), 0, () -> LobbyClient.pose().ordinal() == value,
                            () -> LobbyClient.send(LobbyActionC2S.Action.SET_POSE, value)));
                }
            }
            case SENJATA -> {
                LobbyWeapon[] weapons = LobbyWeapon.values();
                for (int i = 0; i < weapons.length; i++) {
                    int value = i;
                    OptionCard card = new OptionCard(x + (i % 2) * (colW + 6), y + (i / 2) * 34, colW, 28,
                            weapons[i].displayName(), 0, () -> LobbyClient.weapon().ordinal() == value,
                            () -> LobbyClient.send(LobbyActionC2S.Action.SET_WEAPON, value));
                    card.active = value == 0 || LobbyWeapons.available();
                    addRenderableWidget(card);
                }
            }
            case MUSIK -> {
                LobbyTrack[] tracks = LobbyTrack.values();
                for (int i = 0; i < tracks.length; i++) {
                    int value = i;
                    addRenderableWidget(new OptionCard(x + (i % 2) * (colW + 6), y + (i / 2) * 34, colW, 28,
                            tracks[i].displayName(), 0, () -> ClientConfig.lobbySound() == value,
                            () -> ClientConfig.set(ClientConfig.LOBBY_SOUND, value)));
                }
            }
        }
    }

    /** Latar boleh dipilih di luar room, atau oleh owner room. */
    private static boolean canPickBackground() {
        return LobbyClient.room() == null || LobbyClient.isOwner();
    }

    private static String tabName(Tab t) {
        return switch (t) {
            case LATAR -> "Latar";
            case POSE -> "Pose";
            case SENJATA -> "Senjata";
            case MUSIK -> "Musik";
        };
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Ui.draw(g, Ui.text("Penampilan", Ui.Size.BIG), 42, 15, Ui.WHITE, true);
        Ui.nine(g, Ui.Tex.PANEL, panelX(), panelY(), panelW(), panelH());
        int x = panelX() + 10;
        int y = panelY() + 42;
        if (tab == Tab.SENJATA && !LobbyWeapons.available()) {
            int rows = (LobbyWeapon.values().length + 1) / 2;
            Ui.draw(g, Ui.text("Pasang mod TACZ untuk memakai senjata.", Ui.Size.SMALL), x, y + rows * 34 + 4, Ui.DIM, false);
        }
    }

    @Override
    protected void onEscape() {
        LobbyClient.back();
    }

    /** Kartu pilihan: terpilih = bingkai kuning dan tanda centang. */
    private static final class OptionCard extends AbstractButton {
        private final BooleanSupplier selected;
        private final Runnable action;
        private final int swatch;

        OptionCard(int x, int y, int w, int h, String label, int swatch, BooleanSupplier selected, Runnable action) {
            super(x, y, w, h, Ui.text(label, Ui.Size.NORMAL));
            this.selected = selected;
            this.action = action;
            this.swatch = swatch;
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean on = selected.getAsBoolean();
            boolean hot = isHoveredOrFocused();
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            Ui.nine(g, on ? Ui.Tex.CARD_SELECTED : Ui.Tex.CARD, x, y, w, h);
            if (hot && !on) g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0x1AFFFFFF);
            int textX = x + 9;
            if (swatch != 0) {
                g.fill(x + 7, y + 7, x + 11, y + h - 7, swatch);
                textX = x + 17;
            }
            Component label = Ui.fit(getMessage(), w - (textX - x) - 20);
            Ui.drawMiddle(g, label, textX, y, h, on ? Ui.WHITE : 0xFFD6DCEE, true);
            if (on) Ui.icon(g, Ui.Icon.CHECK, x + w - 17, y + (h - 12) / 2, 12);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }

        @Override
        public void playDownSound(SoundManager manager) {
            manager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2F));
        }
    }
}
