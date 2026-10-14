package com.backrooms.world.gen;

import com.backrooms.config.BackroomsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Optional;

/**
 * Satu chunk generator spb-revamped yang di-port.
 *
 * <p>Di mod aslinya ini adalah {@code ChunkGenerator} sungguhan yang dipanggil
 * per chunk selama world generation. Di sini dimensinya void, dan generator
 * yang sama dijalankan sekali oleh {@link com.backrooms.world.LevelBuilder}
 * untuk setiap chunk di dalam area bangunan -- jadi isi areanya dibangun
 * dengan aturan yang persis sama, dan di luar area itu tetap void.
 *
 * <p>Semua koordinat di sini adalah koordinat asli mod; {@link BuildLevel}
 * yang menggesernya ke posisi akhir di dunia.
 *
 * <p>{@link #fill} adalah port dari {@code populateNoise} (lantai/dinding
 * dasar), {@link #generate} port dari {@code generate}.
 */
public interface LevelGenerator {

    /** Port {@code populateNoise}; dijalankan untuk semua chunk sebelum {@link #generate}. */
    default void fill(BuildLevel level, ChunkPos chunk) {
    }

    /** Berapa kali {@link #generate} dijalankan atas semua chunk; lihat Level0Generator. */
    default int passes() {
        return 1;
    }

    void generate(BuildLevel level, ChunkPos chunk, int pass);

    // ----------------------------------------------------------- bantuan

    /** Flag populateNoise aslinya ditambah sinkron ke klien: tanpa update tetangga. */
    int FILL_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    static StructurePlaceSettings settings(Rotation rotation) {
        return new StructurePlaceSettings().setMirror(Mirror.NONE).setRotation(rotation).setIgnoreEntities(true);
    }

    static Optional<StructureTemplate> template(BuildLevel level, String path) {
        return level.template(path);
    }

    static void place(BuildLevel level, Optional<StructureTemplate> template, BlockPos pos,
                      StructurePlaceSettings settings, RandomSource random, int flags) {
        level.place(template, pos, settings, random, flags);
    }

    static boolean is(BuildLevel level, int x, int y, int z, Block block) {
        return level.getBlockState(x, y, z) == block.defaultBlockState();
    }

    static void set(BuildLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, FILL_FLAGS);
    }

    /**
     * {@code !chunk.getBlockPos(0, 20, 0).isWithinDistance(0 20 0, exitSpawnRadius)}
     * dari aslinya: hanya chunk yang cukup jauh dari spawn boleh punya pintu keluar.
     */
    static boolean farFromSpawn(ChunkPos chunk) {
        return !chunk.getBlockAt(0, 20, 0).closerThan(new Vec3i(0, 20, 0), BackroomsConfig.EXIT_SPAWN_RADIUS.get());
    }

    /** {@code (float) n % 5.0F == 0.0F} dari aslinya; benar juga untuk bilangan negatif. */
    static boolean onMazeGrid(ChunkPos chunk) {
        return chunk.x % 5 == 0 && chunk.z % 5 == 0;
    }
}
