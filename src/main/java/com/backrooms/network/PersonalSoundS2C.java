package com.backrooms.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server &rarr; klien: {@code SPBRevamped.sendPersonalPlaySoundPacket} --
 * suara spb-revamped yang hanya didengar pemain ini, tanpa posisi.
 */
public record PersonalSoundS2C(String sound, float volume, float pitch) {

    public static void encode(PersonalSoundS2C msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.sound);
        buf.writeFloat(msg.volume);
        buf.writeFloat(msg.pitch);
    }

    public static PersonalSoundS2C decode(FriendlyByteBuf buf) {
        return new PersonalSoundS2C(buf.readUtf(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(PersonalSoundS2C msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.backrooms.client.skinwalker.SkinWalkerClient.playPersonalSound(msg.sound, msg.volume, msg.pitch)));
        ctx.get().setPacketHandled(true);
    }
}
