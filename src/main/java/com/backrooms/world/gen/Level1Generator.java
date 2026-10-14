package com.backrooms.world.gen;

import com.backrooms.world.maze.Level1Maze;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;

import java.util.Optional;

import static com.backrooms.world.gen.LevelGenerator.FILL_FLAGS;
import static com.backrooms.world.gen.LevelGenerator.is;
import static com.backrooms.world.gen.LevelGenerator.place;
import static com.backrooms.world.gen.LevelGenerator.settings;
import static com.backrooms.world.gen.LevelGenerator.template;

/**
 * Port dari {@code com.sp.world.generation.chunk_generator.Level1ChunkGenerator}.
 *
 * <ul>
 *   <li>Chunk 0,0: tangga masuk (stairwell_1) dan maze pertama.</li>
 *   <li>Setiap 5 chunk: noise Perlin memilih antara aula besar (megaroom1
 *       dua kali + pola lampu acak light1..6) atau maze dengan satu ruangan
 *       acak (pillars/storage). Region jauh dari spawn bisa mendapat tangga
 *       ke level 2.</li>
 * </ul>
 */
public class Level1Generator implements LevelGenerator {

    private final RandomSource random = RandomSource.create();
    private final ImprovedNoise noiseSampler = new ImprovedNoise(random);

    @Override
    public void generate(BuildLevel level, ChunkPos chunk, int pass) {
        int x = chunk.getMinBlockX();
        int z = chunk.getMinBlockZ();
        StructurePlaceSettings settings = settings(Rotation.NONE);

        if (chunk.x == 0 && chunk.z == 0) {
            BlockPos pos = new BlockPos(-1, 19, -1);
            place(level, template(level, "level1/stairwell_1"), pos, settings, random, 2);
            new Level1Maze(8, 10, 10, x, z, "level1").setup(level, false);
            return;
        }
        if (!LevelGenerator.onMazeGrid(chunk)) return;

        double noise = noiseSampler.noise(x * 0.002D, 0.0D, z * 0.002D);
        if (LevelGenerator.farFromSpawn(chunk) && noise <= 0.0D && random.nextBoolean()) {
            BlockPos pos = new BlockPos(x + 16, 11, z + 16);
            place(level, template(level, "level1/stairwell2_1"), pos, settings, random, 2);
        }

        if (noise > 0.0D) {
            Optional<StructureTemplate> megaRoom = template(level, "level1/megaroom1");
            if (megaRoom.isPresent()) {
                BlockPos a = new BlockPos(x - 32, 19, z - 32);
                BlockPos b = new BlockPos(x, 19, z - 32);
                place(level, megaRoom, a, settings, random, 2);
                place(level, megaRoom, b, settings, random, 2);
                Optional<StructureTemplate> lights = template(level, "level1/light" + random.nextIntBetweenInclusive(1, 6));
                place(level, lights, a, settings, random, FILL_FLAGS);
                place(level, lights, b, settings, random, FILL_FLAGS);
                return;
            }
        }
        if (!is(level, x, 19, z, Blocks.RED_WOOL)) {
            new Level1Maze(8, 10, 10, x, z, "level1").setup(level, true);
        }
    }
}
