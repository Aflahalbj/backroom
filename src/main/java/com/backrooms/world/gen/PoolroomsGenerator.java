package com.backrooms.world.gen;

import com.backrooms.world.maze.PoolroomsMaze;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.backrooms.world.gen.LevelGenerator.is;
import static com.backrooms.world.gen.LevelGenerator.place;
import static com.backrooms.world.gen.LevelGenerator.settings;
import static com.backrooms.world.gen.LevelGenerator.template;

/**
 * Port dari {@code com.sp.world.generation.chunk_generator.PoolroomsChunkGenerator}.
 *
 * <ul>
 *   <li>Chunk 0,0: menara masuk (entrance, 91 blok) dan maze "sky".</li>
 *   <li>Setiap 5 chunk: noise memilih versi sky (berlangit) atau dark; region
 *       diisi megaroom sebanyak muat (dicek lewat penanda {@code purple_wool}),
 *       lalu sisa selnya jadi maze berpintu. Region jauh dari spawn bisa
 *       mendapat pintu keluar.</li>
 * </ul>
 */
public class PoolroomsGenerator implements LevelGenerator {

    private static final List<String> MAIN_MEGA_ROOMS =
            List.of("16x16", "16x24", "16x32", "24x16", "24x24", "24x32", "32x16", "32x24", "32x32");

    private final RandomSource random = RandomSource.create();
    private final ImprovedNoise noiseSampler = new ImprovedNoise(random);

    @Override
    public void generate(BuildLevel level, ChunkPos chunk, int pass) {
        int x = chunk.getMinBlockX();
        int z = chunk.getMinBlockZ();
        StructurePlaceSettings settings = settings(Rotation.NONE);

        if (chunk.x == 0 && chunk.z == 0) {
            place(level, template(level, "poolrooms/entrance"), new BlockPos(0, 18, 0), settings, random, 2);
            new PoolroomsMaze(8, 10, 10, x, z, "poolrooms/sky").setup(level);
            return;
        }
        if (!LevelGenerator.onMazeGrid(chunk)) return;

        if (LevelGenerator.farFromSpawn(chunk) && random.nextIntBetweenInclusive(0, 4) == 0) {
            place(level, template(level, "poolrooms/poolrooms_exit"), new BlockPos(x - 24, 18, z - 24), settings, random, 2);
            place(level, template(level, "poolrooms/poolrooms_exit2"), new BlockPos(x - 32, 39, z + 17), settings, random, 2);
        }
        double noise = noiseSampler.noise(x * 0.002D, 0.0D, z * 0.002D);
        boolean sky = noise > 0.0D;
        generateMegaRooms(level, sky, x - 32, z - 32);
        new PoolroomsMaze(8, 10, 10, x, z, "poolrooms/" + (sky ? "sky" : "dark")).setup(level);
    }

    private void generateMegaRooms(BuildLevel level, boolean sky, int originX, int originY) {
        int size = 8;
        int rows = 10;
        int cols = 10;
        String directory = sky ? "poolrooms/sky" : "poolrooms/dark";
        RandomSource random = RandomSource.create();

        int w = random.nextIntBetweenInclusive(1, 6);
        int p = random.nextIntBetweenInclusive(1, 3);
        String first = switch (w) {
            case 1 -> "16x16";
            case 2 -> "16x24";
            case 3 -> "16x32";
            case 4 -> "24x24";
            case 5 -> "24x32";
            default -> "32x32";
        };
        String roomId = directory + "/megaroom_" + first + "_" + p;
        StructurePlaceSettings settings = settings(Rotation.NONE);
        Optional<StructureTemplate> room = template(level, roomId);
        int dirLength = directory.length();
        int roomWidth = Integer.parseInt(roomId.substring(dirLength + 10, dirLength + 12));
        int roomHeight = Integer.parseInt(roomId.substring(dirLength + 13, dirLength + 15));
        int randX = random.nextIntBetweenInclusive(1, cols - (1 + roomWidth / size));
        int randY = random.nextIntBetweenInclusive(1, rows - (1 + roomHeight / size));
        BlockPos firstPos = new BlockPos(randX + (size - 1) * randX + originX, 18, randY + (size - 1) * randY + originY);
        if (room.isPresent() && !is(level, firstPos.getX(), 18, firstPos.getZ(), Blocks.PURPLE_WOOL)) {
            level.place(room, firstPos, settings, random, 2);
        }

        // Lalu coba setiap ukuran sekali, di posisi bebas pertama yang muat.
        List<String> remaining = new ArrayList<>(MAIN_MEGA_ROOMS);
        while (!remaining.isEmpty()) {
            int index = random.nextIntBetweenInclusive(0, remaining.size() - 1);
            String current = remaining.get(index);
            int xx = Integer.parseInt(current.substring(0, 2));
            int yy = Integer.parseInt(current.substring(3, 5));
            p = random.nextIntBetweenInclusive(1, 3);
            boolean turned = yy < xx;
            String id = turned
                    ? directory + "/megaroom_" + yy + "x" + xx + "_" + p
                    : directory + "/megaroom_" + xx + "x" + yy + "_" + p;
            StructurePlaceSettings placement = settings(turned ? Rotation.COUNTERCLOCKWISE_90 : Rotation.NONE);

            boolean placed = false;
            for (int ay = 1; ay < rows - yy / size && !placed; ++ay) {
                for (int ax = 1; ax < cols - xx / size; ++ax) {
                    int sx = ax + (size - 1) * ax + originX;
                    int sz = ay + (size - 1) * ay + originY;
                    if (!isClear(level, sx, sz, xx, yy)) continue;
                    Optional<StructureTemplate> template = level.template(id);
                    if (template.isEmpty()) continue;
                    if (turned) {
                        level.place(template, new BlockPos(sx, 18, sz + (yy - 1)),
                                new BlockPos(sx, 19, sz + (xx - 1)), placement, random, 2);
                    } else {
                        level.place(template, new BlockPos(sx, 18, sz), placement, random, 2);
                    }
                    placed = true;
                    break;
                }
            }
            remaining.remove(index);
        }
    }

    /** Tidak ada {@code purple_wool} di Y=18 pada kotak ruangan plus 1 blok di sekelilingnya. */
    private static boolean isClear(BuildLevel level, int sx, int sz, int width, int height) {
        for (int ry = -1; ry <= height; ++ry) {
            for (int bx = -1; bx <= width; ++bx) {
                if (is(level, sx + bx, 18, sz + ry, Blocks.PURPLE_WOOL)) return false;
            }
        }
        return true;
    }
}
