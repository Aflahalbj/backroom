package com.backrooms.world;

import com.backrooms.block.ModBlocks;
import com.backrooms.config.BackroomsConfig;
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
 * <h2>Dua sistem koordinat</h2>
 *
 * Generator bekerja di koordinat <b>asli</b> mod. Area bangunannya dihitung
 * dari {@link BackroomsConfig#REGION_RADIUS}: region maze ada di setiap chunk
 * kelipatan 5 dan masing-masing mengisi 2 chunk ke kiri sampai 2 chunk ke
 * kanan, jadi area {@code [-5R-2, 5R+2]} chunk berisi tepat {@code (2R+1)^2}
 * region utuh. Area itu membentang -16h..16h+15 blok, jadi tengahnya di 7,5.
 *
 * <p>Di <b>dunia</b>, semuanya digeser sebesar {@link #offsetX()} /
 * {@link #offsetZ()} supaya tengah area tepat di 0,0. Titik spawn ikut
 * digeser, jadi pemain tetap mendarat di titik yang sama relatif terhadap
 * bangunannya seperti di mod aslinya.
 */
public enum BackroomsLevel {

    LEVEL0("level0", "0", "Level 0", new Vec3(0.0D, 21.0D, 0.0D), Level0Generator::new, 0, 64,
            ModBlocks.WALL_BLOCK),
    LEVEL1("level1", "1", "Level 1", new Vec3(6.0D, 22.0D, 3.0D), Level1Generator::new, 0, 64,
            ModBlocks.CONCRETE_11),
    POOLROOMS("poolrooms", "poolrooms", "The Poolrooms", new Vec3(16.0D, 106.0D, 16.0D), PoolroomsGenerator::new, 0, 120,
            ModBlocks.POOL_TILES);

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

    // ------------------------------------------- area, koordinat generator

    public int minChunkX() { return -halfSize(); }

    public int maxChunkX() { return halfSize(); }

    public int minChunkZ() { return -halfSize(); }

    public int maxChunkZ() { return halfSize(); }

    private static int halfSize() {
        return 5 * BackroomsConfig.REGION_RADIUS.get() + 2;
    }

    // ----------------------------------------------- pergeseran ke dunia

    /** Geser X supaya tengah area tepat di 0: area lebar genap jadi -w/2 .. w/2-1. */
    public int offsetX() {
        return -((minChunkX() * 16 + maxChunkX() * 16 + 16) / 2);
    }

    public int offsetZ() {
        return -((minChunkZ() * 16 + maxChunkZ() * 16 + 16) / 2);
    }

    public int worldMinX() { return minChunkX() * 16 + offsetX(); }

    public int worldMaxX() { return maxChunkX() * 16 + 15 + offsetX(); }

    public int worldMinZ() { return minChunkZ() * 16 + offsetZ(); }

    public int worldMaxZ() { return maxChunkZ() * 16 + 15 + offsetZ(); }

    /** Titik spawn aslinya, digeser bersama bangunannya. */
    public Vec3 worldSpawn() {
        return spawn.add(offsetX(), 0.0D, offsetZ());
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
