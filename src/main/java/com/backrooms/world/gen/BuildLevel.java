package com.backrooms.world.gen;

import com.backrooms.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Optional;

/**
 * Dunia sebagaimana dilihat generator: koordinat aslinya mod (spawn di 0,0,
 * region dari -32 sampai +47, dst.), digeser sebesar {@code offsetX/offsetZ}
 * waktu benar-benar dibaca atau ditulis.
 *
 * <p>Generator dan maze yang di-port tetap menghitung semua posisi -- dan
 * membaca penanda wol -- di koordinat aslinya, jadi susunannya identik;
 * hanya letak akhirnya di dunia yang bergeser supaya bangunannya berada di
 * tengah 0,0. Rotasi struktur berporos di titik tempelnya, jadi tidak
 * terpengaruh pergeseran.
 */
public final class BuildLevel {

    private final ServerLevel level;
    private final int offsetX;
    private final int offsetZ;

    public BuildLevel(ServerLevel level, int offsetX, int offsetZ) {
        this.level = level;
        this.offsetX = offsetX;
        this.offsetZ = offsetZ;
    }

    public ServerLevel server() {
        return level;
    }

    public BlockPos real(BlockPos pos) {
        return pos.offset(offsetX, 0, offsetZ);
    }

    public BlockState getBlockState(int x, int y, int z) {
        return level.getBlockState(new BlockPos(x + offsetX, y, z + offsetZ));
    }

    public BlockState getBlockState(BlockPos pos) {
        return level.getBlockState(real(pos));
    }

    public void setBlock(BlockPos pos, BlockState state, int flags) {
        level.setBlock(real(pos), state, flags);
    }

    public Optional<StructureTemplate> template(String path) {
        return level.getStructureManager().get(new ResourceLocation(ModBlocks.NAMESPACE, path));
    }

    public void place(Optional<StructureTemplate> template, BlockPos pos, StructurePlaceSettings settings,
                      RandomSource random, int flags) {
        place(template, pos, pos, settings, random, flags);
    }

    public void place(Optional<StructureTemplate> template, BlockPos pos, BlockPos pivot,
                      StructurePlaceSettings settings, RandomSource random, int flags) {
        template.ifPresent(t -> t.placeInWorld(level, real(pos), real(pivot), settings, random, flags));
    }
}
