package com.backrooms.network;

import com.backrooms.entity.custom.SkinWalkerEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server &rarr; klien: isi komponen {@code spb-revamped:skw} sebuah Skinwalker (sync CCA). */
public record SkinWalkerComponentS2C(int entityId, CompoundTag tag) {

    public static SkinWalkerComponentS2C of(SkinWalkerEntity entity) {
        CompoundTag tag = new CompoundTag();
        entity.component.writeToNbt(tag);
        return new SkinWalkerComponentS2C(entity.getId(), tag);
    }

    public static void encode(SkinWalkerComponentS2C msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeNbt(msg.tag);
    }

    public static SkinWalkerComponentS2C decode(FriendlyByteBuf buf) {
        return new SkinWalkerComponentS2C(buf.readVarInt(), buf.readNbt());
    }

    public static void handle(SkinWalkerComponentS2C msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.backrooms.client.skinwalker.SkinWalkerClient.onSkinWalkerComponent(msg.entityId, msg.tag)));
        ctx.get().setPacketHandled(true);
    }
}
