package com.backrooms.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server &rarr; klien: mulai cutscene.
 * <ul>
 *   <li>{@link Type#DROP}: cutscene perkenalan: lantai lobby bolong, anggota
 *       {@code members} (urutan perkenalan) jatuh dan diperkenalkan satu per
 *       satu; server memindahkan pemain ke Backrooms room-nya sesudahnya
 *       (lihat {@code DropTiming}).</li>
 *   <li>{@link Type#INTRO}: intro asli Level 0 (jatuh dari langit + judul),
 *       digeser ke slot room sebesar {@code offsetX/offsetZ}.</li>
 * </ul>
 */
public record CutsceneS2C(Type type, int offsetX, int offsetZ, List<UUID> members) {

    public enum Type {
        DROP,
        INTRO
    }

    public CutsceneS2C(Type type, int offsetX, int offsetZ) {
        this(type, offsetX, offsetZ, List.of());
    }

    public static void encode(CutsceneS2C msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.type);
        buf.writeInt(msg.offsetX);
        buf.writeInt(msg.offsetZ);
        buf.writeCollection(msg.members, FriendlyByteBuf::writeUUID);
    }

    public static CutsceneS2C decode(FriendlyByteBuf buf) {
        return new CutsceneS2C(buf.readEnum(Type.class), buf.readInt(), buf.readInt(), buf.readList(FriendlyByteBuf::readUUID));
    }

    public static void handle(CutsceneS2C msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.backrooms.client.lobby.LobbyClient.onCutscene(msg)));
        ctx.get().setPacketHandled(true);
    }
}
