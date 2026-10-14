package com.backrooms.lobby;

import net.minecraft.network.FriendlyByteBuf;

import java.util.List;
import java.util.UUID;

/** Isi satu room seperti yang dilihat anggotanya (dikirim ke klien). */
public record RoomInfo(int id, String name, UUID host, RoomState state, int progress, List<Member> members) {

    public static final int MAX_MEMBERS = 8;

    public record Member(UUID id, String name, int pose, int weapon) {}

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(id);
        buf.writeUtf(name);
        buf.writeUUID(host);
        buf.writeEnum(state);
        buf.writeVarInt(progress);
        buf.writeCollection(members, (b, m) -> {
            b.writeUUID(m.id);
            b.writeUtf(m.name);
            b.writeVarInt(m.pose);
            b.writeVarInt(m.weapon);
        });
    }

    public static RoomInfo read(FriendlyByteBuf buf) {
        return new RoomInfo(buf.readVarInt(), buf.readUtf(), buf.readUUID(), buf.readEnum(RoomState.class), buf.readVarInt(),
                buf.readList(b -> new Member(b.readUUID(), b.readUtf(), b.readVarInt(), b.readVarInt())));
    }

    /** Satu baris di daftar "Gabung Room". */
    public record Summary(int id, String name, String hostName, int members, RoomState state) {

        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(id);
            buf.writeUtf(name);
            buf.writeUtf(hostName);
            buf.writeVarInt(members);
            buf.writeEnum(state);
        }

        public static Summary read(FriendlyByteBuf buf) {
            return new Summary(buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readEnum(RoomState.class));
        }
    }
}
