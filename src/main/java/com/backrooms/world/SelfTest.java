package com.backrooms.world;

import com.backrooms.block.ModBlocks;
import com.backrooms.lobby.LobbyManager;
import com.backrooms.lobby.LobbyStage;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.Map;
import java.util.TreeMap;

/**
 * Uji otomatis untuk development: {@code gradlew runServer -Pselftest=true}.
 * Membangun ketiga level (plus set lobby-nya), mencatat isi tiap level ke
 * log, lalu mematikan server. Tidak aktif di permainan biasa.
 */
public final class SelfTest {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean running;

    private SelfTest() {}

    public static boolean enabled() {
        return Boolean.getBoolean("backrooms.selftest");
    }

    public static void start(MinecraftServer server) {
        running = true;
        ModelCheck.run();
        for (BackroomsLevel level : BackroomsLevel.values()) {
            LOGGER.info("[selftest] {}", LevelBuilder.prebuild(server, BuildArea.main(level)));
        }
    }

    public static void tick(MinecraftServer server) {
        if (!running) return;
        if (LevelBuilder.isBuildingAnything()) return;
        running = false;
        for (BackroomsLevel which : BackroomsLevel.values()) {
            report(server.getLevel(which.dimension()), BuildArea.main(which));
            report(server.getLevel(BuildArea.lobby(which).dimension()), BuildArea.lobby(which));
            LobbyStage stage = LobbyManager.stage(which);
            LOGGER.info("[selftest] {} lobby: panggung {}", which.displayName(),
                    stage == null ? "BELUM ADA" : stage.pos().toShortString() + " menghadap " + stage.facing());
        }
        LOGGER.info("[selftest] selesai, server dimatikan.");
        server.halt(false);
    }

    private static void report(ServerLevel level, BuildArea area) {
        BackroomsLevel which = area.which();
        Map<String, Integer> counts = new TreeMap<>();
        int total = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = area.worldMinX(); x <= area.worldMaxX(); x++) {
            for (int z = area.worldMinZ(); z <= area.worldMaxZ(); z++) {
                for (int y = which.barrierMinY(); y <= which.barrierMaxY(); y++) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (state.isAir()) continue;
                    total++;
                    String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                    if (id.startsWith(ModBlocks.NAMESPACE)) id = id.substring(ModBlocks.NAMESPACE.length() + 1);
                    counts.merge(id, 1, Integer::sum);
                }
            }
        }
        int holes = 0;
        int minX = area.worldMinX(), maxX = area.worldMaxX();
        int minZ = area.worldMinZ(), maxZ = area.worldMaxZ();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (x != minX && x != maxX && z != minZ && z != maxZ) continue;
                // Kriteria yang sama dengan penutupnya: apa pun yang bukan blok
                // padat penuh di antara lantai dan plafon dihitung sebagai lubang.
                int[] span = LevelBuilder.solidSpan(level, which, x, z);
                for (int y = span == null ? 0 : span[0] + 1; span != null && y < span[1]; y++) {
                    if (!LevelBuilder.isSolidWall(level, pos.set(x, y, z))) holes++;
                }
            }
        }
        // Air yang lolos ke void di luar dinding/barrier (8 blok ke luar tepi).
        int leaked = 0;
        for (int x = minX - 8; x <= maxX + 8; x++) {
            for (int z = minZ - 8; z <= maxZ + 8; z++) {
                if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) continue;
                for (int y = which.barrierMinY() - 32; y <= which.barrierMaxY(); y++) {
                    if (!level.getFluidState(pos.set(x, y, z)).isEmpty()) leaked++;
                }
            }
        }
        LOGGER.info("[selftest] {}: area x {}..{} z {}..{}, lubang di tepi: {}, air bocor ke luar: {}",
                area.describe(), minX, maxX, minZ, maxZ, holes, leaked);

        Vec3 spawn = area.worldSpawn();
        BlockPos feet = BlockPos.containing(spawn);
        LOGGER.info("[selftest] {}: {} blok non-udara, {} jenis. Spawn {} -> kaki: {}, pijakan: {}",
                area.describe(), total, counts.size(), feet,
                level.getBlockState(feet), level.getBlockState(feet.below()));
        LOGGER.info("[selftest] {} isi: {}", area.describe(), counts);
    }
}
