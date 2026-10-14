package com.backrooms.mixin;

import com.backrooms.client.render.ModVertexFormats;
import com.backrooms.client.render.pbr.BlockIdMap;
import com.backrooms.client.render.pbr.PbrRegistry;
import com.backrooms.client.render.pbr.BlockMaterial;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;

/**
 * Port dari {@code com.sp.mixin.pbr.BufferBuilderMixin} (+ {@code ChunkBuilderMixin}):
 *
 * <ul>
 *   <li>setiap buffer berformat blok vanilla diganti format {@code BLOCKS}
 *       yang membawa ID material blok per vertex;</li>
 *   <li>buffer layer PBR di chunk memakai format {@code PBR} dengan parameter
 *       material dari {@link PbrRegistry}.</li>
 * </ul>
 *
 * Di Fabric format PBR dipilih saat {@code beginBufferBuilding} untuk layer
 * PBR; di sini builder layer PBR ditandai saat dibuat (lihat
 * {@code ChunkBufferBuilderPackMixin}), hasilnya sama.
 */
@Mixin(BufferBuilder.class)
public abstract class BufferBuilderMixin implements BlockMaterial {

    @Shadow
    private ByteBuffer buffer;

    @Shadow
    private int nextElementByte;

    @Unique
    private boolean backrooms$isRenderingBlock;
    @Unique
    private Block backrooms$currentBlock;
    @Unique
    private VertexFormat backrooms$currentFormat;
    @Unique
    private boolean backrooms$pbrLayer;

    @Shadow
    public abstract void nextElement();

    @Shadow
    public abstract void putFloat(int offset, float value);

    @Override
    public void backrooms$setCurrentBlock(Block block) {
        this.backrooms$currentBlock = block;
    }

    @Override
    public void backrooms$setPbrLayer(boolean pbr) {
        this.backrooms$pbrLayer = pbr;
    }

    @ModifyVariable(method = "begin", at = @At("HEAD"), argsOnly = true)
    private VertexFormat backrooms$setFormat(VertexFormat format) {
        this.backrooms$isRenderingBlock = false;
        this.backrooms$currentFormat = format;
        if (this.backrooms$pbrLayer && format == DefaultVertexFormat.BLOCK) {
            format = ModVertexFormats.PBR;
        }
        if (format == DefaultVertexFormat.BLOCK) {
            return this.backrooms$setRendering(ModVertexFormats.BLOCKS);
        }
        return format == ModVertexFormats.PBR ? this.backrooms$setRendering(ModVertexFormats.PBR) : format;
    }

    @Inject(method = "endVertex", at = @At("HEAD"))
    private void backrooms$putBlockID(CallbackInfo ci) {
        if (this.backrooms$isRenderingBlock) {
            if (this.backrooms$currentFormat == ModVertexFormats.BLOCKS) {
                this.buffer.putInt(this.nextElementByte, BlockIdMap.getBlockID(this.backrooms$currentBlock));
                this.nextElement();
            } else if (this.backrooms$currentFormat == ModVertexFormats.PBR) {
                PbrRegistry.PbrMaterial material = PbrRegistry.getMaterial(this.backrooms$currentBlock);
                if (material == null) {
                    return;
                }
                this.putFloat(0, material.zoom());
                this.nextElement();
                this.backrooms$putInt(0, material.textureResolution());
                this.nextElement();
                this.backrooms$putInt(0, material.enableHeight() ? 1 : 0);
                this.nextElement();
                this.putFloat(0, material.depthMultiplier());
                this.nextElement();
            }
        }
    }

    @Unique
    private void backrooms$putInt(int offset, int value) {
        this.buffer.putInt(this.nextElementByte + offset, value);
    }

    @Unique
    private VertexFormat backrooms$setRendering(VertexFormat format) {
        this.backrooms$isRenderingBlock = true;
        this.backrooms$currentFormat = format;
        return format;
    }
}
