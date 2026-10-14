package com.backrooms.client.render.pbr;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Port dari {@code com.sp.render.pbr.BlockIdMap}: ID "material" per blok yang
 * ditulis ke setiap vertex chunk (format {@code BLOCKS}). 0..3 = layer render
 * blok (solid, cutout, cutout mipped, translucent), lalu ID khusus dari mod
 * (pool tiles 18, emergency light 19).
 *
 * <p>Fabric memakai {@code RenderLayers.getBlockLayer}; padanannya di Forge
 * adalah set layer chunk blok tersebut.
 */
public final class BlockIdMap {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Object2IntMap<Block> BLOCK_IDS = new Object2IntOpenHashMap<>();
    private static final List<BlockIDCallback> CALLBACKS = new ArrayList<>();
    public static boolean init = false;

    private BlockIdMap() {}

    public static void init() {
        BLOCK_IDS.clear();
        init = true;
        int numOfBlocks = 0;
        for (Block block : ForgeRegistries.BLOCKS) {
            ChunkRenderTypeSet layers = ItemBlockRenderTypes.getRenderLayers(block.defaultBlockState());
            RenderType renderLayer = layers.isEmpty() ? null : layers.asList().get(0);
            if (renderLayer == RenderType.solid()) {
                BLOCK_IDS.put(block, 0);
            } else if (renderLayer == RenderType.cutout()) {
                BLOCK_IDS.put(block, 1);
            } else if (renderLayer == RenderType.cutoutMipped()) {
                BLOCK_IDS.put(block, 2);
            } else if (renderLayer == RenderType.translucent()) {
                BLOCK_IDS.put(block, 3);
            }
            ++numOfBlocks;
        }
        LOGGER.info("Loaded {} Default Block IDs", numOfBlocks);
        for (BlockIDCallback callback : CALLBACKS) {
            callback.apply(BLOCK_IDS);
        }
    }

    public static int getBlockID(Block block) {
        return !BLOCK_IDS.isEmpty() && init ? BLOCK_IDS.getOrDefault(block, -1) : -1;
    }

    public static void registerBlockID(BlockIDCallback function) {
        CALLBACKS.add(function);
    }

    @FunctionalInterface
    public interface BlockIDCallback {
        void apply(Object2IntMap<Block> map);
    }
}
