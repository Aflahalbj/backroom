package com.backrooms.lobby;

import com.backrooms.block.FluorescentLightBlock;
import com.backrooms.block.Lights;
import com.backrooms.block.ThinFluorescentLightBlock;
import com.backrooms.world.BackroomsLevel;
import com.backrooms.world.BuildArea;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Mencari tempat terbaik di set lobby (yang dibangun generator asli, jadi
 * susunannya acak) untuk barisan karakter dan kamera.
 *
 * <p>Syaratnya: lantai rata, kering, dan beratap yang cukup untuk dua baris
 * pemain, ruang kosong sampai ke posisi kamera, dan ruang di belakang barisan.
 * Dari semua yang memenuhi, dipilih yang punya kedalaman paling jauh di
 * belakang barisan (kesan lorong tanpa ujung) dan paling terang: lampu
 * menyala di atas dan di depan barisan (lihat {@link #lighting}). Di Poolrooms
 * juga yang lantainya paling luas, supaya pemain tidak berdiri di tepi kolam.
 */
final class StageFinder {

    private static final Logger LOGGER = LogUtils.getLogger();

    /*
     * Ukuran dalam blok; sumbu "a" ke arah kamera, "b" ke samping. Tempat
     * berdiri kedua baris pemain (a -2..0, b -2..2) harus kosong dan berlantai;
     * jalur pandang kamera (a 1..reach, b -1..1) cukup kosong -- tiang di kiri
     * kanannya boleh, malah jadi bagian latar.
     */
    private static final int STAND_BACK = 2;
    private static final int STAND_HALF_WIDTH = 2;
    private static final int SIGHT_HALF_WIDTH = 1;
    private static final int MIN_REACH = 4;
    private static final int MAX_REACH = 7;
    private static final int MAX_DEPTH = 30;
    /** Area lantai di sekitar panggung yang dinilai (dan di Poolrooms dikeringkan); a ke kamera, b ke samping. */
    private static final int DRY_BACK = 8;
    private static final int DRY_FRONT = MAX_REACH + 1;
    private static final int DRY_HALF_WIDTH = 7;

    private StageFinder() {}

    static LobbyStage find(ServerLevel level, BuildArea area) {
        long started = System.currentTimeMillis();
        BackroomsLevel which = area.which();
        int minX = area.worldMinX() + 2, maxX = area.worldMaxX() - 2;
        int minZ = area.worldMinZ() + 2, maxZ = area.worldMaxZ() - 2;
        Grid grid = new Grid(level);
        List<BlockPos> lights = lights(level, which, minX, maxX, minZ, maxZ);
        BlockPos spawn = BlockPos.containing(area.worldSpawn());

        Search search = search(grid, which, lights, spawn, minX, maxX, minZ, maxZ, true);
        LobbyStage best = search.best;
        boolean paved = false;
        if (best == null && which == BackroomsLevel.POOLROOMS) {
            // Lantai set Poolrooms terendam seluruhnya: pilih lantai terluas
            // (boleh berair), lalu airnya ditutup keramik jadi dek kolam.
            search = search(grid, which, lights, spawn, minX, maxX, minZ, maxZ, false);
            if (search.best != null) {
                best = pave(level, search.best);
                paved = true;
            }
        }
        if (best == null) {
            LOGGER.warn("Tidak ada tempat lapang di set lobby {}, pakai titik spawn.", area.describe());
            best = new LobbyStage(spawn, Direction.SOUTH, MIN_REACH);
        }
        int standY = best.pos().getY();
        LOGGER.info("Set lobby {}: panggung di {} menghadap {}, kamera sampai {} blok (skor {}, lantai Y={}, {} lampu{}, {} ms).",
                area.describe(), best.pos().toShortString(), best.facing(), best.reach(), String.format("%.1f", search.score),
                standY, lights.size(), paved ? ", air ditutup keramik" : "", System.currentTimeMillis() - started);
        if (Boolean.getBoolean("backrooms.selftest")) {
            StringBuilder map = new StringBuilder();
            for (int z = minZ; z <= maxZ; z++) {
                map.append('\n');
                for (int x = minX; x <= maxX; x++) {
                    int lx = x, lz = z;
                    boolean light = lights.stream().anyMatch(p -> p.getX() == lx && p.getZ() == lz);
                    char c = !grid.passable(x, standY, z) || !grid.open(x, standY + 1, z) ? '#'
                            : !grid.open(x, standY, z) ? '~'
                            : !grid.floor(x, standY - 1, z) ? ' ' : light ? 'o' : '.';
                    if (x == best.pos().getX() && z == best.pos().getZ()) c = '@';
                    map.append(c);
                }
            }
            LOGGER.info("[selftest] peta {} Y={} (x ke kanan, z ke bawah):{}", area.describe(), standY, map);
        }
        return best;
    }

    private record Search(LobbyStage best, double score) {}

    /**
     * Panggung terbaik di ketinggian lantai yang paling umum. {@code dry}: kaki
     * tidak boleh di air; kalau tidak, airnya nanti ditutup keramik
     * ({@link #pave}) sehingga semuanya naik satu blok -- perlu ruang kepala
     * satu blok lebih tinggi.
     */
    private static Search search(Grid grid, BackroomsLevel which, List<BlockPos> lights, BlockPos spawn,
                                 int minX, int maxX, int minZ, int maxZ, boolean dry) {
        LobbyStage best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int y : commonStandYs(grid, which, minX, maxX, minZ, maxZ, dry)) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!grid.standable(x, y, z) || dry && !grid.open(x, y, z)) continue;
                    for (Direction facing : Direction.Plane.HORIZONTAL) {
                        if (!standZone(grid, x, y, z, facing, dry)) continue;
                        int reach = reach(grid, x, y, z, facing, dry);
                        if (reach < MIN_REACH) continue;
                        double score = depth(grid, x, y, z, facing)
                                + (which == BackroomsLevel.POOLROOMS ? 0.5D * floorArea(grid, x, y, z, facing, dry) : 0.0D)
                                + 2.0D * reach
                                + lighting(lights, x, y, z, facing, reach)
                                - 0.02D * Math.sqrt(spawn.distSqr(new BlockPos(x, y, z)));
                        if (score > bestScore) {
                            bestScore = score;
                            best = new LobbyStage(new BlockPos(x, y, z), facing, reach);
                        }
                    }
                }
            }
        }
        return new Search(best, bestScore);
    }

    /**
     * Tutup air di sekitar panggung (area yang sama dengan {@link #floorArea})
     * dengan bahan lantainya, jadi dek rata setinggi permukaan air; panggung
     * naik satu blok ke atas dek itu.
     */
    private static LobbyStage pave(ServerLevel level, LobbyStage stage) {
        Direction facing = stage.facing();
        Direction right = facing.getClockWise();
        BlockPos center = stage.pos();
        BlockState floor = level.getBlockState(center.below());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int a = -DRY_BACK; a <= DRY_FRONT; a++) {
            for (int b = -DRY_HALF_WIDTH; b <= DRY_HALF_WIDTH; b++) {
                pos.set(center.getX() + facing.getStepX() * a + right.getStepX() * b, center.getY(),
                        center.getZ() + facing.getStepZ() * a + right.getStepZ() * b);
                if (level.getBlockState(pos).getFluidState().isEmpty()) continue;
                level.setBlock(pos, floor, Block.UPDATE_CLIENTS);
            }
        }
        return new LobbyStage(center.above(), facing, stage.reach());
    }

    /** Ketinggian lantai yang paling sering muncul, terbanyak dulu (paling banyak 4). */
    private static List<Integer> commonStandYs(Grid grid, BackroomsLevel which, int minX, int maxX, int minZ, int maxZ,
                                               boolean dry) {
        int lowY = which.barrierMinY() + 1;
        int highY = which.barrierMaxY() - 3;
        int[] counts = new int[highY - lowY + 1];
        for (int x = minX; x <= maxX; x += 2) {
            for (int z = minZ; z <= maxZ; z += 2) {
                for (int y = lowY; y <= highY; y++) {
                    if (grid.standable(x, y, z) && (!dry || grid.open(x, y, z))) counts[y - lowY]++;
                }
            }
        }
        List<Integer> ys = new ArrayList<>();
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] > 0) ys.add(i);
        }
        ys.sort((a, b) -> Integer.compare(counts[b], counts[a]));
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < Math.min(4, ys.size()); i++) result.add(lowY + ys.get(i));
        return result;
    }

    private static List<BlockPos> lights(ServerLevel level, BackroomsLevel which, int minX, int maxX, int minZ, int maxZ) {
        List<BlockPos> found = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = which.barrierMinY(); y <= which.barrierMaxY(); y++) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if ((state.getBlock() instanceof FluorescentLightBlock || state.getBlock() instanceof ThinFluorescentLightBlock)
                            && Lights.lit(state)) {
                        found.add(pos.immutable());
                    }
                }
            }
        }
        return found;
    }

    /** Tempat berdiri kedua baris pemain kosong setinggi 3 blok dan berlantai ({@code dry}: juga tanpa air). */
    private static boolean standZone(Grid grid, int x, int y, int z, Direction facing, boolean dry) {
        Direction right = facing.getClockWise();
        for (int a = -STAND_BACK; a <= 0; a++) {
            for (int b = -STAND_HALF_WIDTH; b <= STAND_HALF_WIDTH; b++) {
                int px = x + facing.getStepX() * a + right.getStepX() * b;
                int pz = z + facing.getStepZ() * a + right.getStepZ() * b;
                if (!grid.body(px, y, pz) || dry && !grid.open(px, y, pz) || !grid.floor(px, y - 1, pz)) return false;
                if (!dry && !grid.open(px, y + 3, pz)) return false;
            }
        }
        return true;
    }

    /** Seberapa jauh kamera bisa mundur dari barisan dengan jalur pandang tetap kosong. */
    private static int reach(Grid grid, int x, int y, int z, Direction facing, boolean dry) {
        Direction right = facing.getClockWise();
        for (int a = 1; a <= MAX_REACH; a++) {
            for (int b = -SIGHT_HALF_WIDTH; b <= SIGHT_HALF_WIDTH; b++) {
                int px = x + facing.getStepX() * a + right.getStepX() * b;
                int pz = z + facing.getStepZ() * a + right.getStepZ() * b;
                if (!grid.body(px, y, pz) || !dry && !grid.open(px, y + 3, pz)) return a - 1;
            }
        }
        return MAX_REACH;
    }

    /** Rata-rata ruang kosong di belakang barisan, diukur di tengah dan kedua sisinya. */
    private static double depth(Grid grid, int x, int y, int z, Direction facing) {
        Direction right = facing.getClockWise();
        double total = 0.0D;
        for (int b = -2; b <= 2; b += 2) {
            int run = 0;
            for (int a = -STAND_BACK - 1; a >= -MAX_DEPTH; a--) {
                int px = x + facing.getStepX() * a + right.getStepX() * b;
                int pz = z + facing.getStepZ() * a + right.getStepZ() * b;
                if (!grid.passable(px, y, pz) || !grid.open(px, y + 1, pz)) break;
                run++;
            }
            total += run;
        }
        return total / 3.0D;
    }

    /** Banyaknya petak lantai (bisa diinjak; {@code dry}: tanpa air) di sekitar panggung. */
    private static int floorArea(Grid grid, int x, int y, int z, Direction facing, boolean dry) {
        Direction right = facing.getClockWise();
        int count = 0;
        for (int a = -DRY_BACK; a <= DRY_FRONT; a++) {
            for (int b = -DRY_HALF_WIDTH; b <= DRY_HALF_WIDTH; b++) {
                int px = x + facing.getStepX() * a + right.getStepX() * b;
                int pz = z + facing.getStepZ() * a + right.getStepZ() * b;
                if (grid.floor(px, y - 1, pz) && grid.body(px, y, pz) && (!dry || grid.open(px, y, pz))) count++;
            }
        }
        return count;
    }

    /**
     * Nilai pencahayaan panggung. Yang paling penting lampu tepat di atas
     * barisan (menerangi wajah dan badan karakter), lalu lampu di depannya
     * antara barisan dan kamera; lampu di lorong belakang hanya menambah
     * suasana. Tanpa lampu di atas maupun di depan, karakter jadi siluet
     * gelap -- panggung seperti itu dihindari.
     */
    private static double lighting(List<BlockPos> lights, int x, int y, int z, Direction facing, int reach) {
        Direction right = facing.getClockWise();
        int key = 0, front = 0, back = 0;
        for (BlockPos light : lights) {
            int dx = light.getX() - x;
            int dz = light.getZ() - z;
            int a = dx * facing.getStepX() + dz * facing.getStepZ();
            int b = dx * right.getStepX() + dz * right.getStepZ();
            int dy = light.getY() - y;
            if (dy < 1 || dy > 7) continue;
            if (a >= -STAND_BACK - 1 && a <= 2 && Math.abs(b) <= 3) {
                key++;
            } else if (a >= 3 && a <= reach + 2 && Math.abs(b) <= 3) {
                front++;
            } else if (a >= -14 && a < -STAND_BACK - 1 && Math.abs(b) <= 8) {
                back++;
            }
        }
        double score = 12.0D * Math.min(3, key) + 5.0D * Math.min(2, front) + 2.0D * Math.min(6, back);
        return key == 0 && front == 0 ? score - 60.0D : score;
    }

    /** Pembacaan blok dengan cache sederhana per posisi (panggung saling tumpang tindih). */
    private static final class Grid {
        private final ServerLevel level;
        private final java.util.Map<Long, Byte> cache = new java.util.HashMap<>();
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        Grid(ServerLevel level) {
            this.level = level;
        }

        /** Bit 1 = udara bebas (tanpa collision, tanpa cairan); bit 2 = lantai padat penuh; bit 4 = tanpa collision. */
        private byte flags(int x, int y, int z) {
            long key = BlockPos.asLong(x, y, z);
            Byte cached = cache.get(key);
            if (cached != null) return cached;
            pos.set(x, y, z);
            BlockState state = level.getBlockState(pos);
            byte flags = 0;
            if (state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty()) flags |= 1;
            if (state.isCollisionShapeFullBlock(level, pos)) flags |= 2;
            if (state.getCollisionShape(level, pos).isEmpty()) flags |= 4;
            cache.put(key, flags);
            return flags;
        }

        boolean open(int x, int y, int z) {
            return (flags(x, y, z) & 1) != 0;
        }

        boolean floor(int x, int y, int z) {
            return (flags(x, y, z) & 2) != 0;
        }

        /** Bisa dilewati, boleh berair (genangan Poolrooms). */
        boolean passable(int x, int y, int z) {
            return (flags(x, y, z) & 4) != 0;
        }

        /** Muat orang berdiri: kaki boleh di air dangkal, badan dan kepala di udara. */
        boolean body(int x, int y, int z) {
            return passable(x, y, z) && open(x, y + 1, z) && open(x, y + 2, z);
        }

        /** Bisa diinjak, muat orang berdiri, dan beratap -- bukan di atas atap bangunan. */
        boolean standable(int x, int y, int z) {
            return floor(x, y - 1, z) && body(x, y, z) && covered(x, y, z);
        }

        private boolean covered(int x, int y, int z) {
            for (int dy = 3; dy <= 12; dy++) {
                if (floor(x, y + dy, z)) return true;
            }
            return false;
        }
    }
}
