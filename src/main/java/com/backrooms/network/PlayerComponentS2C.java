package com.backrooms.network;

import com.backrooms.component.PlayerComponent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server &rarr; klien: isi komponen {@code spb-revamped:player} seorang pemain (sync CCA). */
public record PlayerComponentS2C(int entityId, CompoundTag tag) {

    public static PlayerComponentS2C of(PlayerComponent component) {
        CompoundTag tag = new CompoundTag();
        component.writeToNbt(tag);
        return new PlayerComponentS2C(component.player.getId(), tag);
    }

    public static void encode(PlayerComponentS2C msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeNbt(msg.tag);
    }

    public static PlayerComponentS2C decode(FriendlyByteBuf buf) {
        return new PlayerComponentS2C(buf.readVarInt(), buf.readNbt());
    }

    public static void handle(PlayerComponentS2C msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.backrooms.client.skinwalker.SkinWalkerClient.onPlayerComponent(msg.entityId, msg.tag)));
        ctx.get().setPacketHandled(true);
    }
}
