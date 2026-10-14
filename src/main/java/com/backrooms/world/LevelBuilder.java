package com.backrooms.world;

import com.backrooms.world.gen.BuildLevel;
import com.backrooms.world.gen.LevelGenerator;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import com.backrooms.block.WallBlock;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Membangun isi sebuah dimensi pada kunjungan pertamanya.
 *
 * <h2>Kenapa dicicil per tick</h2>
 *
 * Satu level berisi ratusan ruangan (level 0 dengan radius 2: ±1 juta blok).
 * Dikerjakan sekaligus, server membeku puluhan detik dan watchdog bisa
 * mematikannya. Jadi pekerjaannya dipecah per chunk dan dijalankan selama
 * maksimal {@link #BUDGET_NANOS} per tick; pemain yang meminta masuk
 * menunggu dengan progres di action bar, lalu dipindahkan begitu selesai.
 *
 * <h2>Urutan</h2>
 *
 * Sama seperti world generation aslinya: {@code fill} (populateNoise) untuk
 * semua chunk dulu, lalu {@code generate} per pass, lalu dinding barrier di
 * tepi area. Barrier dibutuhkan karena dunia aslinya tak terbatas -- maze di
 * tepi area sengaja punya bukaan ke region sebelah, yang di sini adalah void.
 */
public final class LevelBuilder {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long BUDGET_NANOS = 35_000_000L;
    private static final TicketType<ChunkPos> TICKET =
            TicketType.create("backrooms_build", Comparator.comparingLong(ChunkPos::toLong), 200);

    private static final Map<BackroomsLevel, Job> JOBS = new EnumMap<>(BackroomsLevel.class);
    private static final Deque<BackroomsLevel> ORDER = new ArrayDeque<>();

    private LevelBuilder() {}

    public static boolean isBuilding(BackroomsLevel level) {
        return JOBS.containsKey(level);
    }

    /**
     * Pastikan level sudah dibangun, lalu jalankan {@code whenReady} untuk
     * pemain ini. Kalau sudah dibangun, langsung dijalankan.
     */
    public static void ensureBuilt(MinecraftServer server, BackroomsLevel which, ServerPlayer player,
                                   Consumer<ServerPlayer> whenReady) {
        ServerLevel level = server.getLevel(which.dimension());
        if (level == null) {
            player.sendSystemMessage(Component.literal("Dimensi " + which.dimension().location()
                    + " tidak ada. Datapack mod ini gagal dimuat? Cek log server.").withStyle(ChatFormatting.RED));
            return;
        }
        if (BuildState.get(level).isBuilt()) {
            whenReady.accept(player);
            return;
        }
        Job job = start(which, level);
        job.waiting.add(player.getUUID());
        job.callbacks.put(player.getUUID(), whenReady);
        player.sendSystemMessage(Component.literal(which.displayName()
                + " belum pernah dikunjungi, sedang dibangun... kamu akan dipindahkan otomatis.")
                .withStyle(ChatFormatting.YELLOW));
    }

    /**
     * Bangun tanpa memindahkan siapa pun (untuk admin / konsol).
     *
     * @return pesan status untuk ditampilkan ke yang meminta
     */
    public static String prebuild(MinecraftServer server, BackroomsLevel which) {
        ServerLevel level = server.getLevel(which.dimension());
        if (level == null) return "Dimensi " + which.dimension().location() + " tidak ada.";
        if (BuildState.get(level).isBuilt()) return which.displayName() + " sudah dibangun.";
        boolean already = JOBS.containsKey(which);
        Job job = start(which, level);
        return already ? which.displayName() + " sedang dibangun (" + (100L * job.done / job.total) + "%)."
                : which.displayName() + " mulai dibangun (" + job.total + " langkah), selesai dicatat di log.";
    }

    private static Job start(BackroomsLevel which, ServerLevel level) {
        return JOBS.computeIfAbsent(which, key -> {
            Job job = new Job(key, level);
            ORDER.add(key);
            LOGGER.info("Mulai membangun {} ({} langkah).", key.displayName(), job.total);
            return job;
        });
    }

    /** Dipanggil tiap akhir tick server. */
    public static void tick(MinecraftServer server) {
        BackroomsLevel which = ORDER.peek();
        if (which == null) return;
        Job job = JOBS.get(which);

        long deadline = System.nanoTime() + BUDGET_NANOS;
        try {
            while (!job.steps.isEmpty() && System.nanoTime() < deadline) {
                job.steps.poll().run();
                job.done++;
            }
        } catch (Exception e) {
            LOGGER.error("Gagal membangun {}", which.displayName(), e);
            job.notifyAll(server, Component.literal("Gagal membangun " + which.displayName()
                    + ", lihat log server.").withStyle(ChatFormatting.RED));
            JOBS.remove(which);
            ORDER.poll();
            return;
        }

        if (job.steps.isEmpty()) {
            BuildState.get(job.level).markBuilt();
            LOGGER.info("{} selesai dibangun dalam {} detik.", which.displayName(),
                    (System.currentTimeMillis() - job.startedAt) / 1000);
            JOBS.remove(which);
            ORDER.poll();
            for (UUID id : job.waiting) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null) job.callbacks.get(id).accept(player);
            }
        } else if (server.getTickCount() % 10 == 0) {
            int percent = (int) (100L * job.done / job.total);
            job.actionBar(server, Component.literal("Membangun " + which.displayName() + "... " + percent + "%")
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    /** Blok padat penuh (dinding, lantai, plafon) -- bukan udara, air, lampu, trim, slab, atau pipa. */
    static boolean isSolidWall(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).isCollisionShapeFullBlock(level, pos);
    }

    /** Y blok padat penuh terbawah dan teratas di satu kolom, atau null kalau tidak ada. */
    static int[] solidSpan(ServerLevel level, BackroomsLevel which, int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int low = Integer.MIN_VALUE;
        int high = Integer.MIN_VALUE;
        for (int y = which.barrierMinY(); y <= which.barrierMaxY(); y++) {
            if (isSolidWall(level, pos.set(x, y, z))) {
                if (low == Integer.MIN_VALUE) low = y;
                high = y;
            }
        }
        return low == Integer.MIN_VALUE ? null : new int[]{low, high};
    }

    /** Server berhenti: buang pekerjaan yang belum selesai (akan diulang dari awal). */
    public static void clear() {
        JOBS.clear();
        ORDER.clear();
    }

    private static final class Job {
        final BackroomsLevel which;
        final ServerLevel level;
        final Deque<Runnable> steps = new ArrayDeque<>();
        final Set<UUID> waiting = new LinkedHashSet<>();
        final Map<UUID, Consumer<ServerPlayer>> callbacks = new java.util.HashMap<>();
        final long startedAt = System.currentTimeMillis();
        final int total;
        int done;

        Job(BackroomsLevel which, ServerLevel level) {
            this.which = which;
            this.level = level;
            LevelGenerator generator = which.newGenerator();
            BuildLevel world = new BuildLevel(level, which.offsetX(), which.offsetZ());

            List<ChunkPos> chunks = new ArrayList<>();
            for (int cz = which.minChunkZ(); cz <= which.maxChunkZ(); cz++) {
                for (int cx = which.minChunkX(); cx <= which.maxChunkX(); cx++) {
                    chunks.add(new ChunkPos(cx, cz));
                }
            }
            for (ChunkPos chunk : chunks) {
                steps.add(() -> {
                    hold(chunk);
                    generator.fill(world, chunk);
                });
            }
            for (int pass = 0; pass < generator.passes(); pass++) {
                int p = pass;
                for (ChunkPos chunk : chunks) {
                    steps.add(() -> {
                        hold(chunk);
                        generator.generate(world, chunk, p);
                    });
                }
            }
            addSealSteps();
            addBarrierSteps();
            total = steps.size();
        }

        /**
         * Tutup lubang di tepi area dengan dinding asli level ini.
         *
         * <p>Maze aslinya sengaja membuka tepi tiap region supaya tersambung ke
         * region sebelah; di tepi luar area, sebelahnya adalah void. Di setiap
         * kolom tepi, semua yang bukan blok padat penuh di antara blok padat
         * terbawah dan teratas (lantai dan plafon) diganti dinding -- bukan
         * cuma udara, tapi juga lampu tipis, list bawah, slab, pipa, dan air,
         * karena di posisi itu tidak ada dinding di belakangnya.
         *
         * <p>Hanya ring terluar selebar 1 blok yang disentuh, dan ruangan di
         * grid maze selalu dimulai tepat di garis itu, jadi yang tertutup
         * hanya sisi yang menghadap void.
         */
        void addSealSteps() {
            Block wall = which.sealBlock();
            int minX = which.worldMinX();
            int maxX = which.worldMaxX();
            int minZ = which.worldMinZ();
            int maxZ = which.worldMaxZ();
            List<long[]> columns = new ArrayList<>();
            for (int x = minX; x <= maxX; x++) {
                columns.add(new long[]{x, minZ});
                columns.add(new long[]{x, maxZ});
            }
            for (int z = minZ + 1; z < maxZ; z++) {
                columns.add(new long[]{minX, z});
                columns.add(new long[]{maxX, z});
            }
            for (int i = 0; i < columns.size(); i += 32) {
                List<long[]> batch = columns.subList(i, Math.min(i + 32, columns.size()));
                steps.add(() -> batch.forEach(c -> seal((int) c[0], (int) c[1], wall)));
            }
        }

        void seal(int x, int z, Block wall) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int[] span = solidSpan(level, which, x, z);
            if (span == null) return;
            for (int y = span[0] + 1; y < span[1]; y++) {
                if (isSolidWall(level, pos.set(x, y, z))) continue;
                BlockState state = wall.defaultBlockState();
                if (state.hasProperty(WallBlock.BOTTOM)) {
                    // Baris paling bawah memakai list bawah, persis seperti dinding ruangan asli.
                    state = state.setValue(WallBlock.BOTTOM, !level.getBlockState(pos.below()).is(wall));
                }
                level.setBlock(pos, state, LevelGenerator.FILL_FLAGS);
            }
        }

        /** Tahan chunk sekitar tetap termuat selama dikerjakan, supaya tidak bolak-balik dibaca dari disk. */
        void hold(ChunkPos chunk) {
            ChunkPos real = new ChunkPos(new BlockPos(chunk.getMinBlockX() + which.offsetX(), 0,
                    chunk.getMinBlockZ() + which.offsetZ()));
            level.getChunkSource().addRegionTicket(TICKET, real, 3, real);
        }

        /** Dinding barrier tak terlihat satu blok di luar area bangunan. */
        void addBarrierSteps() {
            int minX = which.worldMinX() - 1;
            int maxX = which.worldMaxX() + 1;
            int minZ = which.worldMinZ() - 1;
            int maxZ = which.worldMaxZ() + 1;
            BlockState barrier = Blocks.BARRIER.defaultBlockState();
            for (int x = minX; x <= maxX; x += 16) {
                int from = x;
                steps.add(() -> {
                    for (int bx = from; bx < Math.min(from + 16, maxX + 1); bx++) {
                        wall(bx, minZ, barrier);
                        wall(bx, maxZ, barrier);
                    }
                });
            }
            for (int z = minZ; z <= maxZ; z += 16) {
                int from = z;
                steps.add(() -> {
                    for (int bz = from; bz < Math.min(from + 16, maxZ + 1); bz++) {
                        wall(minX, bz, barrier);
                        wall(maxX, bz, barrier);
                    }
                });
            }
        }

        void wall(int x, int z, BlockState barrier) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int y = which.barrierMinY(); y <= which.barrierMaxY(); y++) {
                pos.set(x, y, z);
                BlockState current = level.getBlockState(pos);
                // Air yang sempat mengalir keluar tepi selama pembangunan ikut diganti.
                if (current.isAir() || current.getBlock() instanceof LiquidBlock) {
                    level.setBlock(pos, barrier, LevelGenerator.FILL_FLAGS);
                }
            }
        }

        void actionBar(MinecraftServer server, Component message) {
            for (UUID id : waiting) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null) player.displayClientMessage(message, true);
            }
        }

        void notifyAll(MinecraftServer server, Component message) {
            for (UUID id : waiting) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null) player.sendSystemMessage(message);
            }
        }
    }
}
