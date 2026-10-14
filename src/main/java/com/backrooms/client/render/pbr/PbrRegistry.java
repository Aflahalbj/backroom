package com.backrooms.client.render.pbr;

import com.backrooms.client.render.ModRenderTypes;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.Map;

/**
 * Port dari {@code com.sp.render.pbr.PbrRegistry}: blok yang digambar di
 * layer PBR (normal map + parallax) beserta parameter materialnya.
 */
public final class PbrRegistry {

    private static final Map<Block, PbrMaterial> PBR_MATERIALS = new HashMap<>();

    private PbrRegistry() {}

    /** Harus dipanggil saat client setup (Forge hanya menerima render layer di masa loading). */
    @SuppressWarnings("removal")
    public static void registerPBR(Block block, PbrMaterial material) {
        PBR_MATERIALS.remove(block);
        PBR_MATERIALS.put(block, material);
        ItemBlockRenderTypes.setRenderLayer(block, ModRenderTypes.getPbrLayer());
    }

    public static PbrMaterial getMaterial(Block block) {
        return PBR_MATERIALS.get(block);
    }

    public record PbrMaterial(boolean enableHeight, float depthMultiplier, float zoom, int textureResolution) {
    }
}
