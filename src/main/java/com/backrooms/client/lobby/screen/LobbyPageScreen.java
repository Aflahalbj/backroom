package com.backrooms.client.lobby.screen;

import com.backrooms.client.lobby.LobbyClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Dasar semua layar lobby: dunia (karakter dan latarnya) tetap terlihat,
 * game tidak dijeda, dan tepi atas/bawah layar sedikit digelapkan supaya
 * UI di pojok terbaca. Tombolnya dibangun ulang setiap kali keadaan lobby
 * dari server berubah.
 *
 * <p>T membuka chat, Esc membuka menu game (atau kembali, di sub-layar).
 */
public abstract class LobbyPageScreen extends Screen {

    private int builtVersion;

    protected LobbyPageScreen(String title) {
        super(Component.literal(title));
    }

    /** Halaman lobby yang ditampilkan layar ini. */
    public abstract LobbyClient.Page page();

    /** Pasang tombol-tombol layar ini. */
    protected abstract void build();

    /** Gambar di atas dunia, di bawah tombol. */
    protected abstract void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick);

    @Override
    protected final void init() {
        builtVersion = LobbyClient.version();
        build();
    }

    @Override
    public void tick() {
        if (builtVersion != LobbyClient.version()) {
            rebuildWidgets();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        // Dunia tetap terlihat.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!LobbyClient.isReady()) {
            renderPreparing(g);
            return;
        }
        g.fillGradient(0, 0, width, 46, 0x99000000, 0x00000000);
        g.fillGradient(0, height - 58, width, height, 0x00000000, 0xAA000000);
        renderContent(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        // Ganti latar: layar muncul dari gelap, menjawab pilihan pemain.
        float dark = LobbyClient.transitionAlpha();
        if (dark > 0.0F) {
            g.fill(0, 0, width, height, ((int) (255 * dark)) << 24);
        }
    }

    private void renderPreparing(GuiGraphics g) {
        g.fillGradient(0, 0, width, height, 0xFF141B33, 0xFF06080F);
        Ui.drawCentered(g, Ui.text("Menyiapkan lobby", Ui.Size.BIG), width / 2, height / 2 - 22, Ui.WHITE, true);
        int bw = Math.min(220, width - 80);
        int bx = (width - bw) / 2, by = height / 2;
        Ui.nine(g, Ui.Tex.PILL, bx, by, bw, 10, 0xFF0B1022);
        int fw = Math.max(10, Math.round(bw * LobbyClient.percent() / 100.0F));
        Ui.nine(g, Ui.Tex.PILL, bx, by, fw, 10, 0xFFFFC21A);
        Ui.drawCentered(g, Ui.text(LobbyClient.percent() + "%", Ui.Size.SMALL), width / 2, by + 16, Ui.DIM, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onEscape();
            return true;
        }
        if (minecraft != null && minecraft.options.keyChat.matches(keyCode, scanCode)) {
            minecraft.setScreen(new ChatScreen(""));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    protected void onEscape() {
        if (minecraft != null) minecraft.setScreen(new PauseScreen(true));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!super.mouseDragged(mouseX, mouseY, button, dragX, dragY) && button == 0) {
            LobbyClient.dragCharacter(dragX);
        }
        return true;
    }

    /** Pil kecil berisi teks (status, ID room, jumlah pemain). */
    protected static int pill(GuiGraphics g, String text, int x, int y, int background, int color) {
        Component label = Ui.text(text, Ui.Size.SMALL);
        int w = Ui.width(label) + 12;
        Ui.nine(g, Ui.Tex.PILL, x, y, w, 12, background);
        Ui.drawMiddle(g, label, x + 6, y, 12, color, false);
        return w;
    }
}
