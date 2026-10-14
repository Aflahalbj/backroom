package com.backrooms.mixin;

import com.backrooms.client.render.ModRenderTypes;
import com.backrooms.client.render.pbr.BlockMaterial;
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.renderer.ChunkBufferBuilderPack;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/** Buffer layer PBR di tiap paket buffer chunk memakai format PBR (lihat {@link BufferBuilderMixin}). */
@Mixin(ChunkBufferBuilderPack.class)
public abstract class ChunkBufferBuilderPackMixin {

    @Shadow
    @Final
    private Map<RenderType, BufferBuilder> builders;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void backrooms$markPbrBuilder(CallbackInfo ci) {
        BufferBuilder pbr = this.builders.get(ModRenderTypes.getPbrLayer());
        if (pbr instanceof BlockMaterial material) {
            material.backrooms$setPbrLayer(true);
        }
    }
}
