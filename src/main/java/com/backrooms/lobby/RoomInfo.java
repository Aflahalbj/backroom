package com.backrooms.lobby;

import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;
import java.util.UUID;

/**
 * Isi satu room seperti yang dilihat anggotanya (dikirim ke klien).
 *
 * <p>{@code owner} adalah pembuat room (OP): dia yang menekan Mulai, mengatur
 * peran anggota (pemain atau host), dan mode tim. Host tidak ikut main dan
 * tidak dihitung ke batas {@link #MAX_PLAYERS}.
 */
public record RoomInfo(int id, String name, UUID owner, RoomState state, int progress, int teamSize, List<Member> members) {

    /** Batas pemain (host tidak dihitung). */
    public static final int MAX_PLAYERS = 10;
    /** Batas host dalam satu room (kotak host di kiri atas muat dua). */
    public static final int MAX_HOSTS = 2;
    public static final int MAX_TEAM_SIZE = 5;

    /** Warna tim ke-n (tim 1 = indeks 0); cukup untuk mode solo 10 pemain. */
    private static final ChatFormatting[] TEAM_COLORS = {
            ChatFormatting.RED, ChatFormatting.BLUE, ChatFormatting.GREEN, ChatFormatting.YELLOW, ChatFormatting.AQUA,
            ChatFormatting.LIGHT_PURPLE, ChatFormatting.GOLD, ChatFormatting.DARK_AQUA, ChatFormatting.DARK_GREEN,
            ChatFormatting.WHITE
    };

    public enum Role {
        /** Ikut main, punya tim. */
        PLAYER,
        /** Tidak ikut main: masuk sebagai penonton (spectator) saat room mulai; di lobby tetap tampil. */
        HOST
    }

    /** {@code team} = indeks tim (0 = Tim 1), -1 untuk host. */
    public record Member(UUID id, String name, Role role, int team, int pose, int weapon) {
        public boolean isHost() {
            return role == Role.HOST;
        }
    }

    public boolean isOwner(UUID id) {
        return owner.equals(id);
    }

    public List<Member> players() {
        return members.stream().filter(m -> !m.isHost()).toList();
    }

    public List<Member> hosts() {
        return members.stream().filter(Member::isHost).toList();
    }

    public Member member(UUID id) {
        for (Member member : members) {
            if (member.id.equals(id)) return member;
        }
        return null;
    }

    /** Banyaknya tim yang muat untuk ukuran tim ini (10 pemain: duo = 5 tim, trio = 4 tim, ...). */
    public static int teamCount(int teamSize) {
        return (MAX_PLAYERS + teamSize - 1) / teamSize;
    }

    public static String teamMode(int teamSize) {
        return switch (teamSize) {
            case 1 -> "Solo";
            case 2 -> "Duo";
            case 3 -> "Trio";
            case 4 -> "Squad";
            default -> teamSize + " vs " + teamSize;
        };
    }

    public static ChatFormatting teamFormat(int team) {
        return TEAM_COLORS[Math.floorMod(team, TEAM_COLORS.length)];
    }

    /** Warna tim dalam ARGB untuk layar. */
    public static int teamColor(int team) {
        Integer rgb = teamFormat(team).getColor();
        return 0xFF000000 | (rgb == null ? 0xFFFFFF : rgb);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(id);
        buf.writeUtf(name);
        buf.writeUUID(owner);
        buf.writeEnum(state);
        buf.writeVarInt(progress);
        buf.writeVarInt(teamSize);
        buf.writeCollection(members, (b, m) -> {
            b.writeUUID(m.id);
            b.writeUtf(m.name);
            b.writeEnum(m.role);
            b.writeVarInt(m.team + 1);
            b.writeVarInt(m.pose);
            b.writeVarInt(m.weapon);
        });
    }

    public static RoomInfo read(FriendlyByteBuf buf) {
        return new RoomInfo(buf.readVarInt(), buf.readUtf(), buf.readUUID(), buf.readEnum(RoomState.class), buf.readVarInt(),
                buf.readVarInt(), buf.readList(b -> new Member(b.readUUID(), b.readUtf(), b.readEnum(Role.class),
                b.readVarInt() - 1, b.readVarInt(), b.readVarInt())));
    }

    /** Pemain di lobby yang belum ikut room mana pun: bisa ditambahkan owner langsung ke room-nya. */
    public record Candidate(UUID id, String name) {

        public void write(FriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeUtf(name);
        }

        public static Candidate read(FriendlyByteBuf buf) {
            return new Candidate(buf.readUUID(), buf.readUtf());
        }
    }

    /** Satu baris di daftar "Gabung Room"; {@code players} tanpa host. */
    public record Summary(int id, String name, String ownerName, int players, int teamSize, RoomState state) {

        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(id);
            buf.writeUtf(name);
            buf.writeUtf(ownerName);
            buf.writeVarInt(players);
            buf.writeVarInt(teamSize);
            buf.writeEnum(state);
        }

        public static Summary read(FriendlyByteBuf buf) {
            return new Summary(buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(),
                    buf.readEnum(RoomState.class));
        }
    }
}
