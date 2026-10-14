package com.backrooms.world.gen;

import com.backrooms.block.ModBlocks;
import com.backrooms.world.maze.Level0Maze;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Optional;

import static com.backrooms.world.gen.LevelGenerator.is;
import static com.backrooms.world.gen.LevelGenerator.place;
import static com.backrooms.world.gen.LevelGenerator.settings;
import static com.backrooms.world.gen.LevelGenerator.template;

/**
 * Port dari {@code com.sp.world.generation.chunk_generator.Level0ChunkGenerator}.
 *
 * <ul>
 *   <li>Chunk 0,0: aula spawn -- megaroom1 empat kali (2x2) di Y=18 plus
 *       plafon 16x16 di Y=25.</li>
 *   <li>Setiap 5 chunk: satu region 80x80. Separuh peluang jadi maze biasa;
 *       separuh lagi megaroom acak (1-2 mengisi seluruh region, 3-6 di tengah
 *       dengan maze di sekelilingnya), kecuali ada megaroom lain dalam jarak
 *       80 blok. Region jauh dari spawn bisa mendapat tangga keluar.</li>
 *   <li>Setiap chunk: 2x2 genteng plafon 8x8 di Y=25, kecuali di atas megaroom
 *       tinggi (ditandai {@code cyan_wool} di Y=18).</li>
 * </ul>
 *
 * <p>Plafon dijalankan sebagai pass kedua setelah semua ruangan berdiri. Di
 * world generation asli urutannya bergantung urutan chunk dibuat; dengan
 * pass terpisah hasilnya selalu seperti yang dimaksud -- plafon hanya di atas
 * ruangan biasa.
 */
public class Level0Generator implements LevelGenerator {

    @Override
    public int passes() {
        return 2;
    }

    @Override
    public void generate(BuildLevel level, ChunkPos chunk, int pass) {
        if (pass == 0) {
            rooms(level, chunk);
        } else {
            roofs(level, chunk);
        }
    }

    private void rooms(BuildLevel level, ChunkPos chunk) {
        int x = chunk.getMinBlockX();
        int z = chunk.getMinBlockZ();
        RandomSource random = RandomSource.create();
        int roll = random.nextIntBetweenInclusive(1, 2);
        StructurePlaceSettings settings = settings(Rotation.NONE);

        if (chunk.x == 0 && chunk.z == 0) {
            for (int i = 0; i < 16; ++i) {
                for (int j = 0; j < 16; ++j) {
                    level.setBlock(new BlockPos(i, 25, j), (i == 0 && j == 0
                            ? ModBlocks.GHOST_CEILING_TILE.get() : ModBlocks.CEILING_TILE.get()).defaultBlockState(), LevelGenerator.FILL_FLAGS);
                }
            }
            level.setBlock(new BlockPos(0, 25, 0), ModBlocks.GHOST_CEILING_TILE.get().defaultBlockState(), LevelGenerator.FILL_FLAGS);
            Optional<StructureTemplate> hall = template(level, "level0/megaroom1");
            placeQuad(level, hall, x, z, settings, random);
            return;
        }
        if (!LevelGenerator.onMazeGrid(chunk)) return;

        if (LevelGenerator.farFromSpawn(chunk) && roll != 1) {
            BlockPos pos = new BlockPos(x + 15, 4, z + 15);
            place(level, template(level, "level0/stairwell_0"), pos, settings, random, 2);
        }
        if (roll == 1 && !isNearMegaRooms(x, z, level)) {
            int mega = random.nextIntBetweenInclusive(1, 6);
            Optional<StructureTemplate> room = template(level, "level0/megaroom" + mega);
            if (room.isEmpty()) return;
            if (mega == 1 || mega == 2) {
                placeQuad(level, room, x, z, settings, random);
                return;
            }
            BlockPos pos = new BlockPos(x - 16, 18, z - 16);
            place(level, room, pos, settings, random, 2);
        }
        new Level0Maze(16, 5, 5, x, z, "level0").setup(level);
    }

    /** Megaroom 48x48 ditempel 2x2 dengan tumpang-tindih 16 blok, mengisi 80x80. */
    private static void placeQuad(BuildLevel level, Optional<StructureTemplate> room, int x, int z,
                                  StructurePlaceSettings settings, RandomSource random) {
        place(level, room, new BlockPos(x - 32, 18, z - 32), settings, random, 2);
        place(level, room, new BlockPos(x, 18, z - 32), settings, random, 2);
        place(level, room, new BlockPos(x - 32, 18, z), settings, random, 2);
        place(level, room, new BlockPos(x, 18, z), settings, random, 2);
    }

    private void roofs(BuildLevel level, ChunkPos chunk) {
        int x = chunk.getMinBlockX();
        int z = chunk.getMinBlockZ();
        RandomSource random = RandomSource.create();
        for (int i = 0; i < 2; ++i) {
            for (int j = 0; j < 2; ++j) {
                Optional<StructureTemplate> roof = template(level, getRoof());
                StructurePlaceSettings settings = randRotation();
                if (roof.isEmpty()) continue;
                int rx = x + 8 * i;
                int rz = z + 8 * j;
                if (is(level, rx, 18, rz, Blocks.CYAN_WOOL) || !is(level, rx, 25, rz, Blocks.AIR)) continue;
                int px = settings.getRotation() == Rotation.CLOCKWISE_90 ? rx + 7 : rx;
                place(level, roof, new BlockPos(px, 25, rz), settings, random, LevelGenerator.FILL_FLAGS);
            }
        }
    }

    /** Ada {@code red_wool} (penanda megaroom) di Y=19 dalam grid ±80 blok? */
    private static boolean isNearMegaRooms(int x, int z, BuildLevel level) {
        for (int dx = -80; dx <= 80; dx += 80) {
            for (int dz = -80; dz <= 80; dz += 80) {
                if (is(level, x + dx, 19, z + dz, Blocks.RED_WOOL)) return true;
            }
        }
        return false;
    }

    private static String getRoof() {
        return RandomSource.create().nextIntBetweenInclusive(1, 5) == 1 ? "level0/roof2" : "level0/roof1";
    }

    private static StructurePlaceSettings randRotation() {
        return settings(RandomSource.create().nextIntBetweenInclusive(1, 2) == 1 ? Rotation.NONE : Rotation.CLOCKWISE_90);
    }
}
