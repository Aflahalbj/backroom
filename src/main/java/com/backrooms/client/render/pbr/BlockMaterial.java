package com.backrooms.client.render.pbr;

import net.minecraft.world.level.block.Block;

/** Port dari {@code com.sp.mixininterfaces.BlockMaterial}; diterapkan ke BufferBuilder oleh mixin. */
public interface BlockMaterial {

    /** Blok yang sedang digambar ke buffer chunk ini (null di luar penggambaran blok). */
    void backrooms$setCurrentBlock(Block block);

    /** Tandai buffer ini sebagai buffer layer PBR di paket buffer chunk. */
    void backrooms$setPbrLayer(boolean pbr);
}
