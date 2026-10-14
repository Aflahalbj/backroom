package com.backrooms.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Port dari {@code com.sp.block.custom.Rug}: blok tanpa state dengan satu kotak bentuk tetap. */
public class ShapedBlock extends Block {

    private final VoxelShape shape;

    public ShapedBlock(Properties properties, VoxelShape shape) {
        super(properties);
        this.shape = shape;
    }

    /** Karpet kecil setebal setengah piksel. */
    public static VoxelShape rug() {
        return Block.box(0.0D, 0.0D, 0.0D, 16.0D, 0.5D, 16.0D);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }
}
