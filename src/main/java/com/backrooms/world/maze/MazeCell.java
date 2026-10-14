package com.backrooms.world.maze;

import net.minecraft.core.BlockPos;
import com.backrooms.world.gen.BuildLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Optional;

/**
 * Port dari {@code com.sp.world.generation.maze_generator.cells.MazeCell}.
 *
 * <p>Satu sel maze. {@code walls} adalah 4 bit dinding yang masih berdiri
 * (N=8, W=4, S=2, E=1); DFS meruntuhkannya, lalu {@link #drawWalls} memilih
 * ruangan yang bentuk bukaannya cocok -- aroom (persimpangan), broom (T),
 * croom (tikungan), droom (lorong), eroom (buntu) -- dan memutarnya sesuai
 * arah. Semua ditempel di Y=20, sama seperti aslinya.
 *
 * <p>Nama ruangan dan rotasi di setiap {@code case} disalin apa adanya.
 */
public class MazeCell {

    private static final int PLACE_Y = 20;

    private final int worldYPos;
    private final int worldXPos;
    private final int gridPosX;
    private final int gridPosY;
    private final int cellSize;
    private int walls;
    private int doors;
    private boolean visited;

    public MazeCell(int worldYPos, int worldXPos, int cellSize, int gridPosY, int gridPosX) {
        this.worldXPos = worldXPos;
        this.worldYPos = worldYPos;
        this.gridPosX = gridPosX;
        this.gridPosY = gridPosY;
        this.walls = 15;
        this.doors = 0;
        this.cellSize = cellSize;
        this.visited = false;
    }

    /** Port dari {@code MathStuff.rotateBits}: putar 4 bit ke kanan sebanyak {@code distance}. */
    public static int rotateBits(int value, int distance) {
        int repeating = 0;
        for (int i = 0; i < 8; ++i) {
            repeating |= value << i * 4;
        }
        return Integer.rotateRight(repeating, distance) & 15;
    }

    /** Versi poolrooms: ruangan bisa punya pintu di sisi-sisi yang terbuka. */
    public void drawWallsWithDoors(BuildLevel level, String levelId, RoomCount roomCount) {
        StructurePlaceSettings settings = new StructurePlaceSettings().setIgnoreEntities(true);
        RandomSource random = RandomSource.create();
        int aroomNumber = random.nextIntBetweenInclusive(1, roomCount.aRoomCount());
        int broomNumber = random.nextIntBetweenInclusive(1, roomCount.bRoomCount());
        int croomNumber = random.nextIntBetweenInclusive(1, roomCount.cRoomCount());
        int droomNumber = random.nextIntBetweenInclusive(1, roomCount.dRoomCount());
        int eroomNumber = random.nextIntBetweenInclusive(1, roomCount.eRoomCount());

        String roomId;
        switch (walls) {
            case 0 -> {
                switch (doors) {
                    case 0 -> { roomId = levelId + "/aroom_" + aroomNumber; rotate(settings, Rotation.NONE); }
                    case 1 -> { roomId = levelId + "/aroom_1door_" + aroomNumber; rotate(settings, Rotation.CLOCKWISE_90); }
                    case 2 -> { roomId = levelId + "/aroom_1door_" + aroomNumber; rotate(settings, Rotation.CLOCKWISE_180); }
                    case 3 -> { roomId = levelId + "/aroom_cornerdoor_" + aroomNumber; rotate(settings, Rotation.CLOCKWISE_90); }
                    case 4 -> { roomId = levelId + "/aroom_1door_" + aroomNumber; rotate(settings, Rotation.COUNTERCLOCKWISE_90); }
                    case 5 -> { roomId = levelId + "/aroom_halldoor_" + aroomNumber; rotate(settings, Rotation.CLOCKWISE_90); }
                    case 6 -> { roomId = levelId + "/aroom_cornerdoor_" + aroomNumber; rotate(settings, Rotation.CLOCKWISE_180); }
                    case 7 -> { roomId = levelId + "/aroom_3door_" + aroomNumber; rotate(settings, Rotation.CLOCKWISE_180); }
                    case 8 -> { roomId = levelId + "/aroom_1door_" + aroomNumber; rotate(settings, Rotation.NONE); }
                    case 9 -> { roomId = levelId + "/aroom_cornerdoor_" + aroomNumber; rotate(settings, Rotation.NONE); }
                    case 10 -> { roomId = levelId + "/aroom_halldoor_" + aroomNumber; rotate(settings, Rotation.NONE); }
                    case 11 -> { roomId = levelId + "/aroom_3door_" + aroomNumber; rotate(settings, Rotation.CLOCKWISE_90); }
                    case 12 -> { roomId = levelId + "/aroom_cornerdoor_" + aroomNumber; rotate(settings, Rotation.COUNTERCLOCKWISE_90); }
                    case 13 -> { roomId = levelId + "/aroom_3door_" + aroomNumber; rotate(settings, Rotation.NONE); }
                    case 14 -> { roomId = levelId + "/aroom_3door_" + aroomNumber; rotate(settings, Rotation.COUNTERCLOCKWISE_90); }
                    default -> { roomId = levelId + "/aroom_4door_" + aroomNumber; rotate(settings, Rotation.NONE); }
                }
            }
            case 1 -> { doors = rotateBits(doors, 3); roomId = threeWayDoor(levelId, broomNumber); rotate(settings, Rotation.COUNTERCLOCKWISE_90); }
            case 2 -> { roomId = threeWayDoor(levelId, broomNumber); rotate(settings, Rotation.NONE); }
            case 3 -> { doors = rotateBits(doors, 3); roomId = cornerDoor(levelId, croomNumber); rotate(settings, Rotation.COUNTERCLOCKWISE_90); }
            case 4 -> { doors = rotateBits(doors, 1); roomId = threeWayDoor(levelId, broomNumber); rotate(settings, Rotation.CLOCKWISE_90); }
            case 5 -> { roomId = hallwayDoor(levelId, droomNumber); rotate(settings, Rotation.NONE); }
            case 6 -> { roomId = cornerDoor(levelId, croomNumber); rotate(settings, Rotation.NONE); }
            case 7 -> { roomId = singleDoor(levelId, eroomNumber); rotate(settings, Rotation.NONE); }
            case 8 -> { doors = rotateBits(doors, 2); roomId = threeWayDoor(levelId, broomNumber); rotate(settings, Rotation.CLOCKWISE_180); }
            case 9 -> { doors = rotateBits(doors, 2); roomId = cornerDoor(levelId, croomNumber); rotate(settings, Rotation.CLOCKWISE_180); }
            case 10 -> { doors = rotateBits(doors, 1); roomId = hallwayDoor(levelId, droomNumber); rotate(settings, Rotation.CLOCKWISE_90); }
            case 11 -> { doors = rotateBits(doors, 3); roomId = singleDoor(levelId, eroomNumber); rotate(settings, Rotation.COUNTERCLOCKWISE_90); }
            case 12 -> { doors = rotateBits(doors, 1); roomId = cornerDoor(levelId, croomNumber); rotate(settings, Rotation.CLOCKWISE_90); }
            case 13 -> { doors = rotateBits(doors, 2); roomId = singleDoor(levelId, eroomNumber); rotate(settings, Rotation.CLOCKWISE_180); }
            case 14 -> { doors = rotateBits(doors, 1); roomId = singleDoor(levelId, eroomNumber); rotate(settings, Rotation.CLOCKWISE_90); }
            default -> roomId = levelId + "/aroom_" + aroomNumber;
        }
        place(level, roomId, settings, random);
    }

    /** Versi level 0 dan level 1: ruangan tanpa pintu. */
    public void drawWalls(BuildLevel level, String levelId, RoomCount roomCount) {
        StructurePlaceSettings settings = new StructurePlaceSettings().setIgnoreEntities(true);
        RandomSource random = RandomSource.create();
        int aroomNumber = random.nextIntBetweenInclusive(1, roomCount.aRoomCount());
        int broomNumber = random.nextIntBetweenInclusive(1, roomCount.bRoomCount());
        int croomNumber = random.nextIntBetweenInclusive(1, roomCount.cRoomCount());
        int droomNumber = random.nextIntBetweenInclusive(1, roomCount.dRoomCount());
        int eroomNumber = random.nextIntBetweenInclusive(1, roomCount.eRoomCount());

        String roomId;
        switch (walls) {
            case 0 -> { roomId = levelId + "/aroom_" + aroomNumber; rotate(settings, Rotation.NONE); }
            case 1 -> { roomId = levelId + "/broom_" + broomNumber; rotate(settings, Rotation.COUNTERCLOCKWISE_90); }
            case 2 -> { roomId = levelId + "/broom_" + broomNumber; rotate(settings, Rotation.NONE); }
            case 3 -> { roomId = levelId + "/croom_" + croomNumber; rotate(settings, Rotation.COUNTERCLOCKWISE_90); }
            case 4 -> { roomId = levelId + "/broom_" + broomNumber; rotate(settings, Rotation.CLOCKWISE_90); }
            case 5 -> { roomId = levelId + "/droom_" + droomNumber; rotate(settings, Rotation.NONE); }
            case 6 -> { roomId = levelId + "/croom_" + croomNumber; rotate(settings, Rotation.NONE); }
            case 7 -> { roomId = levelId + "/eroom_" + eroomNumber; rotate(settings, Rotation.NONE); }
            case 8 -> { roomId = levelId + "/broom_" + broomNumber; rotate(settings, Rotation.CLOCKWISE_180); }
            case 9 -> { roomId = levelId + "/croom_" + croomNumber; rotate(settings, Rotation.CLOCKWISE_180); }
            case 10 -> { roomId = levelId + "/droom_" + droomNumber; rotate(settings, Rotation.CLOCKWISE_90); }
            case 11 -> { roomId = levelId + "/eroom_" + eroomNumber; rotate(settings, Rotation.COUNTERCLOCKWISE_90); }
            case 12 -> { roomId = levelId + "/croom_" + croomNumber; rotate(settings, Rotation.CLOCKWISE_90); }
            case 13 -> { roomId = levelId + "/eroom_" + eroomNumber; rotate(settings, Rotation.CLOCKWISE_180); }
            case 14 -> { roomId = levelId + "/eroom_" + eroomNumber; rotate(settings, Rotation.CLOCKWISE_90); }
            default -> roomId = levelId + "/aroom_" + aroomNumber;
        }
        place(level, roomId, settings, random);
    }

    private static void rotate(StructurePlaceSettings settings, Rotation rotation) {
        settings.setMirror(Mirror.NONE).setRotation(rotation);
    }

    /** Titik tempel digeser sesuai rotasi supaya ruangan tetap mengisi kotak selnya. */
    private void place(BuildLevel level, String roomId, StructurePlaceSettings settings, RandomSource random) {
        Optional<StructureTemplate> template = level.template(roomId);
        if (template.isEmpty()) return;
        int offset = cellSize - 1;
        BlockPos pos = switch (settings.getRotation()) {
            case NONE -> new BlockPos(worldXPos, PLACE_Y, worldYPos);
            case CLOCKWISE_90 -> new BlockPos(worldXPos + offset, PLACE_Y, worldYPos);
            case COUNTERCLOCKWISE_90 -> new BlockPos(worldXPos, PLACE_Y, worldYPos + offset);
            case CLOCKWISE_180 -> new BlockPos(worldXPos + offset, PLACE_Y, worldYPos + offset);
        };
        level.place(template, pos, settings, random, 2);
    }

    private String threeWayDoor(String level, int number) {
        return switch (doors) {
            case 0 -> level + "/broom_" + number;
            case 1 -> level + "/broom_1door_east_" + number;
            case 4 -> level + "/broom_1door_west_" + number;
            case 5 -> level + "/broom_2door_" + number;
            case 8 -> level + "/broom_1door_north_" + number;
            case 9 -> level + "/broom_2door_1_" + number;
            case 12 -> level + "/broom_2door_2_" + number;
            default -> level + "/broom_3door_" + number;
        };
    }

    private String cornerDoor(String level, int number) {
        return switch (doors) {
            case 0 -> level + "/croom_" + number;
            case 1 -> level + "/croom_1door_1_" + number;
            case 8 -> level + "/croom_1door_2_" + number;
            default -> level + "/croom_2door_" + number;
        };
    }

    private String hallwayDoor(String level, int number) {
        return switch (doors) {
            case 0 -> level + "/droom_" + number;
            case 2 -> level + "/droom_1door_1_" + number;
            case 8 -> level + "/droom_1door_2_" + number;
            default -> level + "/droom_2door_" + number;
        };
    }

    private String singleDoor(String level, int number) {
        return doors == 8 ? level + "/eroom_door_" + number : level + "/eroom_" + number;
    }

    public int getGridPosX() { return gridPosX; }

    public int getGridPosY() { return gridPosY; }

    public boolean isVisited() { return visited; }

    public void setVisited(boolean visited) { this.visited = visited; }

    public int getWorldXPos() { return worldXPos; }

    public int getWorldYPos() { return worldYPos; }

    public void addNorthDoor() { doors |= 8; }

    public void addWestDoor() { doors |= 4; }

    public void addSouthDoor() { doors |= 2; }

    public void addEastDoor() { doors |= 1; }

    public void removeNorthWall() { walls &= ~8; }

    public void removeWestWall() { walls &= ~4; }

    public void removeSouthWall() { walls &= ~2; }

    public void removeEastWall() { walls &= ~1; }
}
