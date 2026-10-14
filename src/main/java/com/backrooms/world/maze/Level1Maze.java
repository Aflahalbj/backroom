package com.backrooms.world.maze;

import net.minecraft.core.BlockPos;
import com.backrooms.world.gen.LevelGenerator;
import com.backrooms.world.gen.BuildLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * Port dari {@code com.sp.world.generation.maze_generator.Level1MazeGenerator}:
 * 10x10 sel berukuran 8, opsional didahului satu ruangan acak (pillars, atau
 * storage dengan peluang 1/9).
 */
public class Level1Maze extends MazeGenerator {

    private static final RoomCount ROOMS = new RoomCount(6, 24, 24, 12, 24);

    public Level1Maze(int size, int rows, int cols, int originX, int originY, String levelDirectory) {
        super(size, rows, cols, originX, originY, levelDirectory);
    }

    public void setup(BuildLevel level, boolean spawnRandomRooms) {
        if (spawnRandomRooms) {
            spawnRandomRooms(level, originX, originY);
        }
        for (int y = 0; y < rows; ++y) {
            for (int x = 0; x < cols; ++x) {
                boolean free = isAirOrNull(level.getBlockState(new BlockPos(cellX(x), 20, cellZ(y))))
                        && isAirOrNull(level.getBlockState(new BlockPos(cellX(x), 26, cellZ(y))));
                if (free) {
                    grid[x][y] = new MazeCell(cellZ(y), cellX(x), size, y, x);
                }
            }
        }
        if (grid[0][0] == null) return;
        carve(level);
        openEdges(1, cols - 2);
        for (MazeCell[] column : grid) {
            for (MazeCell cell : column) {
                if (cell != null) cell.drawWalls(level, levelDirectory, ROOMS);
            }
        }
    }

    @Override
    protected MazeCell checkNeighbors(BuildLevel level) {
        List<MazeCell> candidates = new ArrayList<>();
        for (MazeCell cell : neighbours()) {
            if (cell != null && !cell.isVisited()) candidates.add(cell);
        }
        int x = currentCell.getWorldXPos();
        int z = currentCell.getWorldYPos();
        if (is(level, x, 19, z + size, Blocks.LIME_WOOL)) {
            currentCell.removeNorthWall();
        }
        if (is(level, x, 19, z - size, Blocks.LIME_WOOL)) {
            currentCell.removeSouthWall();
        }
        if (is(level, x + size, 19, z, Blocks.LIME_WOOL)) {
            currentCell.removeWestWall();
        }
        if (is(level, x - size, 19, z, Blocks.LIME_WOOL) || is(level, x - size, 26, z, Blocks.YELLOW_WOOL)) {
            currentCell.removeEastWall();
        }
        return randomOf(candidates);
    }

    private void spawnRandomRooms(BuildLevel level, int x, int z) {
        RandomSource random = RandomSource.create();
        String roomId = random.nextIntBetweenInclusive(0, 8) == 0 ? "level1/storage" : "level1/pillars";
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setMirror(Mirror.NONE).setRotation(Rotation.NONE).setIgnoreEntities(true);
        int offsetX = x + random.nextIntBetweenInclusive(1, 6) * size;
        int offsetZ = z + random.nextIntBetweenInclusive(1, 6) * size;

        boolean place = true;
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 3; ++j) {
                int px = offsetX + size * i;
                int pz = offsetZ + size * j;
                if (!is(level, px, 20, pz, Blocks.AIR) || is(level, px, 26, pz, Blocks.YELLOW_WOOL)) {
                    place = false;
                }
            }
        }
        if (place) {
            level.place(level.template(roomId), new BlockPos(offsetX, 19, offsetZ), settings, random,
                    LevelGenerator.FILL_FLAGS);
        }
    }
}
