package com.backrooms.world.maze;

import net.minecraft.core.BlockPos;
import com.backrooms.world.gen.BuildLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * Port dari {@code com.sp.world.generation.maze_generator.PoolroomsMazeGenerator}:
 * 10x10 sel berukuran 8 dengan dua kekhasan -- DFS cenderung lurus (arah
 * terakhir dihitung dua kali) dan sebagian sambungan diberi pintu.
 */
public class PoolroomsMaze extends MazeGenerator {

    private static final RoomCount ROOMS = new RoomCount(1);

    private int lastRoomDir;

    public PoolroomsMaze(int size, int rows, int cols, int originX, int originY, String levelDirectory) {
        super(size, rows, cols, originX, originY, levelDirectory);
    }

    public void setup(BuildLevel level) {
        for (int y = 0; y < rows; ++y) {
            for (int x = 0; x < cols; ++x) {
                if (isAirOrNull(level.getBlockState(new BlockPos(cellX(x), 18, cellZ(y))))) {
                    grid[x][y] = new MazeCell(cellZ(y), cellX(x), size, y, x);
                }
            }
        }
        // Aslinya crash (NPE) di sini dan tidak menggambar apa-apa.
        if (grid[0][0] == null) return;
        carve(level);
        openEdges(1, cols - 2);
        for (MazeCell[] column : grid) {
            for (MazeCell cell : column) {
                if (cell != null) cell.drawWallsWithDoors(level, levelDirectory, ROOMS);
            }
        }
    }

    @Override
    protected MazeCell checkNeighbors(BuildLevel level) {
        List<MazeCell> candidates = new ArrayList<>();
        List<Integer> directions = new ArrayList<>();
        MazeCell[] around = neighbours();
        for (int i = 0; i < around.length; i++) {
            MazeCell cell = around[i];
            int dir = i + 1; // 1=N, 2=W, 3=S, 4=E
            if (cell != null && !cell.isVisited()) {
                candidates.add(cell);
                directions.add(dir);
                if (lastRoomDir == dir) {
                    candidates.add(cell);
                    directions.add(dir);
                }
            }
        }

        int x = currentCell.getWorldXPos();
        int z = currentCell.getWorldYPos();
        if (is(level, x, 19, z + size, Blocks.YELLOW_WOOL) || is(level, x, 19, z + size, Blocks.PINK_WOOL)) {
            currentCell.removeNorthWall();
        }
        if (is(level, x, 19, z - size, Blocks.RED_WOOL) || is(level, x, 19, z - size, Blocks.PINK_WOOL)) {
            currentCell.removeSouthWall();
        }
        if (is(level, x + size, 19, z, Blocks.ORANGE_WOOL) || is(level, x + size, 19, z, Blocks.PINK_WOOL)) {
            currentCell.removeWestWall();
        }
        if (is(level, x - size, 19, z, Blocks.LIME_WOOL) || is(level, x - size, 19, z, Blocks.PINK_WOOL)) {
            currentCell.removeEastWall();
        }

        if (candidates.isEmpty()) return null;
        int r = RandomSource.create().nextIntBetweenInclusive(0, candidates.size() - 1);
        lastRoomDir = directions.get(r);
        return candidates.get(r);
    }

    /** Sambungan ke samping diberi pintu dengan peluang 1/5, ke depan/belakang 1/3. */
    @Override
    public void removeWalls(MazeCell current, MazeCell neighbour) {
        RandomSource random = RandomSource.create();
        int dx = current.getGridPosX() - neighbour.getGridPosX();
        if (dx != 0) {
            boolean door = random.nextIntBetweenInclusive(1, 5) == 1;
            if (dx > 0) {
                current.removeEastWall();
                neighbour.removeWestWall();
                if (door) {
                    current.addEastDoor();
                    neighbour.addWestDoor();
                }
            } else {
                current.removeWestWall();
                neighbour.removeEastWall();
                if (door) {
                    current.addWestDoor();
                    neighbour.addEastDoor();
                }
            }
        }
        int dy = current.getGridPosY() - neighbour.getGridPosY();
        if (dy != 0) {
            boolean door = random.nextIntBetweenInclusive(1, 3) == 1;
            if (dy > 0) {
                current.removeSouthWall();
                neighbour.removeNorthWall();
                if (door) {
                    current.addSouthDoor();
                    neighbour.addNorthDoor();
                }
            } else {
                current.removeNorthWall();
                neighbour.removeSouthWall();
                if (door) {
                    current.addNorthDoor();
                    neighbour.addSouthDoor();
                }
            }
        }
    }
}
