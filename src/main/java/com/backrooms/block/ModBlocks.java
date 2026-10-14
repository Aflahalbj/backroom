package com.backrooms.block;

import com.backrooms.block.pipe.PipeBlock;
import com.backrooms.block.pipe.PipeCornerBlock;
import com.backrooms.block.pipe.ThinPipeBlock;
import com.backrooms.block.pipe.ThinPipeCornerBlock;
import com.backrooms.sound.ModSounds;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GlassBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Semua blok SP-Backrooms Revamped, diregistrasi dengan ID aslinya
 * ({@code spb-revamped:*}) dan setting yang disalin dari {@code ModBlocks}
 * aslinya baris per baris.
 *
 * <p>Namespace-nya sengaja dipertahankan: file .nbt struktur menuliskan nama
 * blok lengkap, jadi namespace lain berarti seluruh bangunan jadi udara. Mod
 * aslinya hanya ada untuk Fabric, jadi tidak akan bentrok.
 *
 * <p>Padanan setting Fabric -> Forge yang dipakai di bawah:
 * {@code hardness(-1)} = {@code destroyTime(-1)} (tak bisa dihancurkan),
 * {@code solid()} = {@code forceSolidOn()},
 * {@code noBlockBreakParticles()} = {@code noParticlesOnBreak()},
 * {@code nonOpaque()} = {@code noOcclusion()},
 * {@code collidable(false)} = {@code noCollission()}.
 */
public final class ModBlocks {

    public static final String NAMESPACE = "spb-revamped";

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, NAMESPACE);

    /** Urutan registrasi, dipakai untuk item dan tab kreatif. */
    public static final Map<String, RegistryObject<Block>> ALL = new LinkedHashMap<>();

    private ModBlocks() {}

    private static RegistryObject<Block> register(String name, Supplier<Block> block) {
        RegistryObject<Block> object = BLOCKS.register(name, block);
        ALL.put(name, object);
        return object;
    }

    /** {@code FabricBlockSettings.copyOf(base).hardness(-1).noBlockBreakParticles()} */
    private static Properties base(Block base) {
        return Properties.copy(base).destroyTime(-1.0F).noParticlesOnBreak();
    }

    private static Properties base(Supplier<Block> base) {
        return base(base.get());
    }

    /** Tulisan/gambar/panah di dinding: semuanya WallText dengan setting yang sama. */
    private static RegistryObject<Block> wallText(String name) {
        return register(name, () -> new WallTextBlock(base(Blocks.STONE)
                .noOcclusion().noCollission().sound(ModSounds.SILENT)));
    }

    private static RegistryObject<Block> solid(String name, Block base, Function<Properties, Block> factory) {
        return register(name, () -> factory.apply(base(base).forceSolidOn()));
    }

    // --------------------------------------------------------------- blok
    //
    // Hanya blok yang dipakai level 0, level 1, dan poolrooms: semua yang ada
    // di palet struktur ketiga level itu, ditambah yang ditaruh kode (plafon
    // level 0, panah/gambar hasil drawing marker, dinding penutup tepi).

    public static final RegistryObject<Block> WALL_BLOCK = register("wall_block", () ->
            new WallBlock(base(Blocks.OAK_PLANKS).forceSolidOn().sound(ModSounds.WALL)));
    public static final RegistryObject<Block> WALL_BLOCK_2 = register("wall_block_2", () ->
            new WallBlock(base(Blocks.OAK_PLANKS).forceSolidOn().sound(ModSounds.WALL)));
    public static final RegistryObject<Block> CARPET_BLOCK = register("carpet_block", () ->
            new CarpetBlock(base(Blocks.WHITE_WOOL).forceSolidOn().sound(ModSounds.CARPET)));
    public static final RegistryObject<Block> CEILING_TILE = register("ceiling_tile", () ->
            new Block(base(Blocks.OAK_PLANKS).forceSolidOn().sound(ModSounds.CEILING_TILE)));
    public static final RegistryObject<Block> GHOST_CEILING_TILE = register("ghost_ceiling_tile", () ->
            new Block(base(Blocks.OAK_PLANKS).forceSolidOn().noCollission()));
    public static final RegistryObject<Block> FLUORESCENT_LIGHT = register("fluorescent_light", () ->
            new FluorescentLightBlock(base(Blocks.GLASS)));

    public static final RegistryObject<Block> ARROW_1 = wallText("arrow1");
    public static final RegistryObject<Block> ARROW_2 = wallText("arrow2");
    public static final RegistryObject<Block> ARROW_3 = wallText("arrow3");
    public static final RegistryObject<Block> ARROW_4 = wallText("arrow4");
    public static final RegistryObject<Block> WALL_SMALL_1 = wallText("wall_small_1");
    public static final RegistryObject<Block> WALL_SMALL_2 = wallText("wall_small_2");
    public static final RegistryObject<Block> WALL_DRAWING_DOOR = wallText("wall_drawing_door");
    public static final RegistryObject<Block> WALL_DRAWING_WINDOW = wallText("wall_drawing_window");
    public static final RegistryObject<Block> DRAWING_MARKER = register("drawing_marker", () ->
            new DrawingMarkerBlock(base(Blocks.STONE)));
    public static final RegistryObject<Block> RUG_1 = register("rug1", () ->
            new ShapedBlock(base(Blocks.WHITE_WOOL).noOcclusion().noCollission().sound(ModSounds.SILENT), ShapedBlock.rug()));
    public static final RegistryObject<Block> RUG_2 = register("rug2", () ->
            new ShapedBlock(base(Blocks.WHITE_WOOL).noOcclusion().noCollission().sound(ModSounds.SILENT), ShapedBlock.rug()));
    public static final RegistryObject<Block> CHAIN_FENCE = register("chain_fence", () ->
            new ChainFenceBlock(base(Blocks.IRON_BLOCK)));
    public static final RegistryObject<Block> NEW_STAIRS = register("new_stairs", () ->
            new NewStairsBlock(base(Blocks.STONE).noOcclusion().sound(ModSounds.CONCRETE)));
    public static final RegistryObject<Block> BRICKS = register("bricks", () -> new Block(base(Blocks.STONE)));
    public static final RegistryObject<Block> THIN_FLUORESCENT_LIGHT = register("thin_fluorescent_light", () ->
            new ThinFluorescentLightBlock(base(Blocks.GLASS)));
    public static final RegistryObject<Block> EMERGENCY_LIGHT = register("emergency_light", () ->
            new EmergencyLightBlock(base(Blocks.GLASS)));
    public static final RegistryObject<Block> BOTTOM_TRIM = register("bottom_trim", () ->
            new BottomTrimBlock(base(Blocks.STONE).noCollission().noOcclusion().sound(ModSounds.SILENT)));

    public static final RegistryObject<Block> CONCRETE_6 = register("concrete6", () ->
            new Block(base(Blocks.STONE).forceSolidOn().sound(ModSounds.CONCRETE)));
    public static final RegistryObject<Block> CONCRETE_7 = solid("concrete7", Blocks.STONE, Block::new);
    public static final RegistryObject<Block> CONCRETE_9 = solid("concrete9", Blocks.STONE, Block::new);
    public static final RegistryObject<Block> CONCRETE_9_SLAB = solid("concrete9slab", Blocks.STONE, SlabBlock::new);
    public static final RegistryObject<Block> CONCRETE_11 = solid("concrete11", Blocks.STONE, Block::new);

    public static final RegistryObject<Block> THIN_PIPE = register("thin_pipe", () ->
            new ThinPipeBlock(base(Blocks.STONE).noOcclusion().noCollission().sound(ModSounds.SILENT)));
    public static final RegistryObject<Block> THIN_PIPE_CORNER = register("thin_pipe_corner", () ->
            new ThinPipeCornerBlock(base(Blocks.STONE).noOcclusion().noCollission().sound(ModSounds.SILENT)));
    public static final RegistryObject<Block> PIPE = register("pipe", () ->
            new PipeBlock(base(Blocks.STONE).noOcclusion().sound(ModSounds.SILENT)));
    public static final RegistryObject<Block> PIPE_MIDDLE = register("pipe_middle", () ->
            new PipeBlock(base(Blocks.STONE).noOcclusion().sound(ModSounds.SILENT)));
    public static final RegistryObject<Block> PIPE_CORNER = register("pipe_corner", () ->
            new PipeCornerBlock(base(Blocks.STONE).noOcclusion().sound(ModSounds.SILENT)));
    public static final RegistryObject<Block> BIG_PIPE = register("big_pipe", () ->
            new PipeBlock(base(Blocks.STONE).noOcclusion().sound(ModSounds.SILENT)));
    public static final RegistryObject<Block> BIG_PIPE_MIDDLE = register("big_pipe_middle", () ->
            new PipeBlock(base(Blocks.STONE).noOcclusion().sound(ModSounds.SILENT)));

    public static final RegistryObject<Block> WALL_TEXT_1 = wallText("wall_text_1");
    public static final RegistryObject<Block> WALL_TEXT_2 = wallText("wall_text_2");
    public static final RegistryObject<Block> WALL_TEXT_3 = wallText("wall_text_3");
    public static final RegistryObject<Block> WALL_TEXT_4 = wallText("wall_text_4");
    public static final RegistryObject<Block> WALL_TEXT_5 = wallText("wall_text_5");
    public static final RegistryObject<Block> WALL_TEXT_6 = wallText("wall_text_6");
    public static final RegistryObject<Block> WALL_TEXT_7 = wallText("wall_text_7");

    public static final RegistryObject<Block> WOODEN_CRATE = solid("wooden_crate", Blocks.OAK_PLANKS, WoodenCrateBlock::new);
    public static final RegistryObject<Block> POOL_SKY = register("pool_sky", () ->
            new GlassBlock(base(Blocks.GLASS).sound(ModSounds.SILENT)));
    public static final RegistryObject<Block> POOL_TILES = register("pool_tiles", () ->
            new Block(base(Blocks.STONE).forceSolidOn().sound(ModSounds.CONCRETE)));
    public static final RegistryObject<Block> POOL_TILE_WALL = solid("pool_tile_wall", Blocks.STONE, PoolTileWallBlock::new);
    public static final RegistryObject<Block> SLOPE = register("slope", () ->
            new PoolTileSlopeBlock(base(Blocks.STONE).noOcclusion().sound(ModSounds.CONCRETE)));
}
