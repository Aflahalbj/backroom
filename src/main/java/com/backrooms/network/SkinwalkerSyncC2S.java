package com.backrooms.network;

import com.backrooms.component.PlayerComponent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Klien &rarr; server, gabungan tiga paket C2S asli yang dipakai Skinwalker:
 * <ul>
 *   <li>{@link Kind#BEING_CAPTURED}: {@code SyncServerComponent "beingCaptured"} (jumpscare selesai);</li>
 *   <li>{@link Kind#TARGET_ENTITY}: {@code TargetEntitySync} (entity yang sedang dilihat, -1 = tidak ada);</li>
 *   <li>{@link Kind#SEE_SKINWALKER}: {@code SeeActiveSkinwalkerSync} (korban terlihat di layar).</li>
 * </ul>
 */
public record SkinwalkerSyncC2S(Kind kind, int value) {

    public enum Kind {
        BEING_CAPTURED,
        TARGET_ENTITY,
        SEE_SKINWALKER
    }

    public static void encode(SkinwalkerSyncC2S msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.kind);
        buf.writeInt(msg.value);
    }

    public static SkinwalkerSyncC2S decode(FriendlyByteBuf buf) {
        return new SkinwalkerSyncC2S(buf.readEnum(Kind.class), buf.readInt());
    }

    public static void handle(SkinwalkerSyncC2S msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            PlayerComponent playerComponent = PlayerComponent.get(player);
            switch (msg.kind) {
                case BEING_CAPTURED -> {
                    playerComponent.setBeingCaptured(msg.value != 0);
                    playerComponent.sync();
                }
                case TARGET_ENTITY -> {
                    if (msg.value == -1) {
                        playerComponent.setTargetEntity(null);
                    } else {
                        playerComponent.setTargetEntity(player.level().getEntity(msg.value));
                    }
                }
                case SEE_SKINWALKER -> playerComponent.setCanSeeActiveSkinWalkerTarget(msg.value != 0);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
