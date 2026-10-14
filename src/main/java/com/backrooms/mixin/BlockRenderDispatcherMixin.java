package com.backrooms.mixin;

import com.backrooms.client.render.pbr.BlockMaterial;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Port dari {@code com.sp.mixin.pbr.RebuildTaskMixin}: buffer chunk diberi
 * tahu blok mana yang sedang digambar (untuk ID material / parameter PBR).
 * Di Forge, rebuild chunk memanggil overload {@code renderBatched} milik Forge
 * (nama tidak di-obfuscate, karena itu {@code remap = false}).
 */
@Mixin(BlockRenderDispatcher.class)
public abstract class BlockRenderDispatcherMixin {

    private static final String RENDER_BATCHED = "renderBatched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;"
            + "ZLnet/minecraft/util/RandomSource;Lnet/minecraftforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V";

    @Inject(method = RENDER_BATCHED, at = @At("HEAD"), remap = false)
    private void backrooms$setCurrentBlock(BlockState state, BlockPos pos, BlockAndTintGetter level, PoseStack poseStack, VertexConsumer consumer,
                                           boolean checkSides, RandomSource random, ModelData modelData, RenderType renderType, CallbackInfo ci) {
        if (consumer instanceof BlockMaterial material) {
            material.backrooms$setCurrentBlock(state.getBlock());
        }
    }

    @Inject(method = RENDER_BATCHED, at = @At("RETURN"), remap = false)
    private void backrooms$clearCurrentBlock(BlockState state, BlockPos pos, BlockAndTintGetter level, PoseStack poseStack, VertexConsumer consumer,
                                             boolean checkSides, RandomSource random, ModelData modelData, RenderType renderType, CallbackInfo ci) {
        if (consumer instanceof BlockMaterial material) {
            material.backrooms$setCurrentBlock(null);
        }
    }
}
