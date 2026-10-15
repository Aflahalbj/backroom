package com.backrooms.client.lobby.screen;

import com.backrooms.client.lobby.LobbyClient;
import com.backrooms.network.LobbyActionC2S;
import com.backrooms.world.BackroomsLevel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.network.chat.Component;

/**
 * Halaman depan lobby, seperti home game mobile: profil di kiri atas, ikon
 * di kanan atas, karakter di tengah, dan tombol main di kanan bawah.
 */
public class LobbyScreen extends LobbyPageScreen {

    public LobbyScreen() {
        super("Lobby");
    }

    @Override
    public LobbyClient.Page page() {
        return LobbyClient.Page.MAIN;
    }

    @Override
    protected void build() {
        addRenderableWidget(new IconButton(width - 34, 10, 24, Ui.Icon.GEAR, "Pengaturan", true,
                () -> minecraft.setScreen(new OptionsScreen(this, minecraft.options))));
        addRenderableWidget(new IconButton(width - 74, 10, 24, Ui.Icon.SHIRT, "Penampilan", true,
                () -> LobbyClient.openPage(LobbyClient.Page.CUSTOMIZE)));

        int bigW = 124, bigH = 38;
        int bigX = width - bigW - 14, bigY = height - bigH - 14;
        if (LobbyClient.canCreateRoom()) {
            addRenderableWidget(new GameButton(bigX, bigY, bigW, bigH, GameButton.Style.YELLOW, Ui.Icon.PLUS,
                    "Buat room", Ui.Size.BIG, () -> LobbyClient.send(LobbyActionC2S.Action.CREATE_ROOM)).hero());
            addRenderableWidget(new GameButton(bigX - 112, bigY + 8, 104, 30, GameButton.Style.ORANGE, Ui.Icon.PEOPLE,
                    "Gabung room", Ui.Size.NORMAL, () -> LobbyClient.openPage(LobbyClient.Page.JOIN))
                    .badge(() -> LobbyClient.rooms().size()));
        } else {
            // Bukan OP: tidak bisa membuat room, jadi Gabung room yang jadi tombol utama.
            addRenderableWidget(new GameButton(bigX, bigY, bigW, bigH, GameButton.Style.YELLOW, Ui.Icon.PEOPLE,
                    "Gabung room", Ui.Size.BIG, () -> LobbyClient.openPage(LobbyClient.Page.JOIN)).hero()
                    .badge(() -> LobbyClient.rooms().size()));
        }
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (minecraft == null || minecraft.player == null) return;
        // Profil.
        Ui.avatar(g, minecraft.player.getUUID(), 12, 10, 32, true);
        Component name = Ui.text(minecraft.player.getGameProfile().getName(), Ui.Size.BIG);
        Ui.draw(g, Ui.fit(name, width / 3), 50, 13, Ui.WHITE, true);
        String background = BackroomsLevel.values()[LobbyClient.background()].displayName();
        pill(g, "Latar: " + background, 50, 29, 0xE62B6EE8, Ui.WHITE);

        // Chat.
        int cy = height - 30;
        Component hint = Ui.text("Tekan T untuk chat", Ui.Size.SMALL);
        int cw = Ui.width(hint) + 30;
        Ui.nine(g, Ui.Tex.CARD, 12, cy, cw, 18);
        Ui.icon(g, Ui.Icon.CHAT, 16, cy + 3, 12);
        Ui.drawMiddle(g, hint, 32, cy + 3, 12, Ui.WHITE, false);
    }
}
