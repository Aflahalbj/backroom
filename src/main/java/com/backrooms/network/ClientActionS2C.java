package com.backrooms.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server &rarr; klien: aksi dari {@code /backroom camera ...} dan
 * {@code /backroom flashlight ...}. Efek kamera adalah pengaturan klien, jadi
 * command-nya hanya meneruskan perintah ke klien pemain yang menjalankannya.
 */
public record ClientActionS2C(Action action, boolean value) {

    public enum Action {
        /** Efek kamera di dalam dan di luar backrooms sekaligus. */
        CAMERA_ALL,
        CAMERA_BACKROOMS,
        CAMERA_OUTSIDE,
        FLASHLIGHT,
        FLASHLIGHT_TOGGLE
    }

    public static void encode(ClientActionS2C msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.action);
        buf.writeBoolean(msg.value);
    }

    public static ClientActionS2C decode(FriendlyByteBuf buf) {
        return new ClientActionS2C(buf.readEnum(Action.class), buf.readBoolean());
    }

    public static void handle(ClientActionS2C msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.backrooms.client.ClientPackets.action(msg.action, msg.value)));
        ctx.get().setPacketHandled(true);
    }
}
