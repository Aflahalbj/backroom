package com.backrooms.client.lobby.screen;

import com.backrooms.client.lobby.LobbyClient;
import com.backrooms.lobby.RoomInfo;
import com.backrooms.network.LobbyActionC2S;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Khusus owner: daftar pemain di lobby yang belum ikut room. Tombol "Tambah"
 * memasukkan pemain itu langsung ke room (tanpa dia bergabung sendiri),
 * "Tambah semua" memasukkan semuanya sampai room penuh. Daftarnya diminta
 * ulang dari server setiap dua detik selama jendela terbuka.
 */
public class AddPlayerScreen extends LobbyPageScreen {

    private static final int ROW = 28;
    private int scroll;
    private int age;

    public AddPlayerScreen() {
        super("Tambah Pemain");
    }

    @Override
    public LobbyClient.Page page() {
        return LobbyClient.Page.ADD_PLAYER;
    }

    private int panelW() {
        return Math.min(300, width - 40);
    }

    private int panelH() {
        return Math.min(height - 40, 230);
    }

    private int panelX() {
        return (width - panelW()) / 2;
    }

    private int panelY() {
        return (height - panelH()) / 2;
    }

    private int listY() {
        return panelY() + 40;
    }

    private int visibleRows() {
        return Math.max(1, (panelY() + panelH() - 10 - listY()) / ROW);
    }

    private static int freeSlots() {
        RoomInfo room = LobbyClient.room();
        return room == null ? 0 : RoomInfo.MAX_PLAYERS - room.players().size();
    }

    @Override
    protected void build() {
        addRenderableWidget(new IconButton(panelX() + panelW() - 30, panelY() + 8, 22, Ui.Icon.CLOSE, "Tutup", false,
                LobbyClient::back));
        List<RoomInfo.Candidate> players = LobbyClient.candidates();
        if (players.isEmpty()) return;
        boolean full = freeSlots() <= 0;
        GameButton all = new GameButton(panelX() + panelW() - 126, panelY() + 8, 90, 22,
                full ? GameButton.Style.GRAY : GameButton.Style.YELLOW, Ui.Icon.PEOPLE, "Semua", Ui.Size.NORMAL,
                () -> LobbyClient.send(LobbyActionC2S.Action.ADD_ALL));
        all.active = !full;
        addRenderableWidget(all);

        scroll = Mth.clamp(scroll, 0, Math.max(0, players.size() - visibleRows()));
        for (int i = 0; i < visibleRows() && scroll + i < players.size(); i++) {
            RoomInfo.Candidate player = players.get(scroll + i);
            GameButton add = new GameButton(panelX() + panelW() - 74, listY() + i * ROW + 3, 62, 18,
                    full ? GameButton.Style.GRAY : GameButton.Style.BLUE, null, full ? "Penuh" : "Tambah", Ui.Size.NORMAL,
                    () -> LobbyClient.addPlayer(player.id()));
            add.active = !full;
            addRenderableWidget(add);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (++age % 40 == 0) LobbyClient.send(LobbyActionC2S.Action.REQUEST_CANDIDATES);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x66000000);
        int x = panelX(), y = panelY(), w = panelW(), h = panelH();
        Ui.nine(g, Ui.Tex.PANEL, x, y, w, h);
        Ui.draw(g, Ui.text("Tambah pemain", Ui.Size.BIG), x + 14, y + 13, Ui.WHITE, true);

        List<RoomInfo.Candidate> players = LobbyClient.candidates();
        if (players.isEmpty()) {
            int cx = width / 2;
            Ui.icon(g, Ui.Icon.PEOPLE, cx - 14, listY() + 6, 28);
            Ui.drawCentered(g, Ui.text("Tidak ada pemain lain", Ui.Size.BIG), cx, listY() + 40, Ui.WHITE, true);
            Ui.drawCentered(g, Ui.text("Semua pemain di lobby sudah ikut room.", Ui.Size.SMALL), cx, listY() + 56, Ui.DIM, false);
            return;
        }
        for (int i = 0; i < visibleRows() && scroll + i < players.size(); i++) {
            RoomInfo.Candidate player = players.get(scroll + i);
            int ry = listY() + i * ROW;
            Ui.nine(g, Ui.Tex.CARD, x + 10, ry, w - 20, ROW - 4);
            Ui.avatar(g, player.id(), x + 13, ry + 3, ROW - 10, false);
            Component name = Ui.fit(Ui.text(player.name(), Ui.Size.NORMAL), w - 120);
            Ui.drawMiddle(g, name, x + 13 + ROW - 6, ry, ROW - 4, Ui.WHITE, true);
        }
        String slots = "Slot kosong: " + Math.max(0, freeSlots());
        String more = players.size() > visibleRows() ? "  |  gulir: " + (players.size() - visibleRows()) + " lagi" : "";
        Ui.draw(g, Ui.text(slots + more, Ui.Size.SMALL), x + 14, y + 28, Ui.DIM, false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int before = scroll;
        scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, Math.max(0, LobbyClient.candidates().size() - visibleRows()));
        if (scroll != before) rebuildWidgets();
        return true;
    }

    @Override
    protected void onEscape() {
        LobbyClient.back();
    }
}
