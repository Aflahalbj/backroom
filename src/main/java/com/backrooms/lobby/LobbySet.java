package com.backrooms.lobby;

import com.backrooms.block.ModBlocks;
import com.backrooms.world.BackroomsLevel;
import com.backrooms.world.BuildArea;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Persiapan set latar di dimensi lobby setelah dibangun: chunk-nya selalu
 * dimuat (ganti latar langsung tampil, tanpa menunggu chunk dibaca dari
 * disk), dan set Poolrooms diberi biome Poolrooms (warna langit/kabut putih)
 * karena dimensi lobby memakai biome Backrooms biasa.
 */
final class LobbySet {

    private static final ResourceKey<Biome> POOLROOMS_BIOME =
            ResourceKey.create(Registries.BIOME, new ResourceLocation(ModBlocks.NAMESPACE, "poolrooms_biome"));

    private LobbySet() {}

    static void prepare(ServerLevel level, BuildArea area) {
        int minX = area.worldMinX() >> 4, maxX = area.worldMaxX() >> 4;
        int minZ = area.worldMinZ() >> 4, maxZ = area.worldMaxZ() >> 4;
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                level.setChunkForced(cx, cz, true);
            }
        }
        if (area.which() == BackroomsLevel.POOLROOMS) {
            fillBiome(level, POOLROOMS_BIOME, minX, maxX, minZ, maxZ);
        }
    }

    private static void fillBiome(ServerLevel level, ResourceKey<Biome> key, int minX, int maxX, int minZ, int maxZ) {
        Holder<Biome> biome = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(key);
        List<ChunkAccess> changed = new ArrayList<>();
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                LevelChunk chunk = level.getChunk(cx, cz);
                chunk.fillBiomesFromNoise((x, y, z, sampler) -> biome, level.getChunkSource().randomState().sampler());
                chunk.setUnsaved(true);
                changed.add(chunk);
            }
        }
        level.getChunkSource().chunkMap.resendBiomesForChunks(changed);
    }
}
