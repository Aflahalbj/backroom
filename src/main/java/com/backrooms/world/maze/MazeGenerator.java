package com.backrooms.world.maze;

import net.minecraft.core.BlockPos;
import com.backrooms.world.gen.BuildLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Stack;

/**
 * Bagian bersama dari {@code Level0MazeGenerator}, {@code Level1MazeGenerator},
 * dan {@code PoolroomsMazeGenerator} asli: grid sel dan DFS recursive
 * backtracker-nya. Yang beda antar level (sel mana yang boleh dipakai,
 * penanda wol yang dibaca, bukaan di tepi, cara menggambar) ada di subclass.
 *
 * <p>Catatan penamaan yang diwarisi dari aslinya: "Y" di sini adalah sumbu Z
 * dunia, dan {@code grid} diindeks {@code [x][y]}.
 */
public abstract class MazeGenerator {

    protected final int cols;
    protected final int rows;
    protected final int size;
    protected final MazeCell[][] grid;
    protected final int originX;
    protected final int originY;
    protected final String levelDirectory;
    protected MazeCell currentCell;

    protected MazeGenerator(int size, int rows, int cols, int originX, int originY, String levelDirectory) {
        this.size = size;
        this.rows = rows;
        this.cols = cols;
        this.grid = new MazeCell[rows][cols];
        this.originX = originX - 32;
        this.originY = originY - 32;
        this.levelDirectory = levelDirectory;
    }

    protected int cellX(int x) {
        return x + (size - 1) * x + originX;
    }

    protected int cellZ(int y) {
        return y + (size - 1) * y + originY;
    }

    protected static boolean isAirOrNull(BlockState state) {
        return state == null || state.isAir();
    }

    protected static boolean is(BuildLevel level, int x, int y, int z, Block block) {
        return level.getBlockState(new BlockPos(x, y, z)) == block.defaultBlockState();
    }

    /** DFS dari sel [0][0], yang harus sudah dipastikan ada oleh pemanggil. */
    protected void carve(BuildLevel level) {
        currentCell = grid[0][0];
        Stack<MazeCell> stack = new Stack<>();
        currentCell.setVisited(true);
        stack.push(currentCell);
        while (!stack.isEmpty()) {
            for (MazeCell next = checkNeighbors(level); next != null; next = checkNeighbors(level)) {
                next.setVisited(true);
                removeWalls(currentCell, next);
                currentCell = next;
                stack.push(currentCell);
            }
            currentCell = stack.pop();
        }
    }

    protected abstract MazeCell checkNeighbors(BuildLevel level);

    /** Tetangga yang belum dikunjungi, urutan N, W, S, E seperti aslinya. */
    protected MazeCell[] neighbours() {
        int y = currentCell.getGridPosY();
        int x = currentCell.getGridPosX();
        MazeCell north = y + 1 < rows ? grid[x][y + 1] : null;
        MazeCell west = x + 1 < cols ? grid[x + 1][y] : null;
        MazeCell south = y - 1 >= 0 ? grid[x][y - 1] : null;
        MazeCell east = x - 1 >= 0 ? grid[x - 1][y] : null;
        return new MazeCell[]{north, west, south, east};
    }

    protected static MazeCell randomOf(java.util.List<MazeCell> neighbours) {
        if (neighbours.isEmpty()) return null;
        return neighbours.get(RandomSource.create().nextIntBetweenInclusive(0, neighbours.size() - 1));
    }

    public void removeWalls(MazeCell current, MazeCell neighbour) {
        int dx = current.getGridPosX() - neighbour.getGridPosX();
        if (dx != 0) {
            if (dx > 0) {
                current.removeEastWall();
                neighbour.removeWestWall();
            } else {
                current.removeWestWall();
                neighbour.removeEastWall();
            }
        }
        int dy = current.getGridPosY() - neighbour.getGridPosY();
        if (dy != 0) {
            if (dy > 0) {
                current.removeSouthWall();
                neighbour.removeNorthWall();
            } else {
                current.removeNorthWall();
                neighbour.removeSouthWall();
            }
        }
    }

    /**
     * Buka sel-sel di tepi grid berselang-seling supaya maze ini tersambung
     * ke maze tetangganya. Titik awal tiap sisi berbeda antar level.
     */
    protected void openEdges(int westStart, int northStart) {
        for (int i = 0; i < cols; i += 2) {
            if (grid[i][0] != null) grid[i][0].removeSouthWall();
        }
        for (int i = westStart; i < cols; i += 2) {
            if (grid[cols - 1][i] != null) grid[cols - 1][i].removeWestWall();
        }
        for (int i = northStart; i >= 0; i -= 2) {
            if (grid[i][cols - 1] != null) grid[i][cols - 1].removeNorthWall();
        }
        for (int i = cols - 1; i >= 0; i -= 2) {
            if (grid[0][i] != null) grid[0][i].removeEastWall();
        }
    }
}
