package com.backrooms.network;

import com.backrooms.lobby.LobbyStage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server &rarr; klien: pemain ini sedang di lobby atau tidak, latar, pose, dan senjata
 * pilihannya, dan panggung set lobby tempat dia berdiri ({@code null} selama
 * set-nya masih dibangun, dengan {@code percent} progresnya).
 */
public record LobbyStateS2C(boolean active, int background, int pose, int weapon, LobbyStage stage, int percent) {

    public static void encode(LobbyStateS2C msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.active);
        buf.writeVarInt(msg.background);
        buf.writeVarInt(msg.pose);
        buf.writeVarInt(msg.weapon);
        buf.writeBoolean(msg.stage != null);
        if (msg.stage != null) {
            buf.writeBlockPos(msg.stage.pos());
            buf.writeVarInt(msg.stage.facing().get2DDataValue());
            buf.writeVarInt(msg.stage.reach());
        }
        buf.writeVarInt(msg.percent);
    }

    public static LobbyStateS2C decode(FriendlyByteBuf buf) {
        boolean active = buf.readBoolean();
        int background = buf.readVarInt();
        int pose = buf.readVarInt();
        int weapon = buf.readVarInt();
        LobbyStage stage = null;
        if (buf.readBoolean()) {
            BlockPos pos = buf.readBlockPos();
            stage = new LobbyStage(pos, Direction.from2DDataValue(buf.readVarInt()), buf.readVarInt());
        }
        return new LobbyStateS2C(active, background, pose, weapon, stage, buf.readVarInt());
    }

    public static void handle(LobbyStateS2C msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.backrooms.client.lobby.LobbyClient.onState(msg)));
        ctx.get().setPacketHandled(true);
    }
}
