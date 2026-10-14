package com.backrooms.client.lobby.screen;

import com.backrooms.client.lobby.LobbyClient;
import com.backrooms.lobby.RoomInfo;
import com.backrooms.lobby.RoomState;
import com.backrooms.network.LobbyActionC2S;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

/** Jendela daftar room yang aktif; diperbarui otomatis setiap ada room yang berubah. */
public class JoinRoomScreen extends LobbyPageScreen {

    private static final int ROW = 34;
    private int scroll;

    public JoinRoomScreen() {
        super("Gabung Room");
    }

    @Override
    public LobbyClient.Page page() {
        return LobbyClient.Page.JOIN;
    }

    private int panelW() {
        return Math.min(330, width - 40);
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

    @Override
    protected void build() {
        addRenderableWidget(new IconButton(panelX() + panelW() - 30, panelY() + 8, 22, Ui.Icon.CLOSE, "Tutup", false,
                LobbyClient::back));
        List<RoomInfo.Summary> rooms = LobbyClient.rooms();
        if (rooms.isEmpty()) {
            addRenderableWidget(new GameButton(width / 2 - 56, listY() + 70, 112, 32, GameButton.Style.YELLOW, Ui.Icon.PLUS,
                    "Buat room", Ui.Size.BIG, () -> LobbyClient.send(LobbyActionC2S.Action.CREATE_ROOM)).hero());
            return;
        }
        scroll = Mth.clamp(scroll, 0, Math.max(0, rooms.size() - visibleRows()));
        for (int i = 0; i < visibleRows() && scroll + i < rooms.size(); i++) {
            RoomInfo.Summary room = rooms.get(scroll + i);
            boolean full = room.members() >= RoomInfo.MAX_MEMBERS;
            GameButton join = new GameButton(panelX() + panelW() - 74, listY() + i * ROW + 5, 62, 22,
                    full ? GameButton.Style.GRAY : GameButton.Style.YELLOW, null, full ? "Penuh" : "Gabung", Ui.Size.NORMAL,
                    () -> LobbyClient.send(LobbyActionC2S.Action.JOIN_ROOM, room.id()));
            join.active = !full;
            addRenderableWidget(join);
        }
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x66000000);
        int x = panelX(), y = panelY(), w = panelW(), h = panelH();
        Ui.nine(g, Ui.Tex.PANEL, x, y, w, h);
        Ui.draw(g, Ui.text("Gabung room", Ui.Size.BIG), x + 14, y + 13, Ui.WHITE, true);

        List<RoomInfo.Summary> rooms = LobbyClient.rooms();
        if (rooms.isEmpty()) {
            int cx = width / 2;
            Ui.icon(g, Ui.Icon.PEOPLE, cx - 14, listY() + 6, 28);
            Ui.drawCentered(g, Ui.text("Belum ada room aktif", Ui.Size.BIG), cx, listY() + 40, Ui.WHITE, true);
            Ui.drawCentered(g, Ui.text("Buat room dan ajak temanmu.", Ui.Size.SMALL), cx, listY() + 56, Ui.DIM, false);
            return;
        }
        for (int i = 0; i < visibleRows() && scroll + i < rooms.size(); i++) {
            RoomInfo.Summary room = rooms.get(scroll + i);
            int ry = listY() + i * ROW;
            Ui.nine(g, Ui.Tex.CARD, x + 10, ry, w - 20, ROW - 4);
            Component name = Ui.fit(Ui.text(room.name(), Ui.Size.NORMAL), w - 190);
            Ui.drawMiddle(g, name, x + 18, ry + 3, 12, Ui.WHITE, true);
            Ui.drawMiddle(g, Ui.fit(Ui.text("Host: " + room.hostName(), Ui.Size.SMALL), w - 190), x + 18, ry + 16, 10, Ui.DIM, false);
            int px = x + w - 168;
            int count = pill(g, room.members() + "/" + RoomInfo.MAX_MEMBERS, px, ry + 9, 0xCC0B1022, Ui.WHITE);
            int status = room.state() == RoomState.PLAYING ? Ui.RED : room.state() == RoomState.BUILDING ? 0xFFF5A623 : Ui.GREEN;
            pill(g, room.state() == RoomState.PLAYING ? "Sedang main" : room.state() == RoomState.BUILDING ? "Bersiap" : "Menunggu",
                    px + count + 4, ry + 9, status, Ui.WHITE);
        }
        if (rooms.size() > visibleRows()) {
            Ui.drawRight(g, Ui.text("Gulir untuk melihat " + (rooms.size() - visibleRows()) + " room lagi", Ui.Size.SMALL),
                    x + w - 14, y + 16, Ui.DIM, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int before = scroll;
        scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, Math.max(0, LobbyClient.rooms().size() - visibleRows()));
        if (scroll != before) rebuildWidgets();
        return true;
    }

    @Override
    protected void onEscape() {
        LobbyClient.back();
    }
}
