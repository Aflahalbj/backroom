package com.backrooms.world.events.level1;

import com.backrooms.entity.ModEntities;
import com.backrooms.entity.custom.SmilerEntity;
import com.backrooms.sound.ModSounds;
import com.backrooms.world.BackroomsLevel;
import com.backrooms.world.events.AbstractEvent;
import com.backrooms.world.events.LightState;
import com.backrooms.world.events.WorldEvents;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Port dari {@code com.sp.world.events.level1.Level1Blackout}: lampu padam 30 detik dan Smiler bermunculan. */
public class Level1Blackout extends AbstractEvent {
    private int smilerSpawnDelay = 80;

    @Override
    public void init(WorldEvents world) {
        if (world.which() == BackroomsLevel.LEVEL1) {
            if (world.getLightState() != LightState.BLACKOUT) {
                world.setLightState(LightState.BLACKOUT);
                playSound(world, ModSounds.event("lights_out"));
            }
        }
    }

    @Override
    public void ticks(int ticks, WorldEvents world) {
        if (world.which() == BackroomsLevel.LEVEL1) {
            RandomSource random = RandomSource.create();
            List<ServerPlayer> playerList = world.players();
            --this.smilerSpawnDelay;
            if (this.smilerSpawnDelay < 0) {
                for (ServerPlayer player : playerList) {
                    int rand = player.getRandom().nextIntBetweenInclusive(1, 10);
                    if (rand == 1) {
                        SmilerEntity smiler = ModEntities.SMILER.get().create(world.level());
                        if (smiler != null) {
                            BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
                            float randomAngle = random.nextFloat() * 360.0F;
                            Vec3 spawnPos = new Vec3(0.0D, 0.0D, 15.0D).yRot(randomAngle).add(player.position());
                            if (!world.level().getBlockState(mutable.set(spawnPos.x, spawnPos.y, spawnPos.z)).blocksMotion()) {
                                smiler.moveTo(Math.floor(spawnPos.x) + 0.5D, spawnPos.y, Math.floor(spawnPos.z) + 0.5D, 0.0F, 0.0F);
                                world.level().addFreshEntity(smiler);
                                this.smilerSpawnDelay = 80;
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void finish(WorldEvents world) {
        super.finish(world);
        if (world.which() == BackroomsLevel.LEVEL1) {
            world.setLightState(LightState.ON);
            playSound(world, ModSounds.event("lights_on"));
        }
    }

    @Override
    public int duration() {
        return 600;
    }
}
