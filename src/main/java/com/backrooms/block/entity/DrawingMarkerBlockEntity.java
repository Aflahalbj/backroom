package com.backrooms.block.entity;

import com.backrooms.block.DrawingMarkerBlock;
import com.backrooms.block.ModBlocks;
import com.backrooms.block.WallTextBlock;
import com.backrooms.world.BackroomsLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Port dari {@code com.sp.block.entity.DrawingMarkerBlockEntity}.
 *
 * <p>Di level 0, setiap penanda berubah pada tick pertamanya: peluang 1/10
 * jadi gambar (panah 1-4 kalau {@code type=false}, gambar kecil kalau
 * {@code type=true}, menghadap sama dengan penandanya), selebihnya hilang
 * jadi udara. Di level lain penandanya dibiarkan.
 */
public class DrawingMarkerBlockEntity extends BlockEntity {

    private final RandomSource random = RandomSource.create();

    public DrawingMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DRAWING_MARKER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DrawingMarkerBlockEntity entity) {
        if (BackroomsLevel.at(level, pos.getX()) != BackroomsLevel.LEVEL0) return;

        int pick = entity.random.nextIntBetweenInclusive(1, 4);
        int chance = entity.random.nextIntBetweenInclusive(1, 10);
        boolean small = state.getValue(DrawingMarkerBlock.TYPE);

        Block drawing;
        if (chance == 1 && !small) {
            drawing = switch (pick) {
                case 2 -> ModBlocks.ARROW_2.get();
                case 3 -> ModBlocks.ARROW_3.get();
                case 4 -> ModBlocks.ARROW_4.get();
                default -> ModBlocks.ARROW_1.get();
            };
        } else if (chance == 1) {
            drawing = pick == 2 || pick == 4 ? ModBlocks.WALL_SMALL_1.get() : ModBlocks.WALL_SMALL_2.get();
        } else {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            return;
        }
        level.setBlockAndUpdate(pos, drawing.defaultBlockState()
                .setValue(WallTextBlock.FACING, state.getValue(DrawingMarkerBlock.FACING)));
    }
}
