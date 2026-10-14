package com.backrooms.client.lobby.screen;

import com.backrooms.client.lobby.LobbyClient;
import com.backrooms.lobby.LobbyPose;
import com.backrooms.lobby.RoomInfo;
import com.backrooms.lobby.RoomState;
import com.backrooms.network.LobbyActionC2S;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.UUID;

/**
 * Ruang tunggu room seperti custom room game mobile: judul room di atas,
 * empat slot pemain di kiri dan empat di kanan, karakter berjajar di tengah,
 * tombol Mulai di kanan bawah (berubah jadi bar progres saat level dibangun).
 */
public class RoomScreen extends LobbyPageScreen {

    private static final int CARD_H = 30;
    private static final int GAP = 6;

    public RoomScreen() {
        super("Room");
    }

    @Override
    public LobbyClient.Page page() {
        return LobbyClient.Page.ROOM;
    }

    private int cardW() {
        return Mth.clamp(Math.round(width * 0.24F), 100, 132);
    }

    private int cardX(int slot) {
        return slot < 4 ? 12 : width - 12 - cardW();
    }

    private int cardY(int slot) {
        return 58 + (slot % 4) * (CARD_H + GAP);
    }

    @Override
    protected void build() {
        RoomInfo room = LobbyClient.room();
        if (room == null) return;
        int bottom = height - 40;
        addRenderableWidget(new GameButton(12, bottom, 84, 28, GameButton.Style.ORANGE, Ui.Icon.EXIT,
                "Keluar", Ui.Size.NORMAL, () -> LobbyClient.send(LobbyActionC2S.Action.LEAVE_ROOM)));
        addRenderableWidget(new GameButton(102, bottom, 108, 28, GameButton.Style.BLUE, Ui.Icon.SHIRT,
                "Penampilan", Ui.Size.NORMAL, () -> LobbyClient.openPage(LobbyClient.Page.CUSTOMIZE)));

        int bigW = 128, bigH = 38;
        int bigX = width - bigW - 14, bigY = height - bigH - 14;
        boolean host = LobbyClient.isHost();
        GameButton main = switch (room.state()) {
            case WAITING -> host
                    ? new GameButton(bigX, bigY, bigW, bigH, GameButton.Style.YELLOW, null, "Mulai", Ui.Size.HUGE,
                    () -> LobbyClient.send(LobbyActionC2S.Action.START)).hero()
                    : new GameButton(bigX, bigY, bigW, bigH, GameButton.Style.GRAY, null, "Tunggu host", Ui.Size.BIG, () -> {});
            case BUILDING -> new GameButton(bigX, bigY, bigW, bigH, GameButton.Style.YELLOW, null,
                    "Menyiapkan " + room.progress() + "%", Ui.Size.BIG, () -> {}).hero().progress(() -> {
                RoomInfo current = LobbyClient.room();
                return current == null ? 0 : current.progress();
            });
            case PLAYING -> new GameButton(bigX, bigY, bigW, bigH, GameButton.Style.GRAY, null, "Masuk…", Ui.Size.BIG, () -> {});
        };
        main.active = host && room.state() == RoomState.WAITING;
        addRenderableWidget(main);

        if (host && room.state() == RoomState.WAITING) {
            for (int i = 0; i < room.members().size(); i++) {
                UUID id = room.members().get(i).id();
                if (id.equals(room.host())) continue;
                addRenderableWidget(new KickButton(cardX(i) + cardW() - 15, cardY(i) + 3, () -> LobbyClient.kick(id)));
            }
        }
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        RoomInfo room = LobbyClient.room();
        if (room == null) return;
        Ui.drawCentered(g, Ui.fit(Ui.text(room.name(), Ui.Size.HUGE), width / 2), width / 2, 12, Ui.WHITE, true);
        String id = "ID " + room.id();
        String count = room.members().size() + "/" + RoomInfo.MAX_MEMBERS + " pemain";
        int w1 = Ui.width(Ui.text(id, Ui.Size.SMALL)) + 12;
        int w2 = Ui.width(Ui.text(count, Ui.Size.SMALL)) + 12;
        int px = width / 2 - (w1 + 4 + w2) / 2;
        pill(g, id, px, 34, 0xCC0B1022, Ui.DIM);
        pill(g, count, px + w1 + 4, 34, 0xCC0B1022, Ui.WHITE);

        for (int slot = 0; slot < RoomInfo.MAX_MEMBERS; slot++) {
            card(g, room, slot);
        }
    }

    private void card(GuiGraphics g, RoomInfo room, int slot) {
        int x = cardX(slot), y = cardY(slot), w = cardW();
        if (slot >= room.members().size()) {
            Ui.nine(g, Ui.Tex.CARD_EMPTY, x, y, w, CARD_H);
            Component empty = Ui.text("Kosong", Ui.Size.SMALL);
            int total = 12 + 4 + Ui.width(empty);
            int left = x + (w - total) / 2;
            g.setColor(1.0F, 1.0F, 1.0F, 0.45F);
            Ui.icon(g, Ui.Icon.PLUS, left, y + 9, 12);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            Ui.drawMiddle(g, empty, left + 16, y + 9, 12, 0xFF7C84A0, false);
            return;
        }
        RoomInfo.Member member = room.members().get(slot);
        boolean host = member.id().equals(room.host());
        boolean self = minecraft != null && minecraft.player != null && member.id().equals(minecraft.player.getUUID());
        Ui.nine(g, host ? Ui.Tex.CARD_GOLD : Ui.Tex.CARD, x, y, w, CARD_H);
        Ui.avatar(g, member.id(), x + 4, y + 3, 24, host);
        Component name = Ui.fit(Ui.text(member.name(), Ui.Size.NORMAL), w - 36);
        Ui.drawMiddle(g, name, x + 32, y + 4, 11, Ui.WHITE, true);
        if (host) {
            Ui.icon(g, Ui.Icon.CROWN, x + 31, y + 16, 10);
            Ui.drawMiddle(g, Ui.text("Host", Ui.Size.SMALL), x + 43, y + 16, 10, Ui.GOLD, false);
        } else {
            String tag = self ? "Kamu" : LobbyPose.byId(member.pose()).displayName();
            Ui.drawMiddle(g, Ui.text(tag, Ui.Size.SMALL), x + 32, y + 16, 10, Ui.DIM, false);
        }
    }

    /** Untuk host: keluarkan anggota dari room (ikon silang kecil di kartunya). */
    private static final class KickButton extends AbstractButton {
        private final Runnable action;

        KickButton(int x, int y, Runnable action) {
            super(x, y, 12, 12, Component.literal("Keluarkan"));
            this.action = action;
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean hot = isHoveredOrFocused();
            g.setColor(1.0F, hot ? 0.45F : 1.0F, hot ? 0.45F : 1.0F, hot ? 1.0F : 0.55F);
            Ui.icon(g, Ui.Icon.CLOSE, getX(), getY(), 12);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
