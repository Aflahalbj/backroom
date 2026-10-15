package com.backrooms.world.events;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Port dari {@code com.sp.world.events.EventSounds}: suara event dikirim
 * langsung ke tiap pemain di level itu (di sini: di salinan level itu).
 */
public class EventSounds {
    public static void playSound(WorldEvents world, SoundEvent soundEvent) {
        for (ServerPlayer player : world.players()) {
            BlockPos playerPos = player.blockPosition();
            player.connection.send(new ClientboundSoundPacket(Holder.direct(soundEvent), SoundSource.AMBIENT,
                    playerPos.getX(), playerPos.getY(), playerPos.getZ(), 100.0F, 1.0F, player.getRandom().nextLong()));
        }
    }

    public static void playSoundWithRandLocation(WorldEvents world, SoundEvent soundEvent, int yLevel, int range) {
        for (ServerPlayer player : world.players()) {
            for (int i = 0; i < 5; ++i) {
                RandomSource random = RandomSource.create();
                BlockPos playerPos = player.blockPosition();
                int randXOffset = random.nextIntBetweenInclusive(-range, range);
                int randZOffset = random.nextIntBetweenInclusive(-range, range);
                player.connection.send(new ClientboundSoundPacket(Holder.direct(soundEvent), SoundSource.AMBIENT,
                        playerPos.getX() + randXOffset, yLevel, playerPos.getZ() + randZOffset, 100.0F, 1.0F, player.getRandom().nextLong()));
            }
        }
    }

    public static void playDistantSound(WorldEvents world, SoundEvent soundEvent) {
        RandomSource random = RandomSource.create();
        int randXOffset = random.nextIntBetweenInclusive(-300, 300);
        int randZOffset = random.nextIntBetweenInclusive(-300, 300);
        for (ServerPlayer player : world.players()) {
            BlockPos playerPos = player.blockPosition();
            player.connection.send(new ClientboundSoundPacket(Holder.direct(soundEvent), SoundSource.AMBIENT,
                    playerPos.getX() + randXOffset, playerPos.getY(), playerPos.getZ() + randZOffset, 1000.0F, 1.0F, player.getRandom().nextLong()));
        }
    }
}
