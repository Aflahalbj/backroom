package com.backrooms.world.maze;

import net.minecraft.core.BlockPos;
import com.backrooms.world.gen.BuildLevel;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * Port dari {@code com.sp.world.generation.maze_generator.Level0MazeGenerator}:
 * 5x5 sel berukuran 16. Sel hanya dibuat kalau Y=19 di pojoknya kosong (tidak
 * tertutup megaroom); dinding diruntuhkan ke arah penanda {@code lime_wool}.
 */
public class Level0Maze extends MazeGenerator {

    private static final RoomCount ROOMS = new RoomCount(8);

    public Level0Maze(int size, int rows, int cols, int originX, int originY, String levelDirectory) {
        super(size, rows, cols, originX, originY, levelDirectory);
    }

    public void setup(BuildLevel level) {
        for (int y = 0; y < rows; ++y) {
            for (int x = 0; x < cols; ++x) {
                if (isAirOrNull(level.getBlockState(new BlockPos(cellX(x), 19, cellZ(y))))) {
                    grid[x][y] = new MazeCell(cellZ(y), cellX(x), size, y, x);
                }
            }
        }
        // Aslinya crash (NPE) di sini dan tidak menggambar apa-apa.
        if (grid[0][0] == null) return;
        carve(level);
        openEdges(0, cols - 1);
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
        if (is(level, x, 19, z + size, Blocks.LIME_WOOL) || is(level, x, 4, z + size, Blocks.LIME_WOOL)) {
            currentCell.removeNorthWall();
        }
        if (is(level, x, 19, z - size, Blocks.LIME_WOOL)) {
            currentCell.removeSouthWall();
        }
        if (is(level, x + size, 19, z, Blocks.LIME_WOOL) || is(level, x + size, 4, z, Blocks.LIME_WOOL)) {
            currentCell.removeWestWall();
        }
        if (is(level, x - size, 19, z, Blocks.LIME_WOOL)) {
            currentCell.removeEastWall();
        }
        return randomOf(candidates);
    }
}
