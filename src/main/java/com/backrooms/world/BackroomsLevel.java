package com.backrooms.world;

import com.backrooms.block.ModBlocks;
import com.backrooms.world.gen.Level0Generator;
import com.backrooms.world.gen.Level1Generator;
import com.backrooms.world.gen.LevelGenerator;
import com.backrooms.world.gen.PoolroomsGenerator;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * Ketiga dimensi. ID dimensi, titik spawn, dan generator-nya sama dengan mod
 * aslinya (lihat {@code com.sp.world.levels.custom.*}).
 *
 * <p>Generator bekerja di koordinat <b>asli</b> mod. Luas dan letak bangunan
 * di dunia (bangunan utama, set lobby, atau salinan milik room) diatur oleh
 * {@link BuildArea}; titik spawn ikut digeser, jadi pemain tetap mendarat di
 * titik yang sama relatif terhadap bangunannya seperti di mod aslinya.
 */
public enum BackroomsLevel {

    LEVEL0("level0", "0", "Level 0", new Vec3(0.0D, 21.0D, 0.0D), Level0Generator::new, 0, 64,
            ModBlocks.WALL_BLOCK),
    LEVEL1("level1", "1", "Level 1", new Vec3(6.0D, 22.0D, 3.0D), Level1Generator::new, 0, 64,
            ModBlocks.CONCRETE_11),
    POOLROOMS("poolrooms", "poolrooms", "The Poolrooms", new Vec3(16.0D, 106.0D, 16.0D), PoolroomsGenerator::new, 0, 120,
            ModBlocks.POOL_TILES);

    /**
     * Dimensi lobby: ketiga set latar lobby berjajar di sini (lihat
     * {@link BuildArea#lobby}), jadi ganti latar tidak pindah dimensi.
     */
    public static final ResourceKey<Level> LOBBY =
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation(ModBlocks.NAMESPACE, "lobby"));

    private final ResourceKey<Level> dimension;
    private final String commandName;
    private final String displayName;
    private final Vec3 spawn;
    private final Supplier<LevelGenerator> generator;
    private final int barrierMinY;
    private final int barrierMaxY;
    /** Dinding penutup lubang di tepi area -- blok dinding ruangan asli level ini. */
    private final Supplier<Block> sealBlock;

    BackroomsLevel(String id, String commandName, String displayName, Vec3 spawn, Supplier<LevelGenerator> generator,
                   int barrierMinY, int barrierMaxY, Supplier<Block> sealBlock) {
        this.dimension = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(ModBlocks.NAMESPACE, id));
        this.commandName = commandName;
        this.displayName = displayName;
        this.spawn = spawn;
        this.generator = generator;
        this.barrierMinY = barrierMinY;
        this.barrierMaxY = barrierMaxY;
        this.sealBlock = sealBlock;
    }

    public Block sealBlock() { return sealBlock.get(); }

    public ResourceKey<Level> dimension() { return dimension; }

    /** Nama pendek untuk command: "0", "1", "poolrooms". */
    public String commandName() { return commandName; }

    public String displayName() { return displayName; }

    public LevelGenerator newGenerator() { return generator.get(); }

    public int barrierMinY() { return barrierMinY; }

    public int barrierMaxY() { return barrierMaxY; }

    /** Titik spawn di koordinat asli mod; {@link BuildArea#worldSpawn()} menggesernya ke dunia. */
    public Vec3 spawn() { return spawn; }

    /**
     * Level yang "berlaku" di suatu tempat, untuk suasana dan efeknya: di
     * dimensi level itu sendiri, atau di dimensi lobby menurut set latar mana
     * yang ditempati (dari koordinat X). {@code null} di luar Backrooms.
     */
    public static BackroomsLevel at(ResourceKey<Level> key, double x) {
        if (LOBBY.equals(key)) return BuildArea.lobbyAt(x);
        return byDimension(key);
    }

    public static BackroomsLevel at(Level level, double x) {
        return level == null ? null : at(level.dimension(), x);
    }

    public static BackroomsLevel byDimension(ResourceKey<Level> key) {
        for (BackroomsLevel level : values()) {
            if (level.dimension.equals(key)) return level;
        }
        return null;
    }

    public static BackroomsLevel byCommandName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (BackroomsLevel level : values()) {
            if (level.commandName.equals(lower)) return level;
        }
        return null;
    }
}
