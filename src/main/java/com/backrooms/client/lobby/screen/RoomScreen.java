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

import java.util.List;
import java.util.UUID;

/**
 * Ruang tunggu room seperti custom room game mobile: judul room di atas,
 * lima slot pemain di kiri dan lima di kanan, karakter berjajar di tengah,
 * host (tidak ikut main) sebagai kotak kecil di kiri atas, tombol
 * Mulai di kanan bawah (berubah jadi bar progres saat level dibangun).
 *
 * <p>Owner mendapat kontrol tambahan: mode tim di kanan atas, dan di setiap
 * kartu tombol pindah tim (pil tim), jadikan host/pemain, dan keluarkan.
 * Slot kosong jadi tombol "Tambah" yang membuka daftar pemain di lobby.
 */
public class RoomScreen extends LobbyPageScreen {

    private static final int ROWS = RoomInfo.MAX_PLAYERS / 2;
    private static final int GAP = 3;
    private static final int CARDS_Y = 52;
    private static final int CHIP_H = 18;

    public RoomScreen() {
        super("Room");
    }

    @Override
    public LobbyClient.Page page() {
        return LobbyClient.Page.ROOM;
    }

    private int cardW() {
        return Mth.clamp(Math.round(width * 0.27F), 108, 140);
    }

    /** Tinggi kartu: lima baris harus muat di atas tombol-tombol bawah. */
    private int cardH() {
        int bottom = height - 56;
        return Mth.clamp((bottom - CARDS_Y) / ROWS - GAP, 21, 27);
    }

    /** Baris kedua kartu (pil tim, tag, tombol owner). */
    private int line2(int y) {
        return y + cardH() - 13;
    }

    private int cardX(int slot) {
        return slot < ROWS ? 12 : width - 12 - cardW();
    }

    private int cardY(int slot) {
        return CARDS_Y + (slot % ROWS) * (cardH() + GAP);
    }

    /** Kotak host di kiri atas, di atas kolom kartu kiri. */
    private int chipY(int index) {
        return 8 + index * (CHIP_H + 3);
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
        boolean owner = LobbyClient.isOwner();
        boolean waiting = room.state() == RoomState.WAITING;
        boolean hasPlayers = !room.players().isEmpty();
        GameButton main = switch (room.state()) {
            case WAITING -> !owner
                    ? new GameButton(bigX, bigY, bigW, bigH, GameButton.Style.GRAY, null, "Tunggu owner", Ui.Size.BIG, () -> {})
                    : hasPlayers
                    ? new GameButton(bigX, bigY, bigW, bigH, GameButton.Style.YELLOW, null, "Mulai", Ui.Size.HUGE,
                    () -> LobbyClient.send(LobbyActionC2S.Action.START)).hero()
                    : new GameButton(bigX, bigY, bigW, bigH, GameButton.Style.GRAY, null, "Butuh pemain", Ui.Size.BIG, () -> {});
            case BUILDING -> new GameButton(bigX, bigY, bigW, bigH, GameButton.Style.YELLOW, null,
                    "Menyiapkan " + room.progress() + "%", Ui.Size.BIG, () -> {}).hero().progress(() -> {
                RoomInfo current = LobbyClient.room();
                return current == null ? 0 : current.progress();
            });
            case PLAYING -> new GameButton(bigX, bigY, bigW, bigH, GameButton.Style.GRAY, null, "Masuk…", Ui.Size.BIG, () -> {});
        };
        main.active = owner && waiting && hasPlayers;
        addRenderableWidget(main);

        if (!owner || !waiting) return;
        // Mode tim: tekan untuk ganti solo -> duo -> trio -> squad -> 5 vs 5 -> solo.
        int next = room.teamSize() % RoomInfo.MAX_TEAM_SIZE + 1;
        addRenderableWidget(new GameButton(width - 12 - cardW(), 12, cardW(), 26, GameButton.Style.BLUE, Ui.Icon.PEOPLE,
                "Tim: " + RoomInfo.teamMode(room.teamSize()), Ui.Size.NORMAL,
                () -> LobbyClient.send(LobbyActionC2S.Action.SET_TEAM_SIZE, next)));

        for (int slot = room.players().size(); slot < RoomInfo.MAX_PLAYERS; slot++) {
            addRenderableWidget(new EmptySlotButton(cardX(slot), cardY(slot), cardW(), cardH(),
                    () -> LobbyClient.openPage(LobbyClient.Page.ADD_PLAYER)));
        }

        UUID self = minecraft == null || minecraft.player == null ? null : minecraft.player.getUUID();
        boolean playersFull = room.players().size() >= RoomInfo.MAX_PLAYERS;
        boolean hostsFull = room.hosts().size() >= RoomInfo.MAX_HOSTS;
        List<RoomInfo.Member> players = room.players();
        for (int i = 0; i < players.size(); i++) {
            RoomInfo.Member member = players.get(i);
            int x = cardX(i), y = cardY(i), w = cardW();
            int x2 = x + cardH() + 1;
            addRenderableWidget(new TagButton(x2, line2(y), teamTagW(), "T" + (member.team() + 1),
                    RoomInfo.teamColor(member.team()), "Pindah tim", () -> LobbyClient.moveTeam(member.id())));
            TagButton toHost = new TagButton(x + w - 31, line2(y), 28, "Host", 0xFF5B6487, "Jadikan host",
                    () -> LobbyClient.setRole(member.id(), RoomInfo.Role.HOST));
            toHost.active = !hostsFull;
            addRenderableWidget(toHost);
            if (!member.id().equals(self)) {
                addRenderableWidget(new KickButton(x + w - 15, y + 1, () -> LobbyClient.kick(member.id())));
            }
        }
        List<RoomInfo.Member> hosts = room.hosts();
        for (int i = 0; i < hosts.size(); i++) {
            RoomInfo.Member member = hosts.get(i);
            int x = 12, y = chipY(i), w = cardW();
            boolean me = member.id().equals(self);
            TagButton toPlayer = new TagButton(x + w - (me ? 31 : 45), y + 3, 28, "Main", 0xFF2FA35A, "Jadikan pemain",
                    () -> LobbyClient.setRole(member.id(), RoomInfo.Role.PLAYER));
            toPlayer.active = !playersFull;
            addRenderableWidget(toPlayer);
            if (!me) addRenderableWidget(new KickButton(x + w - 15, y + 3, () -> LobbyClient.kick(member.id())));
        }
    }

    private static int teamTagW() {
        return Ui.width(Ui.text("T10", Ui.Size.SMALL)) + 8;
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        RoomInfo room = LobbyClient.room();
        if (room == null) return;
        boolean owner = LobbyClient.isOwner();
        boolean controls = owner && room.state() == RoomState.WAITING;
        Ui.drawCentered(g, Ui.fit(Ui.text(room.name(), Ui.Size.HUGE), width - 2 * (12 + cardW() + 8)), width / 2, 12, Ui.WHITE, true);
        String id = "ID " + room.id();
        String count = room.players().size() + "/" + RoomInfo.MAX_PLAYERS + " pemain";
        String mode = controls ? null : RoomInfo.teamMode(room.teamSize());
        int w1 = Ui.width(Ui.text(id, Ui.Size.SMALL)) + 12;
        int w2 = Ui.width(Ui.text(count, Ui.Size.SMALL)) + 12;
        int w3 = mode == null ? 0 : Ui.width(Ui.text(mode, Ui.Size.SMALL)) + 16;
        int px = width / 2 - (w1 + 4 + w2 + w3) / 2;
        px += pill(g, id, px, 34, 0xCC0B1022, Ui.DIM) + 4;
        px += pill(g, count, px, 34, 0xCC0B1022, Ui.WHITE) + 4;
        if (mode != null) pill(g, mode, px, 34, 0xE62B6EE8, Ui.WHITE);

        List<RoomInfo.Member> players = room.players();
        for (int slot = 0; slot < RoomInfo.MAX_PLAYERS; slot++) {
            card(g, room, slot < players.size() ? players.get(slot) : null, slot, controls);
        }

        List<RoomInfo.Member> hosts = room.hosts();
        if (!hosts.isEmpty()) {
            for (int i = 0; i < hosts.size(); i++) {
                chip(g, room, hosts.get(i), 12, chipY(i), controls);
            }
        }
    }

    private void card(GuiGraphics g, RoomInfo room, RoomInfo.Member member, int slot, boolean controls) {
        int x = cardX(slot), y = cardY(slot), w = cardW(), h = cardH();
        if (member == null) {
            Ui.nine(g, Ui.Tex.CARD_EMPTY, x, y, w, h);
            // Untuk owner, slot kosong adalah tombol tambah pemain.
            Component empty = Ui.text(controls ? "Tambah" : "Kosong", Ui.Size.SMALL);
            int total = 12 + 4 + Ui.width(empty);
            int left = x + (w - total) / 2;
            int iy = y + (h - 12) / 2;
            g.setColor(1.0F, 1.0F, 1.0F, 0.45F);
            Ui.icon(g, Ui.Icon.PLUS, left, iy, 12);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            Ui.drawMiddle(g, empty, left + 16, iy, 12, 0xFF7C84A0, false);
            return;
        }
        boolean owner = room.isOwner(member.id());
        Ui.nine(g, owner ? Ui.Tex.CARD_GOLD : Ui.Tex.CARD, x, y, w, h);
        Ui.avatar(g, member.id(), x + 3, y + 3, h - 6, owner);
        int x2 = x + h + 1;
        Component name = Ui.fit(Ui.text(member.name(), Ui.Size.NORMAL), x + w - x2 - (controls ? 18 : 4));
        Ui.drawMiddle(g, name, x2, y + 1, 11, Ui.WHITE, true);
        int ly = line2(y);
        if (!controls) {
            // Pil tim (untuk owner, pil ini tombol pindah tim).
            Ui.nine(g, Ui.Tex.PILL, x2, ly, teamTagW(), 12, RoomInfo.teamColor(member.team()));
            Component team = Ui.text("T" + (member.team() + 1), Ui.Size.SMALL);
            Ui.drawMiddle(g, team, x2 + (teamTagW() - Ui.width(team)) / 2.0F, ly, 12, Ui.WHITE, true);
        }
        int tx = x2 + teamTagW() + 4;
        int space = x + w - tx - (controls ? 34 : 4);
        if (owner) {
            Ui.icon(g, Ui.Icon.CROWN, tx, ly + 1, 10);
            Component label = Ui.text("Owner", Ui.Size.SMALL);
            // Kalau sempit (tombol owner di kartu sendiri), mahkotanya saja.
            if (Ui.width(label) <= space - 12) Ui.drawMiddle(g, label, tx + 12, ly + 1, 10, Ui.GOLD, false);
        } else {
            boolean self = minecraft != null && minecraft.player != null && member.id().equals(minecraft.player.getUUID());
            String tag = self ? "Kamu" : LobbyPose.byId(member.pose()).displayName();
            Ui.drawMiddle(g, Ui.fit(Ui.text(tag, Ui.Size.SMALL), space), tx, ly + 1, 10, Ui.DIM, false);
        }
    }

    private void chip(GuiGraphics g, RoomInfo room, RoomInfo.Member member, int x, int y, boolean controls) {
        int w = cardW();
        boolean owner = room.isOwner(member.id());
        Ui.nine(g, owner ? Ui.Tex.CARD_GOLD : Ui.Tex.CARD, x, y, w, CHIP_H);
        Ui.avatar(g, member.id(), x + 2, y + 2, 14, owner);
        boolean self = minecraft != null && minecraft.player != null && member.id().equals(minecraft.player.getUUID());
        // Tanpa kontrol owner, sisi kanan chip diberi label "HOST" supaya jelas ini kotak host.
        Component tag = Ui.text("HOST", Ui.Size.SMALL);
        int right = controls ? (self ? 34 : 48) : Ui.width(tag) + 14;
        int nx = x + 19;
        if (owner) {
            Ui.icon(g, Ui.Icon.CROWN, nx, y + 4, 10);
            nx += 12;
        }
        Ui.drawMiddle(g, Ui.fit(Ui.text(member.name(), Ui.Size.SMALL), x + w - right - nx), nx, y + 3, 12, Ui.WHITE, true);
        if (!controls) {
            int tw = Ui.width(tag) + 8;
            Ui.nine(g, Ui.Tex.PILL, x + w - tw - 3, y + 3, tw, 12, 0xFF7A4FD0);
            Ui.drawMiddle(g, tag, x + w - tw + 1, y + 3, 12, Ui.WHITE, false);
        }
    }

    /** Pil kecil yang bisa ditekan (tim, jadikan host/pemain); khusus owner. */
    private static final class TagButton extends AbstractButton {
        private final String label;
        private final int color;
        private final Runnable action;

        TagButton(int x, int y, int w, String label, int color, String narration, Runnable action) {
            super(x, y, w, 12, Component.literal(narration));
            this.label = label;
            this.color = color;
            this.action = action;
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean hot = active && isHoveredOrFocused();
            int y = getY() - (hot ? 1 : 0);
            Ui.nine(g, Ui.Tex.PILL, getX(), y, getWidth(), 12, active ? color : 0xFF3A3F55);
            if (hot) g.fill(getX() + 2, y + 1, getX() + getWidth() - 2, y + 11, 0x33FFFFFF);
            Component text = Ui.text(label, Ui.Size.SMALL);
            Ui.drawMiddle(g, text, getX() + (getWidth() - Ui.width(text)) / 2.0F, y, 12, active ? Ui.WHITE : 0xFF8A90A8, active);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    /** Untuk owner: slot kosong yang bisa ditekan (membuka daftar pemain di lobby); isinya digambar {@link #card}. */
    private static final class EmptySlotButton extends AbstractButton {
        private final Runnable action;

        EmptySlotButton(int x, int y, int w, int h, Runnable action) {
            super(x, y, w, h, Component.literal("Tambah pemain"));
            this.action = action;
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            if (isHoveredOrFocused()) {
                g.fill(getX() + 2, getY() + 2, getX() + getWidth() - 2, getY() + getHeight() - 2, 0x22FFFFFF);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    /** Untuk owner: keluarkan anggota dari room (ikon silang kecil di kartunya). */
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
