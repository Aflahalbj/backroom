package com.backrooms.client.lobby.screen;

import com.backrooms.client.lobby.IntroCutscene;
import com.backrooms.client.lobby.LobbyClient;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Terbuka selama noclip dan intro: menahan semua input, menyembunyikan
 * kursor, dan menggambar layar hitam serta judul intro.
 *
 * <p>Judulnya port dari {@code com.sp.render.gui.TitleText}.
 */
public class CutsceneScreen extends Screen {

    /** Kedipan lampu sebelum karakter tenggelam (ms sejak noclip dimulai). */
    private static final long[][] FLICKERS = {{90L, 200L}, {300L, 360L}, {470L, 520L}};
    private static final long NOCLIP_BLACK = 2700L;

    public CutsceneScreen() {
        super(Component.empty());
    }

    @Override
    protected void init() {
        if (minecraft != null) {
            GLFW.glfwSetInputMode(minecraft.getWindow().getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
        }
    }

    @Override
    public void removed() {
        if (minecraft != null) {
            GLFW.glfwSetInputMode(minecraft.getWindow().getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics g) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long noclip = LobbyClient.noclipTime();
        if (noclip >= 0L) {
            boolean black = noclip >= NOCLIP_BLACK;
            for (long[] flicker : FLICKERS) {
                if (noclip >= flicker[0] && noclip < flicker[1]) black = true;
            }
            if (black) g.fill(0, 0, width, height, 0xFF000000);
            return;
        }
        if (IntroCutscene.BLACK_SCREEN.isBlackScreen) {
            g.fill(0, 0, width, height, 0xFF000000);
        }
        if (IntroCutscene.showTitle()) {
            renderTitle(g);
        }
    }

    private void renderTitle(GuiGraphics g) {
        Component text = Component.translatable("intro.backrooms");
        Component text2 = Component.translatable("intro.credit").append(Component.literal("SpacePotato").withStyle(ChatFormatting.GOLD));
        g.pose().pushPose();
        g.pose().translate((float) (width / 2), (float) (height / 2), 0.0F);
        RenderSystem.enableBlend();
        g.pose().pushPose();
        g.pose().scale(2.5F, 2.5F, 2.5F);
        int w = font.width(text);
        int h = 9;
        g.drawString(font, text, -w / 2, -h / 2, 0xFFFFFF, true);
        g.pose().popPose();
        g.pose().pushPose();
        g.pose().scale(1.0F, 1.0F, 1.0F);
        int w2 = font.width(text2);
        g.drawString(font, text2, -w2 / 2, 10, 0xFFFFFF, true);
        g.pose().popPose();
        RenderSystem.disableBlend();
        g.pose().popPose();
    }
}
